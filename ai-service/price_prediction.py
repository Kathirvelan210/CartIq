"""One-step forecasts for one explicitly scoped, permission-approved series."""
from datetime import datetime, timedelta, timezone
from math import ceil, sqrt
from statistics import mean, median
from typing import Annotated

from pydantic import BaseModel, ConfigDict, Field, model_validator


class Observation(BaseModel):
    model_config = ConfigDict(extra="forbid")
    observedAt: datetime
    recordedAt: datetime
    priceMinor: int = Field(gt=0, le=1_000_000_000, strict=True)

    @model_validator(mode="after")
    def chronology(self):
        if (not self.observedAt.tzinfo or not self.recordedAt.tzinfo
                or self.recordedAt < self.observedAt or self.recordedAt > datetime.now(timezone.utc)):
            raise ValueError("Invalid observation or intake timestamp")
        return self


class Experiment(BaseModel):
    model_config = ConfigDict(extra="forbid")
    points: list[Observation] = Field(max_length=5000)
    datasetVersion: Annotated[str, Field(pattern=r"^[a-f0-9]{64}$")]


def daily(points):
    """Intraday latest available observation. Very late arrivals are not backfilled."""
    rows = {}
    for point in points:
        day = point.observedAt.astimezone(timezone.utc).date()
        if point.recordedAt.astimezone(timezone.utc).date() != day:
            continue
        old = rows.get(day)
        if old is None or point.observedAt > old.observedAt:
            rows[day] = point
    return dict(sorted((day, value.priceMinor) for day, value in rows.items()))


def predict(method, observed):
    if method == "naive":
        return observed[-1]
    if method == "moving-7":
        return mean(observed[-7:])
    # Fixed-alpha single exponential smoothing, fitted to the available history.
    level = observed[0]
    for value in observed[1:]:
        level = 0.3 * value + 0.7 * level
    return level


def scores(actual, predicted):
    errors = [estimate - truth for truth, estimate in zip(actual, predicted)]
    return {"count": len(errors), "actualPriceSumMinor": sum(actual), "maeMinor": mean(abs(error) for error in errors),
            "rmseMinor": sqrt(mean(error ** 2 for error in errors)),
            "wapePercent": 100 * sum(abs(error) for error in errors) / sum(actual),
            "biasMinor": mean(errors)}


def experiment(request):
    today = datetime.now(timezone.utc).date()
    rows = {day: price for day, price in daily(request.points).items() if day < today}
    days = list(rows)
    base = {"observedDays": len(days), "eligibleRows": len(request.points), "datasetVersion": request.datasetVersion}
    if len(days) < 60:
        return base | {"status": "INSUFFICIENT_DATA", "warning": "Requires at least 60 same-day-ingested UTC price days."}
    split_validation = days[int(len(days) * .6)]
    split_test = days[int(len(days) * .8)]
    methods = ("naive", "moving-7", "exponential-0.3")
    predictions = {key: {method: [] for method in methods} for key in ("validation", "test")}
    actual = {key: [] for key in predictions}
    past = []
    for index, day in enumerate(days):
        if day >= split_validation and days[index - 1] + timedelta(days=1) == day:
            split = "test" if day >= split_test else "validation"
            actual[split].append(rows[day])
            for method in methods:
                predictions[split][method].append(predict(method, past))
        past.append(rows[day])
    if min(map(len, actual.values())) < 10:
        return base | {"status": "INSUFFICIENT_DATA", "warning": "Each holdout requires at least 10 consecutive-day targets."}
    metrics = {split: {method: scores(actual[split], predictions[split][method]) for method in methods} for split in predictions}
    selected = min(methods, key=lambda method: metrics["validation"][method]["maeMinor"])
    errors = sorted(abs(truth - estimated) for truth, estimated in zip(actual["validation"], predictions["validation"][selected]))
    width = errors[min(len(errors)-1, ceil(.9 * (len(errors)+1))-1)]
    coverage = mean(abs(truth-estimated) <= width for truth, estimated in zip(actual["test"], predictions["test"][selected]))
    changes = [abs(after-before) for before,after in zip(past,past[1:])]
    train_changes = changes[:max(1,days.index(split_validation)-1)]
    change_limit = max(1, median(train_changes)) * 8
    outliers = sum(change > change_limit for change in changes)
    result = base | {"status": "TRAINED", "method": selected, "modelVersion": "rolling-price-v1-"+selected,
                     "trainedAt": datetime.now(timezone.utc), "validationStart": split_validation, "testStart": split_test,
                     "metrics": metrics, "empiricalTestCoverage": coverage, "investigateLargeChanges": outliers,
                     "intervalEligible": coverage >= .8 and metrics["test"][selected]["maeMinor"] <= metrics["test"]["naive"]["maeMinor"]}
    if days[-1] < today - timedelta(days=1):
        return result | {"status": "STALE", "warning": "The final eligible UTC day is stale."}
    if not result["intervalEligible"]:
        return result | {"status": "HOLDOUT_REJECTED", "warning": "The selected model did not meet the held-out coverage or baseline gate."}
    estimate = predict(selected, past)
    return result | {"estimateMinor": max(1, round(estimate)), "lowMinor": max(1, int(estimate-width)),
                     "highMinor": ceil(estimate+width), "forecastDate": days[-1]+timedelta(days=1),
                     "warning": "Empirical interval from validation residuals. Coverage is measured on the test window."}


def summarize_experiments(reports):
    """Combine authorized per-series admin reports by category, retailer and area."""
    groups = {}
    scopes = set()
    for report in reports:
        scope = report["scope"]
        key = (scope["variant"], scope["retailer"], scope["location"])
        if key in scopes:
            raise ValueError("Use at most one experiment per series")
        scopes.add(key)
        for method, metric in report["evaluation"].get("metrics", {}).get("test", {}).items():
            count = metric["count"]
            for dimension, value in (("category", report["category"]), ("retailer", scope["retailer"]), ("location", scope["location"])):
                sums = groups.setdefault((dimension, value, method), [0,0,0.,0.,0.])
                for index, number in enumerate((count, metric["actualPriceSumMinor"], count*metric["maeMinor"],
                                                count*metric["rmseMinor"]**2, count*metric["biasMinor"])):
                    sums[index] += number
    return [{"dimension": dimension, "segment": value, "model": model, "testCount": count,
             "maeMinor": absolute/count, "rmseMinor": sqrt(squared/count), "wapePercent": 100*absolute/actual,
             "biasMinor": signed/count}
            for (dimension, value, model), (count,actual,absolute,squared,signed) in sorted(groups.items()) if count and actual]


if __name__ == "__main__":
    import json
    import sys
    print(json.dumps(summarize_experiments(json.load(sys.stdin)), indent=2))
