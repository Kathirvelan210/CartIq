from copy import deepcopy
from datetime import date
import hashlib
import pytest

from recommendation_contracts import CONTRACTS, validate_manifest


def digest(value):
    return hashlib.sha256(value.encode()).hexdigest()


def fixture_manifest(task="bulk_buy"):
    # Synthetic metadata tests contract behavior only. No model or evaluation scores.
    return {"schema": 1, "task": task, "version": "unit-test", "cuts": ["2026-02-01", "2026-03-01"],
            "rows": [{"row_digest": digest(split), "split": split, "observed": t, "genuine": True,
                      "permission_reviewed": True, "permission": {"training": True, "evaluation": True, "expires": "2026-12-31",
                                                                   "revoked": False, "reference": "fixture-only"},
                      "evidence_reference": "fixture-only", "label": "normal" if task == "bulk_buy" else "same_variant",
                      "groups": {kind: [digest(split + kind)] for kind in
                                 (["source_record", "product_family", "account_cohort"] if task == "bulk_buy" else
                                  ["source_record", "product_family"])},
                      "features": dict.fromkeys(CONTRACTS[task].features)}
                     for split, t in zip(("train", "validation", "test"), ("2026-01-01", "2026-02-01", "2026-03-01"))]}


def test_manifest_reports_coverage_and_reproducible_digest_without_row_data():
    manifest = fixture_manifest()
    output = validate_manifest(manifest, date(2026, 4, 1))
    assert output == validate_manifest(deepcopy(manifest), date(2026, 4, 1))
    assert output["counts"]["test"]["normal"] == 1
    assert "fixture-only" not in str(output)
    assert validate_manifest({**manifest, "rows": []}, date(2026, 4, 1))["status"] == "INSUFFICIENT_COVERAGE"


def test_manifest_rejects_synthetic_revoked_or_unlicensed_rows():
    for key, value in (("genuine", False), ("permission_reviewed", False)):
        manifest = fixture_manifest()
        manifest["rows"][0][key] = value
        with pytest.raises(ValueError, match="grant"):
            validate_manifest(manifest, date(2026, 4, 1))
    manifest = fixture_manifest()
    manifest["rows"][0]["permission"]["revoked"] = True
    with pytest.raises(ValueError, match="grant"):
        validate_manifest(manifest, date(2026, 4, 1))


def test_manifest_blocks_cross_split_family_source_or_owner_and_incorrect_dates():
    for field in ("source_record", "product_family", "account_cohort"):
        manifest = fixture_manifest()
        manifest["rows"][2]["groups"][field] = manifest["rows"][0]["groups"][field]
        with pytest.raises(ValueError, match="Leakage"):
            validate_manifest(manifest, date(2026, 4, 1))
    manifest = fixture_manifest()
    manifest["rows"][0]["observed"] = "2026-03-01"
    with pytest.raises(ValueError, match="chronological split"):
        validate_manifest(manifest, date(2026, 4, 1))


def test_product_matching_uses_evidence_grouping_for_both_records_not_chronological_dates():
    manifest = fixture_manifest("product_matching")
    del manifest["cuts"]
    manifest["rows"][2]["groups"]["source_record"].append(manifest["rows"][1]["groups"]["source_record"][0])
    with pytest.raises(ValueError, match="Leakage"):
        validate_manifest(manifest, date(2026, 4, 1))
