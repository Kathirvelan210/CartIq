from datetime import datetime, time, timedelta, timezone

import pytest
from fastapi.testclient import TestClient

from app import app
from price_prediction import Experiment, Observation, daily, experiment, scores, summarize_experiments


def make_points(count=75, spacing=1, late=False):
    today = datetime.now(timezone.utc).date() - timedelta(days=int(late))
    return [Observation(observedAt=datetime.combine(today-timedelta(days=(count-i)*spacing), time(11), timezone.utc),
                        recordedAt=datetime.combine(today-timedelta(days=(count-i)*spacing-int(late)), time(13), timezone.utc),
                        priceMinor=4200) for i in range(count)]


def run(points):
    return experiment(Experiment(points=points,datasetVersion="a"*64))


def test_stable_synthetic_series_runs_all_methods_and_returns_interval():
    result = run(make_points())
    assert result["status"] == "TRAINED"
    assert result["method"] == "naive"
    assert set(result["metrics"]["test"]) == {"naive", "moving-7", "exponential-0.3"}
    assert result["metrics"]["test"]["naive"]["maeMinor"] == 0
    assert result["lowMinor"] == result["highMinor"] == 4200
    assert result["validationStart"] < result["testStart"] < result["forecastDate"]


def test_empty_small_discontinuous_or_late_series_do_not_fill_gaps():
    for points in ([], make_points(40), make_points(spacing=2), make_points(74,late=True)):
        result = run(points)
        assert result["status"] == "INSUFFICIENT_DATA" and "estimateMinor" not in result


def test_chooses_latest_same_day_capture_and_rejects_late_backfill():
    points = make_points(3)
    first = points[0]
    later = first.model_copy(update={"observedAt":first.observedAt+timedelta(hours=1),"priceMinor":3900})
    backfill = first.model_copy(update={"recordedAt":first.recordedAt+timedelta(days=1),"priceMinor":5000})
    assert daily([later,backfill,*points])[first.observedAt.date()] == 3900


def test_test_prices_cannot_change_method_selection():
    points = make_points()
    changed = [point.model_copy(update={"priceMinor": 4200+(index%10)*199}) if index>=60 else point for index,point in enumerate(points)]
    result=run(changed)
    assert result["method"] == run(points)["method"]
    assert result["status"] == "HOLDOUT_REJECTED" and "estimateMinor" not in result


def test_metrics_have_different_error_meanings():
    result=scores([100,200],[120,180])
    assert result["maeMinor"] == result["rmseMinor"] == 20
    assert result["biasMinor"] == 0 and result["wapePercent"] == pytest.approx(100*40/300)


def test_internal_route_requires_secret_and_returns_explicit_insufficiency(monkeypatch):
    monkeypatch.setenv("AI_SERVICE_SECRET","synthetic-local-fixture-secret")
    payload={"points":[],"datasetVersion":"a"*64}
    client=TestClient(app)
    assert client.post("/price-intelligence/experiment",json=payload).status_code == 401
    response=client.post("/price-intelligence/experiment",json=payload,headers={"X-Service-Token":"synthetic-local-fixture-secret"})
    assert response.status_code == 200 and response.json()["status"] == "INSUFFICIENT_DATA"


def test_groups_held_out_metrics_with_observation_weighted_error():
    rows=[{"scope":{"variant":str(i),"retailer":"blinkit","location":str(i)},"category":"dairy",
           "evaluation":{"metrics":{"test":{"naive":scores(actual,predicted)}}}}
          for i,(actual,predicted) in enumerate((([100],[120]),([200,300],[180,330])))]
    dairy=next(row for row in summarize_experiments(rows) if row["dimension"]=="category")
    assert dairy["testCount"]==3 and dairy["maeMinor"]==pytest.approx(70/3) and dairy["wapePercent"]==pytest.approx(100*70/600)
    with pytest.raises(ValueError):
        summarize_experiments(rows+rows)
