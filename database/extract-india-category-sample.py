"""Archive and replay small category samples for the India grocery catalogue."""

import argparse
from decimal import Decimal, localcontext
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import sys
import time
import unicodedata
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


def load_existing_extractor():
    spec = importlib.util.spec_from_file_location("off_extract", Path(__file__).with_name("extract-off-india.py"))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


off = load_existing_extractor()
FOOD = "in.openfoodfacts.org"
BEAUTY = "world.openbeautyfacts.org"
HOUSEHOLD = "world.openproductsfacts.org"
REUSE = "https://openfoodfacts.github.io/documentation/docs/Product-Opener/api/tutorials/license-be-on-the-legal-side/"
BEAUTY_LICENCE = "https://www.data.gouv.fr/datasets/open-beauty-facts"
BEAUTY_IMAGE_GUIDANCE = "https://wiki.openfoodfacts.org/images/e/ed/OpenBeautyFacts.pdf"
HOUSEHOLD_LICENCE = "https://www.data.gouv.fr/datasets/open-products-facts"
MAX_REPLY = 16 * 1024 * 1024

# The first group of requests used the category before page_size. Preserve the exact URLs for replay.
def endpoint(tag="", page_size=20, host=FOOD, category_first=False):
    base = f"https://{host}/api/v2/search?countries_tags=en:india"
    paging = f"&page_size={page_size}&page=1&sort_by=unique_scans_n"
    category = f"&categories_tags=en:{tag}" if tag else ""
    return base + (category + paging if category_first else paging + category)


QUERIES = {
    key: endpoint(tag, 25, category_first=True)
    for key, tag in {
        "rice": "rices", "flour": "flours", "pulses": "pulses", "oil": "plant-based-oils",
        "spices": "spices", "dairy": "dairies", "snacks": "salty-snacks",
        "biscuits": "biscuits-and-cakes", "beverages": "beverages", "sauces": "sauces", "ghee": "ghee"
    }.items()
}
QUERIES.update({
    key: endpoint(tag)
    for key, tag in {
        "flour-alt": "wheat-flours", "pulses-alt": "lentils", "oil-alt": "vegetable-oils",
        "snacks-alt": "snacks", "biscuits-alt": "biscuits", "beverages-alt": "non-alcoholic-beverages",
        "sauces-alt": "condiments", "oil-mustard": "mustard-oils", "oil-sunflower": "sunflower-oils",
        "biscuits-sweet": "sweet-snacks", "bakery": "breads", "cookies": "cookies", "ghee-v3": "clarified-butter"
    }.items()
})
QUERIES.update({
    "personal": endpoint(host=BEAUTY),
    "household": endpoint("household-products", host=HOUSEHOLD),
    "household-country": endpoint(host=HOUSEHOLD),
    "household-india": endpoint(host="in.openproductsfacts.org"),
    "prior-market": endpoint(page_size=40),
})

# Ordered precedence resolves overlapping taxonomy tags. Scope narrower slices when a broad query is unavailable.
CATEGORIES = {
    "rice-and-grains": ("rice", ("rices", "rice-grains", "cereal-grains", "puffed-rice", "rolled-oats")),
    "flour-and-atta": ("flour flour-alt", ("flours", "wheat-flours", "cereal-flours", "whole-wheat-flours")),
    "pulses-and-lentils": ("pulses pulses-alt", ("pulses", "lentils", "dry-legumes")),
    "cooking-oil-and-ghee": (
        "oil ghee oil-alt oil-mustard oil-sunflower ghee-v3 dairy",
        ("vegetable-oils", "plant-based-oils", "mustard-oils", "sunflower-oils", "ghee", "clarified-butter")
    ),
    "spices-and-masalas": ("spices", ("spices", "spice-mixes", "masalas")),
    "dairy": ("dairy", ("dairies",)),
    "packaged-snacks": (
        "snacks snacks-alt prior-market",
        ("snacks", "salty-snacks", "crisps", "namkeen", "chocolates", "confectioneries")
    ),
    "biscuits-and-bakery": (
        "biscuits biscuits-alt biscuits-sweet bakery cookies snacks-alt prior-market",
        ("biscuits-and-cakes", "biscuits-and-crackers", "biscuits", "cakes", "breads", "cookies")
    ),
    "beverages": ("beverages beverages-alt prior-market", ("beverages", "non-alcoholic-beverages", "waters", "fruit-juices")),
    "sauces-and-condiments": ("sauces sauces-alt prior-market", ("condiments", "sauces", "ketchup")),
    "personal-care": (
        "personal", ("cleansers", "toothpastes", "cosmetic-products", "cosmetic products", "face-wash", "face wash",
                     "lip-balms", "shampoos", "deodorants", "moisturizers", "skin-care", "hygiene")
    ),
    "household-essentials": (
        "household household-country household-india",
        ("household-products", "household-cleaning-products", "laundry-detergents", "detergents", "dishwashing-liquids")
    ),
}
FOOD_PRECEDENCE = (
    "flour-and-atta", "cooking-oil-and-ghee", "pulses-and-lentils", "biscuits-and-bakery",
    "dairy", "spices-and-masalas", "sauces-and-condiments", "rice-and-grains", "beverages", "packaged-snacks"
)
PACK = re.compile(r"([0-9]+(?:[.,][0-9]+)?)\s*(kg|g|ml|l|litres?|liters?|pieces?|pcs)", re.I)
MAX_AMOUNT = Decimal("999999999.999")
STATUS = ("records_examined", "india_market", "valid_gtin", "unique_products", "unique_variants",
          "accepted", "rejected", "review_required", "quota_excess", "missing_brand", "missing_pack_size",
          "prior_gtin_overlap", "selected_prior_overlap")


