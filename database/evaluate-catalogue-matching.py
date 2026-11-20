"""Evaluate the catalogue rule through a local admin API, alongside fixed baselines."""

import argparse
from collections import Counter
from decimal import Decimal
import json
import os
from pathlib import Path
import re
import unicodedata
from urllib.parse import urlsplit
from urllib.request import HTTPRedirectHandler, ProxyHandler, Request, build_opener


MATCH = "match"
DISTINCT = "distinct"
REVIEW = "review"
SPLITS = ("train", "validation", "test")
COHORTS = ("observed", "controlled")
INPUT_FIELDS = ("gtin", "name", "brand", "pack", "variant", "formulation")
GROUND_TRUTH = {"SAME_VARIANT": MATCH, "DIFFERENT_VARIANT": DISTINCT, "UNCERTAIN": REVIEW}
RULE_DECISIONS = {"EXACT_GTIN": MATCH, "SUPPORTED_MATCH": MATCH, "DISTINCT": DISTINCT,
                  "REVIEW": REVIEW, "INSUFFICIENT_INFORMATION": REVIEW}
SLICE_NAMES = ("missing_gtin", "invalid_or_restricted_gtin", "different_valid_gtins", "conflicting_gtin",
               "multipack", "multipack_conflict", "pack_ambiguous")
PACK = re.compile(
    r"(?:(?P<count>[1-9][0-9]{0,4})\s*[x×]\s*)?"
    r"(?P<quantity>[0-9]+(?:[.,][0-9]+)?)\s*"
    r"(?P<unit>kg|g|gm|gms|grm|ml|mls|l|ltr|ltrs|litre|litres|liter|liters|piece|pieces|pcs)"
)
PACK_SUFFIX = re.compile(r"(?<!\S)" + PACK.pattern + r"$")
GENERIC_ATTRIBUTES = {"n a", "unknown", "unspecified", "standard", "default"}


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def canonical_gtin(value, allow_restricted=False):
    if not value or not re.fullmatch(r"[0-9]{8}|[0-9]{12,14}", value):
        return None
    if sum(int(digit) * (1 if index % 2 == 0 else 3)
           for index, digit in enumerate(reversed(value))) % 10:
        return None
    gtin = value.zfill(14)
    return None if gtin.startswith("0200") and not allow_restricted else gtin


