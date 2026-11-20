import json
import logging
import socket
import ssl

import httpx
import pytest
from fastapi.testclient import TestClient
from pydantic import ValidationError

from app import app
import recipe_generation as generation

FIXTURE = {"name": "Fixture upma", "description": "Semolina and onion", "cuisine": "South Indian",
           "meal": "BREAKFAST", "diet": "VEGETARIAN", "servings": 4, "prepMinutes": 5, "cookMinutes": 15,
           "difficulty": "EASY", "ingredients": [{"name": "Semolina", "quantity": 0.2, "unit": "kg"}],
           "steps": ["Toast semolina."], "substitutions": []}


@pytest.fixture
def configured(monkeypatch):
    monkeypatch.setenv("AI_SERVICE_SECRET", "fixture-internal-credential")
    monkeypatch.setenv("CARTIQ_RECIPE_GENERATION_ENABLED", "true")
    monkeypatch.setenv("GEMINI_MODEL", "gemini-3.5-flash")
    monkeypatch.setenv("GEMINI_API_KEY", "fixture-key-only")
    return {"x-service-token": "fixture-internal-credential"}


def test_auth_and_structured_generation(configured, monkeypatch):
    calls = []
    def fake(key, model, request, trace):
        calls.append((key, model, request.pantry))
        return json.dumps(FIXTURE)
    monkeypatch.setattr(generation, "_model_request", fake)
    client = TestClient(app)
    body = {"prompt": "Suggest an Indian breakfast", "pantry": ["Semolina"]}
    assert client.post("/recipes/generate", json=body).status_code == 401
    response = client.post("/recipes/generate", json=body, headers=configured)
    assert response.status_code == 200 and response.json() == FIXTURE
    assert len(calls) == 1 and calls[0][2] == ["Semolina"]


@pytest.mark.parametrize("bad", [
    {"nutrition": {"protein": 30}}, {"description": "30 g of protein"}, {"description": "Costs ₹50"},
    {"description": "A high-protein dinner."},
    {"ingredients": [{"name": "oil", "quantity": 0, "unit": "ml"}]},
    {"ingredients": [{"name": "oil", "quantity": "1", "unit": "cup"}]}, {"servings": 21}, {"steps": []},
])
def test_rejects_unsupported_recipe_data(bad):
    with pytest.raises(ValidationError):
        generation.RecipeDraft.model_validate(FIXTURE | bad)


def test_disabled_and_timeout_do_not_retry(configured, monkeypatch):
    calls = []
    def timeout(*args):
        calls.append(1)
        raise httpx.ReadTimeout("fixture-only")
    monkeypatch.setattr(generation, "_model_request", timeout)
    client = TestClient(app)
    args = {"json": {"prompt": "Suggest dinner"}, "headers": configured}
    monkeypatch.setenv("CARTIQ_RECIPE_GENERATION_ENABLED", "false")
    assert client.post("/recipes/generate", **args).status_code == 503
    assert calls == []
    monkeypatch.setenv("CARTIQ_RECIPE_GENERATION_ENABLED", "true")
    response = client.post("/recipes/generate", **args)
    assert response.status_code == 504 and "fixture-only" not in response.text and calls == [1]


def test_week_limits_model_to_supplied_curated_recipes(configured, monkeypatch):
    client = TestClient(app)
    data = {"prompt": "Vegetarian breakfasts", "choices": {"id1": "Fixture upma"}}
    calls = []
    def respond(*args, **kwargs):
        calls.append(1)
        return json.dumps({"recipeIds": ["id1" if len(calls) == 1 else "unknown"] * 7})
    monkeypatch.setattr(generation, "_model_request", respond)
    assert client.post("/recipes/week", json=data).status_code == 401
    first = client.post("/recipes/week", json=data, headers=configured)
    assert first.status_code == 200 and first.json()["recipeIds"] == ["id1"] * 7
    assert client.post("/recipes/week", json=data, headers=configured).status_code == 502
    assert len(calls) == 2


def test_shopping_assistant_can_only_choose_a_cartiq_tool(configured, monkeypatch):
    client = TestClient(app)
    data = {"prompt": "Which retailer can supply my entire shopping list?"}
    monkeypatch.setattr(generation, "_model_request", lambda *a, **kw: '{"intent":"BASKET"}')
    assert client.post("/shopping/intent", json=data).status_code == 401
    assert client.post("/shopping/intent", json=data, headers=configured).json() == {"intent": "BASKET"}
    monkeypatch.setattr(generation, "_model_request", lambda *a, **kw: '{"intent":"BASKET","price":500}')
    assert client.post("/shopping/intent", json=data, headers=configured).status_code == 502


