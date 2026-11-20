"""Internal forecasting service. It accepts observations; it never invents live prices."""
from __future__ import annotations
import os
import secrets
from contextlib import asynccontextmanager
from datetime import datetime, timezone
from typing import Annotated
import numpy as np
import pandas as pd
from fastapi import FastAPI, Header, HTTPException, Response
from pydantic import BaseModel, Field, ConfigDict, field_validator
from sklearn.linear_model import LinearRegression
from training import ModelRegistry
from recipe_generation import GenerateRequest, RecipeDraft, WeekRequest, WeekChoices, ShoppingQuestion, ShoppingRoute, generate, generation_readiness
from price_prediction import Experiment, experiment

registry = ModelRegistry()

@asynccontextmanager
async def lifespan(_app):
    if os.environ.get("CARTIQ_ENV") == "production" and len(os.environ.get("AI_SERVICE_SECRET", "").encode()) < 32:
        raise RuntimeError("AI_SERVICE_SECRET must be at least 32 bytes in production")
    yield

production = os.environ.get("CARTIQ_ENV") == "production"
app = FastAPI(title="CartIQ forecasting", version="0.2.0", lifespan=lifespan,
              docs_url=None if production else "/docs", redoc_url=None if production else "/redoc",
              openapi_url=None if production else "/openapi.json")

class PricePoint(BaseModel):
    model_config = ConfigDict(extra="forbid")
    observedAt: datetime
    priceMinor: int = Field(gt=0, le=1_000_000_000)

    @field_validator("observedAt")
    @classmethod
    def valid_time(cls, value):
        if value.tzinfo is None or value > datetime.now(timezone.utc):
            raise ValueError("Observation must be timezone-aware and not in the future")
        return value

class ForecastRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    points: list[PricePoint] = Field(min_length=8, max_length=1000)
    productId: str | None = Field(default=None,max_length=60)
    platform: str = Field(default="best",max_length=20)
    locationId: str | None = Field(default=None,max_length=60)
    sample: bool = True

