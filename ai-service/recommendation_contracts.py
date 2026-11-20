"""Offline recommendation dataset contracts. Reporting only; no ingestion or training."""

from collections import Counter
from dataclasses import dataclass
from datetime import date
import hashlib
import json
from pathlib import Path
import re
from types import MappingProxyType


@dataclass(frozen=True)
class Contract:
    features: tuple[str, ...]
    labels: frozenset[str]
    chronological: bool


CONTRACTS = MappingProxyType({
    "product_matching": Contract(("brand", "title", "gtin", "pack", "flavour", "formulation"),
                                 frozenset({"same_variant", "distinct", "unresolved"}), False),
    "recipe_recommendation": Contract(("explicit_diet", "cuisine", "servings", "pantry_coverage", "recipe_id"),
                                      frozenset({"chosen", "rejected", "not_relevant", "unresolved"}), True),
    "bulk_buy": Contract(("pack", "unit_price_minor", "stock_packs", "daily_use", "household_size", "pantry",
                          "storage", "use_by_days", "budget_minor", "retailer", "location"),
                         frozenset({"normal", "larger", "multiple", "no_extra", "insufficient"}), True),
    "personalized": Contract(("saved_pack", "selected_before", "explicit_diet", "pantry_gap", "budget_minor",
                              "price_minor", "stock_packs", "retailer", "location"),
                             frozenset({"accepted", "rejected", "modified", "not_relevant", "unresolved"}), True),
})
_SPLITS = ("train", "validation", "test")
_DIGEST = re.compile(r"[0-9a-f]{64}")


def validate_manifest(manifest: dict, as_of: date) -> dict:
    """Validate reviewed, de-identified rows and report coverage without returning row data."""
    contract = CONTRACTS[manifest["task"]]
    windows = manifest.get("cuts")
    if contract.chronological:
        if not isinstance(windows, list) or len(windows) != 2:
            raise ValueError("Chronological evaluation needs two predeclared cut dates")
        validation, test = map(date.fromisoformat, windows)
        if not validation < test <= as_of:
            raise ValueError("Invalid chronological cuts")
    version = manifest["version"]
    if manifest["schema"] != 1 or not isinstance(version, str) or not re.fullmatch(r"[A-Za-z0-9._-]{1,60}", version):
        raise ValueError("Unsupported dataset schema or version")
    all_ids, observed_splits = set(), {}
    counts = {split: Counter() for split in _SPLITS}

    for row in manifest["rows"]:
        split = row["split"]
        observed = date.fromisoformat(row["observed"])
        if split not in _SPLITS or observed > as_of or row["label"] not in contract.labels:
            raise ValueError("Invalid split, label or observation date")
        if contract.chronological:
            expected = "train" if observed < validation else "validation" if observed < test else "test"
            if split != expected:
                raise ValueError("Observation is outside its chronological split")
        permission = row["permission"]
        if (row.get("genuine") is not True or row.get("permission_reviewed") is not True
                or permission.get("revoked") is not False
                or permission.get("training") is not True or permission.get("evaluation") is not True
                or not permission.get("reference") or date.fromisoformat(permission["expires"]) < as_of):
            raise ValueError("A current reviewed training and evaluation grant is required")
        if not row.get("evidence_reference") or set(row["features"]) != set(contract.features):
            raise ValueError("Evidence and every feature key are required; unknown feature values use null")
        identity = row["row_digest"]
        if not _digest(identity) or identity in all_ids:
            raise ValueError("Missing or duplicate immutable row digest")
        all_ids.add(identity)
        groups = row["groups"]
        required = {"source_record", "product_family"}
        if manifest["task"] != "product_matching":
            required.add("account_cohort")
        if set(groups) != required or any(not values or not all(_digest(v) for v in values) for values in groups.values()):
            raise ValueError("Missing de-identified source, family or account grouping keys")
        for kind, keys in groups.items():
            for key in keys:
                if observed_splits.setdefault((kind, key), split) != split:
                    raise ValueError("Leakage across split boundaries")
        counts[split][row["label"]] += 1

    serialized = json.dumps(manifest, sort_keys=True, separators=(",", ":"), ensure_ascii=False, allow_nan=False)
    return {"task": manifest["task"], "version": version, "sha256": hashlib.sha256(serialized.encode()).hexdigest(),
            "counts": counts, "status": "COVERAGE_REPORTED" if all(counts.values()) else "INSUFFICIENT_COVERAGE"}


def _digest(value):
    return isinstance(value, str) and _DIGEST.fullmatch(value) is not None


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("manifest", type=Path)
    parser.add_argument("--as-of", type=date.fromisoformat, required=True)
    args = parser.parse_args()
    result = validate_manifest(json.loads(args.manifest.read_text(encoding="utf-8")), args.as_of)
    print(json.dumps(result, sort_keys=True))