def load_pairs(path):
    dataset = json.loads(path.read_text(encoding="utf-8"))
    items, records = dataset["items"], dataset["pairs"]
    classifications, seen_pairs, references, split_ownership, source_identities = [], {}, set(), {}, {}
    for index, record in enumerate(records):
        split = record["split"]
        reference = record.get("reference", f"pair-{index + 1:03}")
        cohort = record.get("cohort", "observed")
        if split not in SPLITS or cohort not in COHORTS:
            raise ValueError(f"Invalid split or cohort: {record}")
        if not isinstance(reference, str) or not reference.strip() or len(reference) > 100 or reference in references:
            raise ValueError(f"Invalid or repeated pair reference: {reference!r}")
        references.add(reference)
        source_rows = {side: items[record[side]] for side in ("left", "right")}
        if source_rows["left"]["sourceRecord"] == source_rows["right"]["sourceRecord"]:
            raise ValueError(f"The two sides reuse one source record: {record}")
        overrides = record.get("overrides", {})
        if not isinstance(overrides, dict) or set(overrides) - source_rows.keys() or (cohort == "observed" and overrides):
            raise ValueError(f"Invalid pair overrides: {reference}")
        comparison, signature = {}, []
        for side, item in source_rows.items():
            before = {key: item["identity"].get(key) for key in INPUT_FIELDS}
            if any(value is not None and not isinstance(value, str) for value in before.values()):
                raise ValueError(f"Source identity fields must be text or null: {reference}/{side}")
            source = item["sourceRecord"]
            identity = (item["family"], before)
            if source_identities.setdefault(source, identity) != identity:
                raise ValueError(f"Inconsistent identity for source record: {source}")
            changes = overrides.get(side, {})
            if not isinstance(changes, dict) or set(changes) - before.keys():
                raise ValueError(f"Invalid identity field overrides: {reference}/{side}")
            if any(value is not None and not isinstance(value, str) for value in changes.values()):
                raise ValueError(f"Override identity fields must be text or null: {reference}/{side}")
            actual = before | changes
            comparison[side] = actual
            identifiers = [("source_record", item["sourceRecord"]), ("family", item["family"])]
            for barcode in (before["gtin"], actual["gtin"]):
                gtin = canonical_gtin(barcode, allow_restricted=True)
                if gtin:
                    identifiers.append(("gtin", gtin))
            for key in identifiers:
                if not isinstance(key[1], str) or not key[1].strip():
                    raise ValueError(f"Source record and family are required: {reference}/{side}")
                if split_ownership.setdefault(key, split) != split:
                    raise ValueError(f"Split overlap: {key}")
            changed = {key: value for key, value in actual.items() if value != before[key]}
            signature.append((item["sourceRecord"], json.dumps(changed, sort_keys=True)))
        if cohort == "controlled" and all(encoded == "{}" for _, encoded in signature):
            raise ValueError(f"Controlled pair must change an input field: {reference}")
        source_pair = tuple(sorted(item["sourceRecord"] for item in source_rows.values()))
        label = GROUND_TRUTH[record["label"]]
        original_label, variants = seen_pairs.setdefault(source_pair, (label, set()))
        variant = tuple(sorted(signature))
        if label != original_label or variant in variants:
            raise ValueError(f"Conflicting label or repeated source pair variant: {reference}")
        variants.add(variant)
        row = {"reference": reference, "split": split, "label": label,
               "scenario": record["scenario"], "comparison": comparison}
        if "cohort" in record:
            row["cohort"] = cohort
        if "origin" in record:
            origin = record["origin"]
            if not isinstance(origin, str) or not origin.strip():
                raise ValueError(f"Invalid pair origin: {reference}")
            row["origin"] = origin
        if "sliceTags" in record:
            tags = record["sliceTags"]
            if not isinstance(tags, list) or not all(isinstance(tag, str) and tag in SLICE_NAMES for tag in tags):
                raise ValueError(f"Unknown slice tag: {reference}")
            row["sliceTags"] = tags
        classifications.append(row)
    return classifications


def gtin_baseline(left, right):
    left_code, right_code = canonical_gtin(left["gtin"]), canonical_gtin(right["gtin"])
    if not left_code or not right_code:
        return REVIEW
    return MATCH if left_code == right_code else DISTINCT


def tokens(value):
    text = unicodedata.normalize("NFKC", value or "").lower()
    return set("".join(char if unicodedata.category(char)[0] in "LNM" else " " for char in text).split())


def title_brand_baseline(left, right):
    left_title, right_title = tokens(left["name"]), tokens(right["name"])
    left_brand, right_brand = tokens(left["brand"]), tokens(right["brand"])
    if not all((left_title, right_title, left_brand, right_brand)):
        return REVIEW
    similarity = len(left_title & right_title) / len(left_title | right_title)
    return MATCH if left_brand == right_brand and similarity >= 0.7 else DISTINCT


def normalized(value):
    text = unicodedata.normalize("NFKC", value or "").casefold()
    return " ".join("".join(char if unicodedata.category(char)[0] in "LNM" else " "
                            for char in text).split())


def usable_gtin(value):
    return canonical_gtin(unicodedata.normalize("NFKC", value or "").strip())


def normalized_pack(value):
    raw = unicodedata.normalize("NFKC", value or "").strip().casefold()
    match = PACK.fullmatch(raw) if len(raw) <= 60 else None
    if not match:
        return None
    digits = match["quantity"].replace(",", ".")
    whole, _, fraction = digits.partition(".")
    if len(whole) > 9 or len(fraction.rstrip("0")) > 6:
        return None
    quantity = Decimal(digits)
    if quantity <= 0:
        return None
    unit = match["unit"]
    count = int(match["count"] or 1)
    if unit in {"kg", "l", "ltr", "ltrs", "litre", "litres", "liter", "liters"}:
        return count, quantity * 1000, "g" if unit == "kg" else "ml"
    if unit in {"piece", "pieces", "pcs"}:
        return (count, quantity, "piece") if quantity == quantity.to_integral_value() else None
    return count, quantity, "g" if unit in {"g", "gm", "gms", "grm"} else "ml"


