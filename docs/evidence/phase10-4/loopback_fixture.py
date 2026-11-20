"""Run only in the isolated diagnostic FastAPI child with a synthetic credential."""
import json
import os
import socket
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

import httpx
import uvicorn

import recipe_generation
from test_recipe_generation import FIXTURE

CASES = {"success": 200, "auth": 401, "model": 404, "payload": 400,
         "quota": 429, "server": 503, "connection": None, "timeout": None, "malformed": 200}
EXPECTED_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent"
events = Path(os.environ["DIAGNOSTIC_EVENTS"])


def record(stage, case):
    with events.open("a", encoding="ascii") as out:
        out.write(json.dumps({"stage": stage, "case": case}) + "\n")


class Provider(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def do_POST(self):
        case = self.path.removeprefix("/")
        assert case in CASES and "x-goog-api-key" not in self.headers
        self.rfile.read(int(self.headers["Content-Length"]))
        record("received", case)
        if case == "timeout":
            time.sleep(12)
            return
        if CASES[case] == 200:
            generated = json.dumps(FIXTURE | {"substitutions": ["Fixture substitution for the offline test."]})
            data = {"candidates": [{"finishReason": "STOP", "content": {
                "parts": [{"text": generated if case == "success" else "{bad json"}]}}]}
        else:
            data = {"error": {"message": "private-fixture-detail", "code": CASES[case]}}
        encoded = json.dumps(data).encode()
        self.send_response(CASES[case])
        self.send_header("X-Goog-Request-Id", "fixture-" + case)
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)


class LoopbackTransport(httpx.HTTPTransport):
    def handle_request(self, request):
        assert str(request.url) == EXPECTED_URL and request.method == "POST"
        assert request.headers["x-goog-api-key"] == "offline-fixture"
        payload = json.loads(request.read())
        user_input = json.loads(payload["contents"][0]["parts"][0]["text"])
        case = user_input["prompt"].removeprefix("Offline recipe diagnostic ")
        assert case in CASES and not user_input["pantry"]
        record("dispatch", case)
        port = refused.getsockname()[1] if case == "connection" else server.server_port
        target = httpx.Request("POST", f"http://127.0.0.1:{port}/{case}",
                               content=request.content, extensions=request.extensions)
        return super().handle_request(target)


class FixtureHttpx:
    def __getattr__(self, name):
        return getattr(httpx, name)

    def Client(self, **settings):
        return httpx.Client(transport=LoopbackTransport(retries=0), **settings)


if __name__ == "__main__":
    if os.environ.get("GEMINI_API_KEY") != "offline-fixture":
        raise SystemExit("Only the synthetic fixture credential is accepted.")
    with socket.socket() as refused, ThreadingHTTPServer(("127.0.0.1", 0), Provider) as server:
        refused.bind(("127.0.0.1", 0))  # Keep this port reserved without listening.
        threading.Thread(target=server.serve_forever, daemon=True).start()
        recipe_generation.httpx = FixtureHttpx()
        uvicorn.run("app:app", host="127.0.0.1", port=18202, access_log=False)