def normal(value):
    return " ".join(unicodedata.normalize("NFKC", value).casefold().split())


def tags(row):
    return {tag.removeprefix("en:") for tag in off.tags(row, "categories_tags") if tag.startswith("en:")}


def classify(row, domain):
    taxonomy = tags(row)
    if domain == FOOD:
        # Some namkeen records also carry a stale biscuit tag. Prefer specific salty tags.
        if taxonomy.intersection({"namkeen", "salty-snacks", "crisps"}):
            taxonomy = taxonomy.difference(CATEGORIES["biscuits-and-bakery"][1])
        for group in FOOD_PRECEDENCE:
            if taxonomy.intersection(CATEGORIES[group][1]):
                return group
        return None
    group = "personal-care" if domain == BEAUTY else "household-essentials"
    return group if taxonomy.intersection(CATEGORIES[group][1]) else None


def supported_pack(raw):
    if len(raw) > 60:
        return False
    m = PACK.fullmatch(raw)
    if not m:
        return False
    with localcontext() as context:
        context.prec = 64
        value = Decimal(m[1].replace(",", "."))
        unit = m[2].lower()
        if unit == "kg" or unit.startswith("l"):
            value *= 1000
        precision = 0 if unit.startswith("piece") or unit == "pcs" else 3
        return 0 < value <= MAX_AMOUNT and -value.normalize().as_tuple().exponent <= precision


def restricted_gtin(gtin):
    # OFF may assign 200-prefixed codes to products without retail barcodes.
    ean = gtin[1:] if gtin.startswith("0") else gtin
    return ean.startswith("200") and len(ean) == 13


def quality(row, gtin):
    if not gtin:
        return "rejected", "Invalid GTIN length or check digit"
    if str(row.get("obsolete", "")).lower() in {"1", "true", "on", "yes"}:
        return "rejected", "Source marks the product obsolete"
    reasons = []
    name, brand, quantity = [off.text(row, key) for key in ("product_name", "brands", "quantity")]
    if restricted_gtin(gtin):
        reasons.append("200-prefixed code may be source-assigned; verify the retail barcode")
    if not name or len(name) > 150:
        reasons.append("Missing or overlength product name")
    if not brand or len(brand) > 100 or "," in brand:
        reasons.append("Missing, overlength or multi-label brand")
    if not supported_pack(quantity):
        reasons.append("Missing, unsupported or ambiguous pack size")
    if len(json.dumps(row, ensure_ascii=False, separators=(",", ":")).encode("utf-16-le")) // 2 > 262144:
        reasons.append("Record exceeds CartIQ staging limit")
    return ("review_required", "; ".join(reasons)) if reasons else ("accepted", "Passed intake checks; admin approval required")