def comparable_title(identity, pack):
    title = unicodedata.normalize("NFKC", identity["name"] or "").strip().casefold()
    suffix = PACK_SUFFIX.search(title)
    if suffix and pack == normalized_pack(suffix[0]):
        title = title[:suffix.start()]
    return set(normalized(title).split()) - set(normalized(identity["brand"]).split())


def comparable_field(identity, field):
    value = normalized(identity[field])
    return None if not value or value in GENERIC_ATTRIBUTES else value


def conservative_baseline(left, right):
    left_code, right_code = (usable_gtin(item["gtin"]) for item in (left, right))
    if any((item["gtin"] or "").strip() and not code
           for item, code in ((left, left_code), (right, right_code))):
        return REVIEW, "invalid_or_restricted_gtin"
    left_pack, right_pack = (normalized_pack(item["pack"]) for item in (left, right))
    shared = left_code is not None and left_code == right_code
    fields = ((left_pack, right_pack), *(tuple(comparable_field(item, field) for item in (left, right))
                                        for field in ("brand", "variant", "formulation")))
    if any(a is not None and b is not None and a != b for a, b in fields):
        return (REVIEW if shared else DISTINCT), "explicit_attribute_conflict"
    left_brand, right_brand = fields[1]
    if shared and left_brand and right_brand and left_pack and right_pack:
        left_title = comparable_title(left, left_pack)
        right_title = comparable_title(right, right_pack)
        if left_title and right_title and (left_title <= right_title or right_title <= left_title):
            return MATCH, "shared_gtin_with_brand_pack_and_title"
    return REVIEW, "insufficient_corroboration"


def matching_slices(pair):
    identities = list(pair["comparison"].values())
    raw = [(item["gtin"] or "").strip() for item in identities]
    codes = [usable_gtin(value) for value in raw]
    packs = [normalized_pack(item["pack"]) for item in identities]
    groups = set(pair.get("sliceTags", ()))
    scenario = pair["scenario"].casefold().replace("-", "_")
    if not all(raw):
        groups.add("missing_gtin")
    if any(value and not code for value, code in zip(raw, codes)):
        groups.add("invalid_or_restricted_gtin")
    if all(codes) and codes[0] != codes[1]:
        groups.add("different_valid_gtins")
    if ("conflict" in scenario and ("gtin" in scenario or "barcode" in scenario)) \
            or (codes[0] and codes[0] == codes[1] and all(packs) and packs[0] != packs[1]):
        groups.add("conflicting_gtin")
    if "multipack" in scenario or any(pack and pack[0] > 1 for pack in packs):
        groups.add("multipack")
    if ("multipack_vs" in scenario or "flattened_multipack" in scenario
            or (all(packs) and packs[0][0] != packs[1][0])):
        groups.update(("multipack", "multipack_conflict"))
    if not all(packs):
        groups.add("pack_ambiguous")
    return groups


def predictions(transport, api, token, pairs, hybrid=False):
    results = {}
    endpoint = api + "/admin/catalogue-matching/review"
    if hybrid:
        endpoint += "?method=hybrid"
    for start in range(0, len(pairs), 50):
        payload = [{"reference": p["reference"], "comparison": p["comparison"]}
                   for p in pairs[start:start+50]]
        request = Request(endpoint,
                          data=json.dumps({"pairs": payload}).encode("utf-8"), method="POST",
                          headers={"Content-Type": "application/json", "Authorization": f"Bearer {token}"})
        with transport.open(request, timeout=30) as response:
            chunk = json.load(response)
        if [r["reference"] for r in chunk] != [p["reference"] for p in payload]:
            raise ValueError("Review response does not match submitted pair references")
        results.update({r["reference"]: r["evidence"] for r in chunk})
    return results


