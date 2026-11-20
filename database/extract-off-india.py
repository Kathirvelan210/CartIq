"""Extract a bounded India pilot from an Open Food Facts JSONL dump or API response."""

import argparse
from collections import Counter
from datetime import datetime, timezone
import gzip
import hashlib
import json
from pathlib import Path
import re
from tempfile import NamedTemporaryFile
from urllib.parse import urlsplit


SOURCE = "https://openfoodfacts-ds.s3.eu-west-3.amazonaws.com/openfoodfacts-products.jsonl.gz"
ATTRIBUTION = "Open Food Facts contributors — https://world.openfoodfacts.org"
LICENSES = {"database": "ODbL-1.0", "contents": "DbCL-1.0", "images": "CC-BY-SA-3.0"}
INDIA = {"en:india", "india"}
GTIN = re.compile(r"(?:[0-9]{8}|[0-9]{12,14})")
MAX_SOURCE_LINE_BYTES = 16 * 1024 * 1024


def text(row, field):
    value = row.get(field)
    return value.strip() if isinstance(value, str) else ""


def tags(row, field):
    value = row.get(field)
    if isinstance(value, list):
        return [tag.strip().lower() for tag in value if isinstance(tag, str)]
    return []


def india_signals(row):
    return {
        "market": "en:india" in tags(row, "countries_tags"),
        "manufacturing": bool(INDIA.intersection(tags(row, "manufacturing_places_tags"))),
        "ingredient_origin": "en:india" in tags(row, "origins_tags"),
    }


def canonical_gtin(code):
    if not GTIN.fullmatch(code):
        return None
    checksum = sum(int(n) * (3 if i % 2 == 0 else 1)
                   for i, n in enumerate(reversed(code[:-1])))
    return code.zfill(14) if (-checksum) % 10 == int(code[-1]) else None


def source_headers(file):
    data = file.read_text(encoding="iso-8859-1").replace("\r\n", "\n").strip()
    response = data.rsplit("\n\n", 1)[-1].splitlines()
    status = re.match(r"HTTP/\S+ (\d{3})", response[0] if response else "")
    if not status or status[1] not in ("200", "206"):
        raise ValueError("Source headers must end with an HTTP 200 or 206 response.")
    headers = {key.lower(): value.strip()
               for key, _, value in (item.partition(":") for item in response[1:])}
    return int(status[1]), headers


def source_lines(source, is_prefix):
    with gzip.open(source, "rb") as reader:
        while True:
            try:
                raw = reader.readline(MAX_SOURCE_LINE_BYTES + 1)
            except EOFError:
                if not is_prefix:
                    raise
                return
            if not raw or (is_prefix and not raw.endswith(b"\n")):
                return
            if len(raw) > MAX_SOURCE_LINE_BYTES:
                raise ValueError("A source line exceeds the 16 MiB parsing limit.")
            yield raw


def api_lines(source):
    if source.stat().st_size > MAX_SOURCE_LINE_BYTES:
        raise ValueError("API response exceeds the 16 MiB parsing limit.")
    page = json.loads(source.read_bytes())
    if not isinstance(page, dict) or not isinstance(page.get("products"), list):
        raise ValueError("Expected an Open Food Facts response with a products array.")
    info = {key: page.get(key) for key in ("count", "page", "page_count", "page_size")}
    lines = (json.dumps(row, ensure_ascii=False, separators=(",", ":")).encode("utf-8") + b"\n"
             for row in page["products"])
    return info, lines


