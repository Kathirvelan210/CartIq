import json
from datetime import datetime,timezone,timedelta
from training import train,ModelRegistry
from app import PricePoint

def dataset():
    now=datetime.now(timezone.utc)-timedelta(minutes=1)
    return {"schemaVersion":1,"sample":True,"source":"SYNTHETIC_TEST_FIXTURE","prices":[{"productId":"milk","platform":"blinkit","locationId":"test-pin","priceMinor":6800+day*2,"observedAt":(now-timedelta(days=40-day)).isoformat()} for day in range(40)],"products":[{"id":"milk","name":"Toned Milk","brand":"Test"},{"id":"oats","name":"Rolled Oats","brand":"Test"}],"recipes":[{"id":"oats","name":"Overnight Oats","ingredients":[{"productId":"milk","name":"Toned Milk"},{"productId":"oats","name":"Rolled Oats"}]}]}

def test_chronological_evaluation_and_saved_model_reload(tmp_path):
    data=dataset();metadata=train(data,tmp_path,trees=10)
    assert metadata["sample"] is True
    assert metadata["price"]["trainRows"]>metadata["price"]["holdoutRows"]>0
    assert metadata["ingredients"]["independentTestAccuracy"] is None
    assert all((tmp_path/name).exists() for name in ["price.joblib","recipes.joblib","ingredients.joblib","metadata.json"])
    registry=ModelRegistry(tmp_path)
    assert registry.status()["trained"] is True
    points=[PricePoint(observedAt=r["observedAt"],priceMinor=r["priceMinor"]) for r in data["prices"]]
    forecast=registry.predict(points,"milk","best","test-pin")
    assert forecast["lowMinor"]<=forecast["estimateMinor"]<=forecast["highMinor"]
    assert "Saved" in forecast["method"] and "sample" in forecast["method"]
    assert registry.predict(points,"unknown","best","test-pin") is None
    assert registry.predict(points,"milk","best","test-pin",sample=False) is None
    assert registry.recommend(["milk","oats"])[0]["recipeId"]=="oats"
    assert registry.match("Toned Milk")[0]["productId"]=="milk"
    assert registry.match("car engine lubricant")==[]

def test_missing_sample_flag_is_rejected(tmp_path):
    data=dataset();del data["sample"]
    import pytest
    with pytest.raises(ValueError):train(data,tmp_path,trees=5)