def metrics(rows):
    def ratio(n, d):
        return round(n / d, 4) if d else None

    counts = Counter((truth, prediction) for truth, prediction, _ in rows)
    tp, fp = counts[MATCH, MATCH], counts[DISTINCT, MATCH]
    fn = sum(counts[MATCH, label] for label in (DISTINCT, REVIEW))
    tn = sum(counts[DISTINCT, label] for label in (DISTINCT, REVIEW))
    gtin_rows = [(truth, prediction) for truth, prediction, eligible in rows if eligible and truth != REVIEW]
    review_correct = sum((truth == REVIEW) == (prediction == REVIEW) for truth, prediction, _ in rows)
    decided = sum(prediction != REVIEW for _, prediction, _ in rows)
    return {
        "pairs": len(rows),
        "definitePairs": tp + fp + tn + fn,
        "tp": tp, "fp": fp, "fn": fn, "tn": tn,
        "precision": ratio(tp, tp+fp), "recall": ratio(tp, tp+fn),
        "f1": ratio(2*tp, 2*tp+fp+fn),
        "falsePositiveRate": ratio(fp, fp+tn), "falseNegativeRate": ratio(fn, tp+fn),
        "reviewClassificationAccuracy": ratio(review_correct, len(rows)),
        "unresolvedRoutedToReview": counts[REVIEW, REVIEW],
        "unresolvedTotal": sum(counts[REVIEW, label] for label in (MATCH, DISTINCT, REVIEW)),
        "coverage": ratio(decided, len(rows)),
        "twoValidGtinDefinitePairs": len(gtin_rows),
        "twoValidGtinDecisionAccuracy": ratio(sum(truth == prediction for truth, prediction in gtin_rows),
                                              len(gtin_rows)),
        "decisionCounts": {f"{truth}/{prediction}": count for (truth, prediction), count in sorted(counts.items())},
    }


