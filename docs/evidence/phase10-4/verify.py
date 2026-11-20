"""Run with the CartIQ AI venv: verify.py FRESH_OUTPUT_DIR BUILT_JAR EXPO_WEB_DIR."""
import json
import os
from pathlib import Path
import re
import secrets
import socket
import subprocess
import sys
import time
from contextlib import ExitStack
from datetime import datetime, timezone

import httpx

here = Path(__file__).resolve().parent
root = here.parents[2]
ai = root / "ai-service"
out, jar, web = map(lambda arg: Path(arg).resolve(), sys.argv[1:])
assert jar.is_file() and (web / "index.html").is_file()
out.mkdir(exist_ok=False)
for port in (18201, 18202):
    with socket.socket() as probe:
        probe.bind(("127.0.0.1", port))
allowed = "PATH PATHEXT SYSTEMROOT WINDIR COMSPEC USERPROFILE TEMP TMP APPDATA LOCALAPPDATA JAVA_HOME PROGRAMFILES PROGRAMFILES(X86) NUMBER_OF_PROCESSORS"
runtime = {k:v for k,v in os.environ.items() if k.upper() in allowed.split()}
token = secrets.token_urlsafe(48)
shared = dict(runtime, AI_SERVICE_SECRET=token, CARTIQ_RECIPE_GENERATION_ENABLED="true")
database = "jdbc:h2:mem:diagnostic104;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
# Oracle's Windows PATH shim leaves a child JVM running if only the shim exits.
settings = subprocess.run(["java", "-XshowSettings:properties", "-version"], capture_output=True,
                          text=True, env=runtime, check=True)
jdk = re.search(r"^\s*java\.home = (.+)$", settings.stderr, re.MULTILINE)
if not jdk:
    raise RuntimeError("Could not locate the JDK for the disposable JVM")
java = Path(jdk[1].strip()) / "bin/java.exe"


def stop(process):
    if process.poll() is None:
        process.terminate()
        process.wait(timeout=15)


def child(stack, args, env, filename):
    log = stack.enter_context((out / filename).open("wb"))
    process = subprocess.Popen(args, env=env, cwd=out, stdout=log, stderr=subprocess.STDOUT)
    stack.callback(stop, process)
    return process


with ExitStack() as stack, httpx.Client(trust_env=False, timeout=2) as local:
    # Windows venv launchers can delegate to another process. Use the actual
    # interpreter with the venv packages so process ownership/cleanup is exact.
    python_env = dict(shared, GEMINI_API_KEY="offline-fixture", GEMINI_MODEL="gemini-3.5-flash",
                      DIAGNOSTIC_EVENTS=str(out / "events.jsonl"),
                      PYTHONPATH=os.pathsep.join([str(ai), str(Path(sys.prefix) / "Lib/site-packages")]))
    fast = child(stack, [sys._base_executable, "-B", str(here / "loopback_fixture.py")], python_env, "mock-ai.log")
    spring = child(stack, [str(java), "-Xmx460m", "-Duser.home=.", "-jar", str(jar)],
                   dict(shared, SERVER_PORT="18201", SERVER_ADDRESS="127.0.0.1", SPRING_PROFILES_ACTIVE="local",
                        SPRING_DATASOURCE_URL=database, SPRING_FLYWAY_URL=database, SPRING_FLYWAY_USER="sa",
                        JWT_SECRET=secrets.token_urlsafe(48), AI_SERVICE_URL="http://127.0.0.1:18202",
                        QUICKCOMMERCE_ENABLED="false", SAMPLE_DATA="false",
                        SMARTCART_IMPORT_STARTER_CATALOGUE="true"), "mock-spring.log")
    for _ in range(100):
        if fast.poll() is not None or spring.poll() is not None:
            raise RuntimeError("Isolated service exited during startup")
        try:
            health = local.get("http://127.0.0.1:18201/actuator/health").json()["status"]
            ai_health = local.get("http://127.0.0.1:18202/health").json()["status"]
            if health == "UP" and ai_health == "ok":
                break
        except (httpx.HTTPError, ValueError, KeyError):
            pass
        time.sleep(0.5)
    else:
        raise RuntimeError("Loopback readiness timed out")
    ready = local.get("http://127.0.0.1:18202/recipes/readiness", headers={"X-Service-Token":token}).json()
    assert ready["ready"] and ready["model"] == "gemini-3.5-flash"
    readiness = {"utc": datetime.now(timezone.utc).isoformat(), "spring": health, "fastapi":ai_health,
                 "model":ready["model"], "generationEnabled":ready["generationEnabled"], "credential":"synthetic fixture"}
    (out / "readiness.json").write_text(json.dumps(readiness, indent=2) + "\n", encoding="utf8")
    subprocess.run(["node", str(here / "browser.cjs"), str(out), str(web)], check=True, env=runtime, cwd=out)
    events = [json.loads(line) for line in (out / "events.jsonl").read_text().splitlines()]
    assert len(events) == 17  # Nine dispatches, eight HTTP receipts; one connection was refused.
    logs = (out / "mock-ai.log").read_text(errors="replace") + (out / "mock-spring.log").read_text(errors="replace")
    for private in (token, "offline-fixture", "private-fixture-detail", "Offline recipe diagnostic "):
        assert private not in logs, "Unexpected private fixture data in service logs"
    print("Nine browser outcomes and single-dispatch counts verified.")
