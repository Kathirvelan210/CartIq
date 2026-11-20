from datetime import datetime, timezone, timedelta
from fastapi.testclient import TestClient
from app import app

client = TestClient(app)

def series(days=30):
    now = datetime.now(timezone.utc) - timedelta(minutes=1)
    return [{"observedAt": (now-timedelta(days=days-i)).isoformat(), "priceMinor": 10000+i*5} for i in range(days)]

def test_forecast_uses_history_and_exposes_uncertainty():
    response = client.post("/forecast", json={"points": series()})
    assert response.status_code == 200
    data = response.json()
    assert data["lowMinor"] <= data["estimateMinor"] <= data["highMinor"]
    assert 0 <= data["confidence"] <= .95
    assert "not a calibrated probability" in data["caveat"]

def test_duplicate_days_do_not_create_false_evidence():
    response = client.post("/forecast", json={"points": [series()[0]]*20})
    assert response.status_code == 422

def test_invalid_prices_and_future_times_rejected():
    data = series()
    data[0]["priceMinor"] = -10
    assert client.post("/forecast", json={"points": data}).status_code == 422
    data = series()
    data[0]["observedAt"] = (datetime.now(timezone.utc)+timedelta(days=1)).isoformat()
    assert client.post("/forecast", json={"points": data}).status_code == 422

def test_internal_auth(monkeypatch):
    monkeypatch.setenv("AI_SERVICE_SECRET", "test-shared-secret")
    assert client.post("/forecast", json={"points": series()}).status_code == 401
    assert client.post("/forecast", headers={"X-Service-Token":"test-shared-secret"}, json={"points":series()}).status_code == 200

def test_production_lifespan_requires_service_secret(monkeypatch):
    import pytest
    monkeypatch.setenv("CARTIQ_ENV", "production")
    monkeypatch.delenv("AI_SERVICE_SECRET", raising=False)
    with pytest.raises(RuntimeError, match="AI_SERVICE_SECRET"):
        with TestClient(app):
            pass
    monkeypatch.setenv("AI_SERVICE_SECRET", "fixture-service-secret-at-least-32-bytes")
    with TestClient(app) as service:
        assert service.get("/health").status_code == 200
        assert service.get("/models/status").status_code == 401

