"""Stage an operator-reviewed Open Food Facts JSONL snapshot in CartIQ."""
import argparse
import gzip
import hashlib
import json
import os
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import urlsplit
from urllib.request import HTTPRedirectHandler, Request, build_opener


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def records(path):
    opener = gzip.open if path.suffix == ".gz" else open
    with opener(path, "rt", encoding="utf-8", newline="") as stream:
        while raw := stream.readline(262147):
            line = raw.removesuffix("\n").removesuffix("\r")
            if len(line) > 262144:
                raise ValueError("Record exceeds 262144 characters. Prepare a bounded pilot snapshot.")
            yield line


def fingerprint(path):
    digest = hashlib.sha256()
    count = 0
    for record in records(path):
        digest.update(record.encode("utf-8") + b"\n")
        count += 1
        if count > 100000:
            raise ValueError("An import batch supports at most 100000 records. Prepare smaller snapshots.")
    if not count:
        raise ValueError("The snapshot contains no records.")
    return digest.hexdigest(), count


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("snapshot", type=Path)
    parser.add_argument("--api", default="http://127.0.0.1:18880/api")
    parser.add_argument("--chunk-size", type=int, choices=range(1, 21), default=10)
    parser.add_argument("--max-chunks", type=int,
                        help="Stop after this many successful chunks for a controlled pilot.")
    parser.add_argument("--manifest", type=Path, help="Extractor manifest to verify SHA-256 and register the source URL.")
    args = parser.parse_args()
    if args.max_chunks is not None and args.max_chunks < 1:
        parser.error("--max-chunks must be positive.")
    base = urlsplit(args.api)
    if base.username or base.password or base.query or base.fragment or not (
        base.scheme == "https" and base.hostname or
        base.scheme == "http" and base.hostname in {"127.0.0.1", "localhost", "::1"}
    ):
        parser.error("Use a trusted HTTPS API or loopback HTTP.")
    token = os.environ.get("CARTIQ_ADMIN_TOKEN")
    if not token:
        parser.error("Set CARTIQ_ADMIN_TOKEN to a short-lived administrator token.")
    endpoint = args.api.rstrip("/") + "/admin/catalogue-imports/batches"
    transport = build_opener(NoRedirect())

    def request(method, url, body):
        payload = json.dumps(body, ensure_ascii=False).encode("utf-8")
        req = Request(url, payload, {
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/json",
        }, method=method)
        with transport.open(req, timeout=60) as response:
            return json.load(response)

    try:
        snapshot = args.snapshot.resolve(strict=True)
        before = snapshot.stat()
        sha256, total = fingerprint(snapshot)
        batch_request = {"sha256": sha256, "rows": total}
        if args.manifest is not None:
            manifest = json.loads(args.manifest.read_text(encoding="utf-8"))
            if not isinstance(manifest, dict) or manifest.get("output_jsonl_sha256") != sha256:
                raise ValueError("Snapshot fingerprint differs from the extractor manifest.")
            source_url = manifest.get("source")
            if not isinstance(source_url, str) or not source_url:
                raise ValueError("The extractor manifest has no source URL.")
            batch_request["sourceUrl"] = source_url
        batch = request("POST", endpoint, batch_request)
        batch_id = batch["id"]
        offset = batch["committed_rows"]

        submitted = 0

        def append(start, chunk):
            nonlocal submitted
            current = snapshot.stat()
            if (before.st_size, before.st_mtime_ns) != (current.st_size, current.st_mtime_ns):
                raise ValueError("Snapshot changed. Use the original file to resume.")
            progress = request("PUT", f"{endpoint}/{batch_id}/rows",
                               {"offset": start, "records": chunk})
            submitted += 1
            print(f"{batch_id} {progress['committed_rows']}/{total} "
                  f"review={progress['staged_rows']} correction={progress['correction_rows']} "
                  f"invalid={progress['invalid_rows']}", flush=True)

        print(f"Snapshot {sha256}, batch {batch_id}, resuming from {offset}/{total}", flush=True)
        chunk = []
        size = 0
        for index, line in enumerate(records(snapshot)):
            if index < offset:
                continue
            if len(chunk) == args.chunk_size or size + len(line) > 524288:
                append(index - len(chunk), chunk)
                if submitted == args.max_chunks:
                    print("Stopped at the committed checkpoint. Re-run with this snapshot to continue.")
                    return
                chunk = []
                size = 0
            chunk.append(line)
            size += len(line)
        if chunk:
            append(total - len(chunk), chunk)
    except (HTTPError, URLError, OSError, ValueError) as exc:
        parser.exit(1, f"Import stopped: {exc}. Re-run with the same unchanged snapshot to resume.\n")


if __name__ == "__main__":
    main()