def test_non_flash_configuration_fails_before_dispatch(configured, monkeypatch):
    monkeypatch.setenv("GEMINI_MODEL", "gemini-unverified-pro")
    monkeypatch.setattr(generation, "_model_request", lambda *args: pytest.fail("unexpected dispatch"))
    assert TestClient(app).post("/recipes/generate", json={"prompt": "Make lunch"}, headers=configured).status_code == 503


def test_readiness_is_private_and_never_dispatches(configured, monkeypatch):
    monkeypatch.setattr(generation, "_model_request", lambda *a, **kw: pytest.fail("unexpected dispatch"))
    with TestClient(app) as client:
        assert client.get("/recipes/readiness").status_code == 401
        ready = client.get("/recipes/readiness", headers=configured).json()
        assert ready == {"model": "gemini-3.5-flash", "geminiCredential": "PRESENT",
                         "serviceCredential": "PRESENT", "generationEnabled": True, "ready": True}
        monkeypatch.delenv("GEMINI_API_KEY")
        assert not client.get("/recipes/readiness", headers=configured).json()["ready"]
        monkeypatch.delenv("AI_SERVICE_SECRET")
        assert client.get("/recipes/readiness").status_code == 503


@pytest.mark.parametrize("statement", ["This pack is in stock.", "Prices will fall tomorrow.", "Available for delivery today."])
def test_substitutions_reject_shopping_claims(statement):
    with pytest.raises(ValidationError):
        generation.RecipeDraft.model_validate(FIXTURE | {"substitutions": [statement]})


@pytest.mark.parametrize("upstream,status", [({"candidates": ["invalid"]}, 502),
    ({"candidates": [{"finishReason": "STOP", "content": {"parts": [None]}}]}, 502),
    ({"candidates": [{"finishReason": "STOP", "content": {"parts": [{"text": json.dumps(FIXTURE |
        {"substitutions": ["Use rice flakes instead of semolina."]})}]}}]}, 200)])
def test_local_transport_validates_provider_envelope(configured, monkeypatch, upstream, status):
    monkeypatch.setenv("GEMINI_MODEL", "gemini-3.8-flash")
    real_client = httpx.Client
    calls = []
    def respond(req):
        calls.append(req.url.path)
        assert req.url.path == "/v1beta/models/gemini-3.8-flash:generateContent"
        config = json.loads(req.content)["generationConfig"]
        assert "candidateCount" not in config
        assert config["maxOutputTokens"] == 2800
        assert config["responseMimeType"] == "application/json"
        assert "ingredients" in config["responseJsonSchema"]["properties"]
        assert req.headers["x-goog-api-key"] == "fixture-key-only"
        return httpx.Response(200, json=upstream)
    with TestClient(app) as client:
        monkeypatch.setattr(generation.httpx, "Client", lambda **kw: real_client(transport=httpx.MockTransport(respond), **kw))
        response = client.post("/recipes/generate", json={"prompt": "Suggest dinner"}, headers=configured)
        assert response.status_code == status
        assert len(calls) == 1
        if status == 200:
            assert response.json()["substitutions"] == ["Use rice flakes instead of semolina."]


@pytest.mark.parametrize("upstream,category,public", [
    (401,"GEMINI_AUTH",503), (403,"GEMINI_AUTH",503), (404,"GEMINI_MODEL_OR_ENDPOINT",503),
    (400,"GEMINI_INVALID_REQUEST",502), ("bad-key","GEMINI_AUTH",503), (429,"GEMINI_QUOTA",429),
    (500,"GEMINI_SERVER",502), (503,"GEMINI_SERVER",502), (302,"GEMINI_HTTP",502),
    ("timeout","GEMINI_TIMEOUT",504), ("network","GEMINI_CONNECTION",502),
    ("dns","GEMINI_DNS",502), ("tls","GEMINI_TLS",502), ("bad-output","GEMINI_INVALID_OUTPUT",502),
    ("success","SUCCESS",200)])