def report(pairs, evidence, hybrid_evidence=None, conservative=False):
    def decisions(pair):
        left, right = (pair["comparison"][side] for side in ("left", "right"))
        result = {"scorer": RULE_DECISIONS[evidence[pair["reference"]]["decision"]],
                  "gtinOnly": gtin_baseline(left, right),
                  "titleBrand": title_brand_baseline(left, right)}
        if hybrid_evidence is not None:
            result["hybrid"] = RULE_DECISIONS[hybrid_evidence[pair["reference"]]["decision"]]
        return result

    methods = ("scorer", "gtinOnly", "titleBrand")
    per_pair = [{**p, "decisions": decisions(p), "ruleEvidence": evidence[p["reference"]]} for p in pairs]
    if hybrid_evidence is not None:
        for pair in per_pair:
            pair["hybridEvidence"] = hybrid_evidence[pair["reference"]]
        methods += ("hybrid",)
    if conservative:
        for pair in per_pair:
            recommendation, reason = conservative_baseline(**pair["comparison"])
            pair["decisions"]["conservative"] = recommendation
            pair["conservativeReason"] = reason
            pair["slices"] = sorted(matching_slices(pair))
        methods += ("conservative",)

    def subset_metrics(group):
        return {
            method: metrics([(p["label"], p["decisions"][method],
                              all(canonical_gtin(side["gtin"]) for side in p["comparison"].values()))
                             for p in group])
            for method in methods
        }

    def confidence_counts(group, evidence_key, method):
        counts = Counter((p[evidence_key]["confidence"], p["label"], p["decisions"][method]) for p in group)
        return {level: {f"{truth}/{decision}": count for (tier, truth, decision), count
                        in sorted(counts.items()) if tier == level} for level in ("HIGH", "MEDIUM", "LOW")}

    by_split = {split: [p for p in per_pair if p["split"] == split] for split in SPLITS}
    by_split["all"] = per_pair
    by_cohort = {
        split: {cohort: [p for p in group if p.get("cohort", "observed") == cohort] for cohort in COHORTS}
        for split, group in by_split.items()
    }
    # Keep the original report shape for the historical benchmark.
    summaries = {split: subset_metrics(groups["observed"]) for split, groups in by_cohort.items()}
    outcome = {
        "metrics": summaries,
        "scorerConfidenceCounts": {split: confidence_counts(by_cohort[split]["observed"], "ruleEvidence", "scorer")
                                   for split in ("test", "all")},
        "pairs": per_pair,
    }
    if hybrid_evidence is not None or any(p.get("cohort") == "controlled" for p in per_pair):
        outcome["cohortMetrics"] = {
            split: {cohort: subset_metrics(group) for cohort, group in groups.items()}
            for split, groups in by_cohort.items()
        }
        outcome["cohortErrorReferences"] = {
            cohort: {
                method: {
                    "falseMatches": [p["reference"] for p in group
                                     if p["label"] == DISTINCT and p["decisions"][method] == MATCH],
                    "missedMatches": [p["reference"] for p in group
                                      if p["label"] == MATCH and p["decisions"][method] != MATCH],
                    "unresolvedGivenDecision": [p["reference"] for p in group
                                                if p["label"] == REVIEW and p["decisions"][method] != REVIEW],
                }
                for method in methods
            }
            for cohort, group in by_cohort["test"].items()
        }
    if hybrid_evidence is not None:
        outcome["hybridConfidenceCounts"] = {
            split: {
                cohort: confidence_counts(group, "hybridEvidence", "hybrid")
                for cohort, group in by_cohort[split].items()
            }
            for split in ("test", "all")
        }
        outcome["hybridDecisionCounts"] = {
            split: {
                cohort: dict(sorted(Counter(p["hybridEvidence"]["decision"] for p in group).items()))
                for cohort, group in groups.items()
            }
            for split, groups in by_cohort.items()
        }
    if conservative:
        outcome["sliceMetrics"] = {
            split: {cohort: {slice_name: subset_metrics([p for p in group if slice_name in p["slices"]])
                            for slice_name in SLICE_NAMES}
                    for cohort, group in groups.items()}
            for split, groups in by_cohort.items()
        }
        outcome["testErrorExamples"] = {
            cohort: {method: [
                {"reference": p["reference"], "expected": p["label"], "decision": p["decisions"][method],
                 "scenario": p["scenario"], "slices": p["slices"], "comparison": p["comparison"]}
                for p in group if p["label"] != p["decisions"][method]
            ] for method in methods}
            for cohort, group in by_cohort["test"].items()
        }
    return outcome


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("dataset", type=Path)
    parser.add_argument("--api", default="http://127.0.0.1:18889/api")
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--hybrid", action="store_true", help="Also evaluate the hybrid rules and report each cohort")
    parser.add_argument("--conservative", action="store_true",
                        help="Also evaluate the conservative GTIN/pack rule and report error slices")
    parser.add_argument("--split", choices=(*SPLITS, "all"), default="all",
                        help="Evaluate one preassigned split. Validate split ownership across the full dataset first.")
    parser.add_argument("--origin", help="Evaluate only cases with this origin after validating all split assignments")
    args = parser.parse_args()
    endpoint = urlsplit(args.api)
    if (endpoint.scheme != "http" or endpoint.hostname not in {"127.0.0.1", "::1", "localhost"}
            or endpoint.username or endpoint.password or endpoint.query or endpoint.fragment
            or endpoint.path.rstrip("/") != "/api"):
        parser.error("Use the loopback address of a disposable CartIQ instance.")
    token = os.environ.get("CARTIQ_ADMIN_TOKEN")
    if not token:
        parser.error("Set CARTIQ_ADMIN_TOKEN for the disposable CartIQ instance.")
    pairs = load_pairs(args.dataset)
    if args.split != "all":
        pairs = [pair for pair in pairs if pair["split"] == args.split]
    if args.origin:
        pairs = [pair for pair in pairs if pair.get("origin") == args.origin]
        if not pairs:
            parser.error("No cases match the requested split and origin.")
    transport = build_opener(ProxyHandler({}), NoRedirect())
    api = args.api.rstrip("/")
    evidence = predictions(transport, api, token, pairs)
    hybrid = predictions(transport, api, token, pairs, hybrid=True) if args.hybrid else None
    outcome = report(pairs, evidence, hybrid, args.conservative)
    args.output.write_text(json.dumps(outcome, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(outcome.get("cohortMetrics", outcome["metrics"]), indent=2))


if __name__ == "__main__":
    main()