def fetch_sources(directory, agent, retry):
    for key, url in QUERIES.items():
        header = directory / (key + ".headers.txt")
        body = directory / (key + ".json")
        if header.exists():
            try:
                off.source_headers(header)
                if body.exists():
                    continue
            except ValueError:
                pass
            if not retry:
                continue
        if key == "prior-market":
            # This optional response is a historical archive supplied by the operator.
            continue
        req = Request(url, headers={"User-Agent": agent, "Accept": "application/json"})
        try:
            with urlopen(req, timeout=24) as res:
                archive_response(res, header, body)
        except HTTPError as err:
            with err:
                archive_response(err, header, body)
            if err.code == 429:
                print("Source rate limit reached. Wait for Retry-After, then resume with --retry-failed.", file=sys.stderr)
                return
        except (URLError, TimeoutError, ValueError) as err:
            header.write_text("", encoding="ascii")
            print(f"{key}: {err}", file=sys.stderr)
        finally:
            time.sleep(7)


def archive_response(res, header, body):
    data = res.read(MAX_REPLY + 1)
    if len(data) > MAX_REPLY:
        raise ValueError("API response exceeded 16 MiB")
    body.write_bytes(data)
    header.write_bytes((f"HTTP/1.1 {res.status}\r\n" + str(res.headers)).encode("iso-8859-1"))


def read_sources(directory):
    pages, provenance = {}, {}
    for key, url in QUERIES.items():
        header = directory / (key + ".headers.txt")
        body = directory / (key + ".json")
        # Operator archives from curl may store exact query URL alongside the response.
        recorded_url = directory / (key + ".url")
        if recorded_url.exists() and recorded_url.read_text(encoding="ascii").strip() != url:
            raise ValueError(f"Archive URL differs from configured URL for {key}")
        entry = {"url": url, "status": "not_attempted"}
        provenance[key] = entry
        if not header.exists():
            continue
        if body.exists():
            with body.open("rb") as stream:
                entry["response_sha256"] = hashlib.file_digest(stream, "sha256").hexdigest()
        entry["header_sha256"] = hashlib.sha256(header.read_bytes()).hexdigest()
        try:
            status, headers = off.source_headers(header)
            entry.update(status=status, date=headers.get("date"))
            if status != 200:
                raise ValueError(f"Expected HTTP 200, got {status}")
            page, lines = off.api_lines(body)
            pages[key] = [json.loads(line) for line in lines]
            entry.update(reported_total=page["count"], products_returned=len(pages[key]))
        except (OSError, ValueError) as err:
            status_match = re.findall(r"^HTTP/\S+ (\d{3})", header.read_text(encoding="iso-8859-1"), re.M)
            entry.update(status=int(status_match[-1]) if status_match else "no_http_response", issue=str(err))
    return pages, provenance


def evaluate(group, page_ids, pages, quota, prior_codes):
    counts = dict.fromkeys(STATUS, 0)
    variants, products, selection, audit = set(), set(), [], []
    for key in page_ids.split():
        url = QUERIES[key]
        host = url.split("/")[2]
        for row in pages.get(key, []):
            counts["records_examined"] += 1
            if not isinstance(row, dict):
                audit.append({"source": key, "decision": "excluded", "reason": "Malformed source object"})
                continue
            evidence = off.india_signals(row)
            counts["india_market"] += evidence["market"]
            if not evidence["market"] or classify(row, host) != group:
                continue
            name, brand, pack = (off.text(row, f) for f in ("product_name", "brands", "quantity"))
            code = off.text(row, "code")
            gtin = off.canonical_gtin(code)
            counts["missing_brand"] += not brand
            counts["missing_pack_size"] += not pack
            counts["valid_gtin"] += gtin is not None
            if gtin not in variants:
                decision, reason = quality(row, gtin)
                if gtin:
                    variants.add(gtin)
                    if name and brand and "," not in brand:
                        products.add((normal(brand), normal(name)))
                    counts["prior_gtin_overlap"] += gtin in prior_codes
            else:
                decision, reason = "rejected", "Repeated canonical GTIN in this category sample"
            if decision == "accepted":
                if len(selection) < quota:
                    selection.append(row)
                    counts["selected_prior_overlap"] += gtin in prior_codes
                else:
                    decision, reason = "quota_excess", "Intake eligible beyond this category quota"
            counts[decision] += 1
            audit.append({"source": key, "source_product": f"https://{host}/product/{code}" if code.isascii() and code.isdigit() else None,
                          "code": code, "gtin": gtin, "name": name, "brand": brand, "pack": pack, "india_metadata": evidence,
                          "decision": decision, "reason": reason})
    counts["unique_variants"], counts["unique_products"] = len(variants), len(products)
    return counts, selection, audit