def forecast(points: list[PricePoint]) -> dict:
    frame = pd.DataFrame({"day": [p.observedAt.date() for p in points], "price": [p.priceMinor for p in points]})
    # The caller supplies one canonical variant/location. Compare the daily lowest reading.
    daily = frame.groupby("day")["price"].min().sort_index()
    if len(daily) < 8:
        raise ValueError("At least eight distinct days are needed")
    days = np.array([(day - daily.index[0]).days for day in daily.index], dtype=float)
    values = daily.to_numpy(dtype=float)
    holdout = max(3, len(values) // 5)
    split = len(values) - holdout
    model = LinearRegression().fit(days[:split, None], values[:split])
    predicted = model.predict(days[split:, None])
    linear_error = float(np.mean(np.abs(values[split:] - predicted)))
    baseline_predictions = np.array([np.mean(values[max(0, i - 7):i]) for i in range(split, len(values))])
    baseline_error = float(np.mean(np.abs(values[split:] - baseline_predictions)))
    if linear_error <= baseline_error:
        model.fit(days[:, None], values)
        estimate = float(model.predict([[days[-1] + 1]])[0])
        errors = np.abs(values[split:] - predicted)
        method = "Linear regression; chronological holdout validation"
        error = linear_error
    else:
        estimate = float(np.mean(values[-7:]))
        errors = np.abs(values[split:] - baseline_predictions)
        method = "Seven-day mean; outperformed linear regression on holdout"
        error = baseline_error
    # Reject uncontrolled extrapolation from a short series.
    estimate = float(np.clip(estimate, values.min() * .8, values.max() * 1.2))
    width = max(float(np.quantile(errors, .9)), estimate * .03)
    quality = max(0., min(.95, 1 - error / max(1., float(np.mean(values))))) * min(1., len(values) / 30)
    return {"estimateMinor": max(1, round(estimate)), "lowMinor": max(1, round(estimate - width)),
            "highMinor": max(1, round(estimate + width)), "confidence": round(quality, 3), "method": method,
            "caveat": "Experimental next-day estimate. Confidence is a historical fit quality score, not a calibrated probability. The range is empirical; future prices may fall outside it. A price drop is never guaranteed."}

@app.get("/health")
def health():
    return {"status": "ok", "mode": "experimental", "livePriceProvider": False}

@app.post("/forecast")
def predict(request: ForecastRequest, x_service_token: Annotated[str | None, Header()] = None):
    authorize(x_service_token)
    try:
        if request.productId and request.locationId:
            trained = registry.predict(request.points,request.productId,request.platform,request.locationId,request.sample)
            if trained:
                return trained
        return forecast(request.points)
    except ValueError as error:
        raise HTTPException(status_code=422, detail=str(error)) from error

def authorize(token):
    required=os.environ.get("AI_SERVICE_SECRET","")
    if required and not secrets.compare_digest(required.encode(), (token or "").encode()):
        raise HTTPException(status_code=401,detail="Invalid internal service credential")

@app.post("/price-intelligence/experiment")
def price_experiment(request: Experiment, x_service_token: Annotated[str | None, Header()] = None):
    if not os.getenv("AI_SERVICE_SECRET"):
        raise HTTPException(503, "Price experiment authentication is not configured")
    authorize(x_service_token)
    return experiment(request)

class RecipeQuery(BaseModel):
    model_config = ConfigDict(extra="forbid")
    ingredients: list[str] = Field(max_length=100)
    sample: bool = True

class IngredientQuery(BaseModel):
    model_config = ConfigDict(extra="forbid")
    query: str = Field(min_length=1,max_length=150)
    sample: bool = True

@app.get("/models/status")
def model_status(x_service_token: Annotated[str | None, Header()] = None):
    authorize(x_service_token)
    return registry.status()

@app.post("/recipes/recommend")
def recipe_recommendations(request: RecipeQuery,x_service_token: Annotated[str | None, Header()] = None):
    authorize(x_service_token)
    return registry.recommend(request.ingredients,request.sample)

@app.post("/ingredients/match")
def ingredient_matches(request: IngredientQuery,x_service_token: Annotated[str | None, Header()] = None):
    authorize(x_service_token)
    return registry.match(request.query,request.sample)

@app.get("/recipes/readiness")
def recipe_readiness(x_service_token: Annotated[str | None, Header()] = None):
    if not os.getenv("AI_SERVICE_SECRET"):
        raise HTTPException(503, "Private service authentication is not configured")
    authorize(x_service_token)
    return generation_readiness()


@app.post("/recipes/generate", response_model=RecipeDraft)
def generate_recipe(request: GenerateRequest, response: Response, x_service_token: Annotated[str | None, Header()] = None,
                    x_request_id: Annotated[str | None, Header()] = None):
    if not os.getenv("AI_SERVICE_SECRET"):
        raise HTTPException(503, "Recipe generation service authentication is not configured")
    authorize(x_service_token)
    return generate(request, x_request_id, response_headers=response.headers)



@app.post("/recipes/week", response_model=WeekChoices)
def plan_week(request: WeekRequest, x_service_token: Annotated[str | None, Header()] = None,
              x_request_id: Annotated[str | None, Header()] = None):
    if not os.getenv("AI_SERVICE_SECRET"):
        raise HTTPException(503, "Meal planning service authentication is not configured")
    authorize(x_service_token)
    return generate(request, x_request_id)


@app.post("/shopping/intent", response_model=ShoppingRoute)
def shopping_intent(request: ShoppingQuestion, x_service_token: Annotated[str | None, Header()] = None,
                    x_request_id: Annotated[str | None, Header()] = None):
    if not os.getenv("AI_SERVICE_SECRET"):
        raise HTTPException(503, "Shopping assistant authentication is not configured")
    authorize(x_service_token)
    return generate(request, x_request_id)