def extract(source, source_info, output, row_limit, examined_limit, mode, api_url):
    status, headers = source_info
    length = source.stat().st_size
    byte_range = headers.get("content-range")
    is_prefix = False
    if status == 206:
        match = re.fullmatch(r"bytes 0-(\d+)/(\d+)", byte_range or "")
        if not match or int(match[1]) + 1 != length or length > int(match[2]):
            raise ValueError("A partial gzip snapshot must start at byte zero and match Content-Range.")
        is_prefix = length < int(match[2])
    if headers.get("content-length") and int(headers["content-length"]) != length:
        raise ValueError("Source length differs from the download response.")
    if api_url:
        if status != 200 or not headers.get("date"):
            raise ValueError("The API response requires HTTP 200 with a Date header.")
        api_page, rows = api_lines(source)
    else:
        if not headers.get("etag") or not headers.get("last-modified"):
            raise ValueError("Bulk source headers must include ETag and Last-Modified.")
        api_page, rows = None, source_lines(source, is_prefix)

    counts = dict.fromkeys((
        "records_examined", "invalid_json", "india_relevant", "market_tagged",
        "manufacturing_tagged", "ingredient_origin_tagged", "valid_gtin",
        "invalid_gtin", "duplicate_gtin", "missing_name", "missing_brand",
        "ambiguous_brand", "missing_pack_size", "rows_written",
    ), 0)
    brands, categories, manufacturing_brands = Counter(), Counter(), Counter()
    seen = set()
    output_hash = hashlib.sha256()
    with output.open("wb") as target:
        for raw in rows:
            if counts["records_examined"] >= examined_limit or counts["rows_written"] >= row_limit:
                break
            counts["records_examined"] += 1
            try:
                row = json.loads(raw)
                if not isinstance(row, dict):
                    raise ValueError("Expected object")
            except (UnicodeError, ValueError):
                counts["invalid_json"] += 1
                continue

            evidence = india_signals(row)
            counts["market_tagged"] += evidence["market"]
            counts["manufacturing_tagged"] += evidence["manufacturing"]
            counts["ingredient_origin_tagged"] += evidence["ingredient_origin"]
            selected = (evidence["market"] if mode == "market" else
                        evidence["manufacturing"] if mode == "manufacturing" else
                        evidence["market"] or evidence["manufacturing"])
            if not selected:
                continue
            counts["india_relevant"] += 1
            name, brand, pack = (text(row, f) for f in ("product_name", "brands", "quantity"))
            counts["missing_name"] += not name
            counts["missing_brand"] += not brand
            counts["ambiguous_brand"] += "," in brand
            counts["missing_pack_size"] += not pack
            category = tags(row, "categories_tags")
            categories[category[-1] if category else "unclassified"] += 1
            if brand:
                normalized_brand = " ".join(brand.split()).casefold()
                brands[normalized_brand] += 1
                if evidence["manufacturing"]:
                    manufacturing_brands[normalized_brand] += 1
            gtin = canonical_gtin(text(row, "code"))
            if gtin is None:
                counts["invalid_gtin"] += 1
            else:
                counts["valid_gtin"] += 1
                if gtin in seen:
                    counts["duplicate_gtin"] += 1
                seen.add(gtin)
            # Keep complete source records, including quality issues, for admin review.
            line = raw.removesuffix(b"\n").removesuffix(b"\r") + b"\n"
            if len(line.decode("utf-8").encode("utf-16-le")) // 2 - 1 > 262144:
                raise ValueError("A selected source row exceeds the staging limit of 262144 characters.")
            target.write(line)
            output_hash.update(line)
            counts["rows_written"] += 1

    with source.open("rb") as compressed:
        source_hash = hashlib.file_digest(compressed, "sha256").hexdigest()
    return {
        "source": api_url or SOURCE, "source_etag": headers.get("etag"),
        "source_last_modified": headers.get("last-modified"), "source_http_status": status,
        "source_response_date": headers.get("date"), "source_api_page": api_page,
        "source_content_range": byte_range, "source_file_sha256": source_hash,
        "extracted_utc": datetime.now(timezone.utc).isoformat(),
        "attribution": ATTRIBUTION, "licenses": LICENSES,
        "filter": {"mode": mode, "row_limit": row_limit, "examined_limit": examined_limit},
        "scope": "Source-order sample. Market and manufacture tags are contributed metadata. Ingredient origin alone does not qualify.",
        "source_is_bulk_prefix": is_prefix,
        "raw_preservation": ("Retain the original API response alongside the JSONL, which re-serializes complete objects."
                             if api_url else "Original source lines retained with LF line endings."),
        "output_jsonl_sha256": output_hash.hexdigest(), "counts": counts,
        "source_leaf_categories": dict(sorted(categories.items())),
        "reported_brand_labels": dict(sorted(brands.items())),
        "brand_labels_on_manufacture_tagged_records": dict(sorted(manufacturing_brands.items())),
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path, help="Downloaded .gz file or raw API response")
    parser.add_argument("output", type=Path, help="Destination .jsonl file")
    parser.add_argument("--headers", required=True, type=Path, help="HTTP headers from curl -D")
    parser.add_argument("--api-url", help="Exact official OFF API URL used to collect a single JSON page")
    parser.add_argument("--limit", type=int, default=100)
    parser.add_argument("--max-examined", type=int, default=250_000)
    parser.add_argument("--mode", choices=("market", "manufacturing", "market-or-manufacturing"),
                        default="market-or-manufacturing")
    args = parser.parse_args()
    if not 1 <= args.limit <= 100_000 or args.max_examined < 1:
        parser.error("--limit must be 1–100000 and --max-examined must be positive.")
    if args.api_url:
        url = urlsplit(args.api_url)
        if url.scheme != "https" or url.netloc not in {"in.openfoodfacts.org", "world.openfoodfacts.org"} or url.path != "/api/v2/search":
            parser.error("--api-url must be an official Open Food Facts v2 search URL.")
    manifest = args.output.with_suffix(".manifest.json")
    if len({p.resolve() for p in (args.source, args.headers, args.output, manifest)}) != 4:
        parser.error("Source, headers, output and manifest must be different files.")
    try:
        with NamedTemporaryFile(dir=args.output.parent, delete=False) as handle:
            work = Path(handle.name)
        try:
            result = extract(args.source, source_headers(args.headers), work,
                             args.limit, args.max_examined, args.mode, args.api_url)
            if not result["counts"]["rows_written"]:
                raise ValueError("No matching records were found.")
            work.replace(args.output)
            manifest.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        finally:
            work.unlink(missing_ok=True)
    except (OSError, EOFError, ValueError) as exc:
        parser.exit(1, f"Extraction stopped: {exc}\n")
    print(json.dumps(result["counts"], sort_keys=True))


if __name__ == "__main__":
    main()