def archived_registry(snapshots):
    codes, inputs = set(), []
    for path in snapshots:
        with path.open("rb") as stream:
            inputs.append({"filename": path.name, "sha256": hashlib.file_digest(stream, "sha256").hexdigest()})
        with path.open(encoding="utf-8") as stream:
            for line in stream:
                code = off.canonical_gtin(off.text(json.loads(line), "code"))
                if code:
                    codes.add(code)
    return codes, inputs


def build(archive, output, quota, snapshots):
    pages, sources = read_sources(archive)
    prior_codes, registry_inputs = archived_registry(snapshots)
    output.mkdir(parents=True, exist_ok=True)
    results, selected_gtins = {}, set()
    for group, (page_ids, _) in CATEGORIES.items():
        counts, selection, audit = evaluate(group, page_ids, pages, quota, prior_codes)
        codes = {off.canonical_gtin(off.text(row, "code")) for row in selection}
        if codes & selected_gtins:
            raise ValueError("A canonical GTIN appears in more than one category selection")
        selected_gtins.update(codes)
        selected_file = output / f"{group}.jsonl"
        with selected_file.open("w", encoding="utf-8", newline="\n") as target:
            for row in selection:
                target.write(json.dumps(row, ensure_ascii=False, separators=(",", ":")) + "\n")
        results[group] = {"sources": page_ids.split(), "counts": counts, "rows": audit,
                          "payload_sha256": hashlib.sha256(selected_file.read_bytes()).hexdigest()}
    manifest = {
        "requested_quota_per_category": quota,
        "total_intake_accepted": len(selected_gtins),
        "archived_page_objects_examined": sum(len(rows) for rows in pages.values()),
        "comparison_registry": {"inputs": registry_inputs, "distinct_gtins": len(prior_codes),
                                "selected_overlap": len(selected_gtins & prior_codes)},
        "method": "Explicit India sales-market tag and deterministic category priority. Per-category rows examined "
                  "are page occurrences, including overlapping pages. Category metrics other than examined and India-market "
                  "are scoped to matching category. Products are provisional normalized brand/name groups; variants are "
                  "distinct checksum-valid GTINs. Quota is applied to intake-eligible unique variants. All still need admin approval.",
        "attribution": {"food": off.ATTRIBUTION, "personal-care": "Open Beauty Facts contributors — https://world.openbeautyfacts.org",
                        "household-essentials": "Open Products Facts contributors — https://world.openproductsfacts.org"},
        "licences": {"food": off.LICENSES,
                     "personal-care": {"database": "ODbL-1.0", "licence_source": BEAUTY_LICENCE,
                                       "images": "CC BY-SA", "image_licence_source": BEAUTY_IMAGE_GUIDANCE},
                     "household-essentials": {"database": "ODbL-1.0", "licence_source": HOUSEHOLD_LICENCE},
                     "food_reuse_guidance": REUSE},
        "import_safety": "These evaluation extracts are not directly staged. Preserve source-specific attribution. "
                         "Open Beauty Facts is separate and is not supported by the CartIQ food importer.",
        "sources": sources, "categories": results,
    }
    (output / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
                                          encoding="utf-8", newline="\n")
    for category, report in results.items():
        print(category, json.dumps(report["counts"]))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive", type=Path, help="Archive directory with .json response files and .headers.txt files")
    parser.add_argument("output", type=Path, help="Separate directory for review manifest and category JSONL")
    parser.add_argument("--quota", type=int, default=8)
    parser.add_argument("--compare-jsonl", type=Path, action="append", default=[],
                        help="Prior source JSONL to measure overlap. May be repeated.")
    parser.add_argument("--fetch", metavar="USER_AGENT", help="Fetch missing pages using an identifying contact")
    parser.add_argument("--retry-failed", action="store_true", help="Retry prior failed responses when fetching")
    args = parser.parse_args()
    if args.archive.resolve() == args.output.resolve() or not 1 <= args.quota <= 25:
        parser.error("Use separate archive and output directories and a quota of 1–25.")
    if args.fetch:
        args.archive.mkdir(parents=True, exist_ok=True)
        fetch_sources(args.archive, args.fetch, args.retry_failed)
    if not args.archive.is_dir():
        parser.error("Archive directory does not exist.")
    try:
        build(args.archive, args.output, args.quota, args.compare_jsonl)
    except (OSError, ValueError) as err:
        parser.exit(1, f"Extraction stopped: {err}\n")


if __name__ == "__main__":
    main()