def test_provider_diagnostics_are_correlated_private_and_single_attempt(configured, monkeypatch, caplog, upstream, category, public):
    correlation = "fa38d0bf-a7fd-496a-ad42-773941a22b2c"
    real_client = httpx.Client
    calls = []
    status = upstream if isinstance(upstream, int) else {"bad-key":400,"bad-output":200,"success":200}.get(upstream)
    def respond(req):
        calls.append(req.url.path)
        assert req.url.path == "/v1beta/models/gemini-3.5-flash:generateContent" and not req.url.query
        assert req.headers["x-goog-api-key"] == "fixture-key-only"
        body = json.loads(req.content)
        config = body["generationConfig"]
        assert config["maxOutputTokens"] == 2800 and "candidateCount" not in config
        assert config["responseMimeType"] == "application/json" and "ingredients" in config["responseJsonSchema"]["properties"]
        assert json.loads(body["contents"][0]["parts"][0]["text"])["pantry"] == []
        if upstream == "timeout":
            raise httpx.ReadTimeout("private-text")
        if upstream in ("dns","tls","network"):
            cause = {"dns":socket.gaierror(), "tls":ssl.SSLError(), "network":OSError()}[upstream]
            raise httpx.ConnectError("private-text") from cause
        data = {"error":{"message":"private-text","details":[{"reason":"API_KEY_INVALID" if upstream=="bad-key" else "OTHER"}]}}
        if status == 200:
            data = {"responseId":"response-123","usageMetadata":{"totalTokenCount":123},"candidates":[
                {"finishReason":"STOP","content":{"parts":[{"text":json.dumps(FIXTURE) if upstream=="success" else "private-text"}]}}]}
        return httpx.Response(status, json=data, headers={"x-goog-request-id":"provider-123"})
    with TestClient(app) as client, caplog.at_level(logging.INFO, logger="uvicorn.error"):
        monkeypatch.setattr(generation.httpx,"Client",lambda **kw: real_client(transport=httpx.MockTransport(respond),**kw))
        result = client.post("/recipes/generate",json={"prompt":"private-text"},headers=configured | {"X-Request-Id":correlation})
    assert len(calls) == 1 and result.status_code == public
    if category != "SUCCESS":
        assert result.headers["x-cartiq-ai-error"] == category
        assert result.headers.get("x-gemini-http-status") == (str(status) if status else None)
        assert result.headers.get("x-gemini-request-id") == ("provider-123" if status else None)
    else:
        assert result.json() == FIXTURE
    log = " ".join(record.getMessage() for record in caplog.records if record.name=="uvicorn.error")
    assert correlation in log and "outcome="+category in log
    if status == 200:
        assert "responseId=response-123" in log and "totalTokens=123" in log
    for private in ("fixture-key-only","fixture-internal-credential","private-text"):
        assert private not in log and private not in result.text

def test_slow_recipe_completes_once_over_loopback(configured, monkeypatch):
    import threading
    import time
    from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

    received = []
    class Provider(BaseHTTPRequestHandler):
        def log_message(self, *args):
            pass

        def do_POST(self):
            received.append(self.path)
            self.rfile.read(int(self.headers['Content-Length']))
            time.sleep(11)  # Longer than the old ten-second read timeout.
            body = json.dumps({'candidates': [{'finishReason': 'STOP', 'content': {
                'parts': [{'text': json.dumps(FIXTURE)}]}}]}).encode()
            self.send_response(200)
            self.send_header('Content-Length', str(len(body)))
            self.end_headers()
            self.wfile.write(body)

    with ThreadingHTTPServer(('127.0.0.1', 0), Provider) as upstream:
        worker = threading.Thread(target=upstream.serve_forever, daemon=True)
        worker.start()
        class Transport(httpx.HTTPTransport):
            def handle_request(self, req):
                local = httpx.Request('POST', f'http://127.0.0.1:{upstream.server_port}/fixture',
                                      content=req.content, extensions=req.extensions)
                return super().handle_request(local)
        real_client = httpx.Client
        try:
            with TestClient(app) as client:
                monkeypatch.setattr(generation.httpx, 'Client', lambda **kw: real_client(transport=Transport(), **kw))
                response = client.post('/recipes/generate', json={'prompt':'Slow recipe fixture'}, headers=configured)
                assert response.status_code == 200 and response.json() == FIXTURE
                assert received == ['/fixture']
        finally:
            upstream.shutdown()
            worker.join()
