"""Reproducible training on exported application data, with chronological price evaluation."""
from __future__ import annotations
import argparse
import hashlib
import json
import os
from pathlib import Path
from datetime import datetime, timezone, timedelta
import urllib.request
import joblib
import numpy as np
import pandas as pd
from sklearn.compose import ColumnTransformer
from sklearn.preprocessing import OneHotEncoder
from sklearn.pipeline import Pipeline
from sklearn.ensemble import RandomForestRegressor
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics import mean_absolute_error
from sklearn.metrics.pairwise import cosine_similarity

MODEL_DIRECTORY = Path(os.environ.get("MODEL_DIRECTORY", Path(__file__).parent / "models"))
CATEGORICAL = ["productId", "platform", "locationId"]
NUMERIC = ["lag1", "lag3", "lag7", "mean7", "change3", "dayOfWeek"]

def features(values, day, product, platform, location):
    return {"productId": product, "platform": platform, "locationId": location,
            "lag1": float(values[-1]), "lag3": float(values[-3]), "lag7": float(values[-7]),
            "mean7": float(np.mean(values[-7:])), "change3": float(values[-1]-values[-3]),
            "dayOfWeek": day.weekday()}

def daily_prices(rows):
    frame = pd.DataFrame(rows)
    needed = set(CATEGORICAL + ["observedAt", "priceMinor"])
    if not needed.issubset(frame.columns):
        raise ValueError("Price dataset is missing required fields")
    frame["observedAt"] = pd.to_datetime(frame["observedAt"], utc=True, errors="coerce")
    frame["priceMinor"] = pd.to_numeric(frame["priceMinor"], errors="coerce")
    frame = frame.dropna(subset=list(needed))
    frame = frame[(frame.priceMinor > 0) & (frame.priceMinor <= 1_000_000_000) & (frame.observedAt <= pd.Timestamp.now(tz="UTC"))]
    frame["day"] = frame.observedAt.dt.date
    frame = frame.sort_values("observedAt").groupby(CATEGORICAL+["day"], as_index=False).tail(1)
    best = frame.groupby(["productId", "locationId", "day"], as_index=False).agg(priceMinor=("priceMinor", "min"), observedAt=("observedAt", "max"))
    best["platform"] = "best"
    return pd.concat([frame,best],ignore_index=True).sort_values("observedAt")

def supervised(frame):
    rows, targets, dates = [], [], []
    for keys, group in frame.groupby(CATEGORICAL):
        group = group.sort_values("day")
        values = group.priceMinor.to_numpy(dtype=float)
        for index in range(7,len(group)):
            day = group.iloc[index].day
            # All numeric predictors precede the target day.
            rows.append(features(values[:index],day,*keys))
            targets.append(values[index]);dates.append(day)
    if not rows:
        raise ValueError("At least eight dated observations per product/store/location are required")
    return pd.DataFrame(rows), np.asarray(targets), np.asarray(dates)

def atomic_joblib(value, path):
    temporary = path.with_suffix(".temporary")
    joblib.dump(value, temporary)
    temporary.replace(path)

def train(dataset, directory=MODEL_DIRECTORY, trees=100):
    directory = Path(directory);directory.mkdir(parents=True,exist_ok=True)
    if dataset.get("schemaVersion") != 1 or not isinstance(dataset.get("sample"),bool):
        raise ValueError("Dataset requires schemaVersion=1 and an explicit sample flag")
    frame = daily_prices(dataset["prices"])
    x,y,dates = supervised(frame)
    unique_days = sorted(set(dates))
    if len(unique_days)<14:
        raise ValueError("At least 21 source days are needed for meaningful chronological training and holdout")
    cutoff = unique_days[max(1,int(len(unique_days)*.8))]
    train_mask, validation_mask = dates<cutoff, dates>=cutoff
    if sum(train_mask)<20 or sum(validation_mask)<5:
        raise ValueError("Insufficient chronological training/validation examples")
    transform = ColumnTransformer([("identity",OneHotEncoder(handle_unknown="ignore",sparse_output=False),CATEGORICAL), ("lags","passthrough",NUMERIC)])
    estimator = Pipeline([("features",transform),("regressor",RandomForestRegressor(n_estimators=trees,max_depth=12,min_samples_leaf=3,random_state=42,n_jobs=2))])
    estimator.fit(x.loc[train_mask],y[train_mask])
    prediction = estimator.predict(x.loc[validation_mask])
    learned_mae = float(mean_absolute_error(y[validation_mask],prediction))
    last_mae = float(mean_absolute_error(y[validation_mask],x.loc[validation_mask,"lag1"]))
    mean_mae = float(mean_absolute_error(y[validation_mask],x.loc[validation_mask,"mean7"]))
    metrics = {"randomForestMaeMinor":round(learned_mae,3),"lastPriceMaeMinor":round(last_mae,3),"sevenDayMeanMaeMinor":round(mean_mae,3)}
    selected = min([(learned_mae,"random_forest"),(last_mae,"last_price"),(mean_mae,"seven_day_mean")])[1]
    predictions = prediction if selected=="random_forest" else x.loc[validation_mask,"lag1" if selected=="last_price" else "mean7"].to_numpy()
    errors = np.abs(y[validation_mask]-predictions)
    # Validation metrics remain out of sample. Refit the deployed estimator on all available history.
    estimator.fit(x,y)
    price_artifact = {"estimator":estimator,"selected":selected,"residual90":float(np.quantile(errors,.9)),"validationMaeMinor":float(np.mean(errors)),"groups":set(tuple(row) for row in frame[CATEGORICAL].drop_duplicates().to_numpy()),"sample":dataset["sample"]}
    recipes = dataset.get("recipes",[])
    products = dataset.get("products",[])
    if not recipes or not products:
        raise ValueError("Recipe and product training corpora cannot be empty")
    recipe_texts = [r["name"]+" "+" ".join(i["name"] for i in r["ingredients"]) for r in recipes]
    recipe_vectorizer = TfidfVectorizer(ngram_range=(1,2),strip_accents="unicode")
    recipe_matrix = recipe_vectorizer.fit_transform(recipe_texts)
    product_texts = [p["name"]+" "+p["id"].replace("-"," ")+" "+" ".join(p.get("aliases",[])) for p in products]
    ingredient_vectorizer = TfidfVectorizer(analyzer="char_wb",ngram_range=(3,5),strip_accents="unicode")
    ingredient_matrix = ingredient_vectorizer.fit_transform(product_texts)
    recipe_artifact = {"vectorizer":recipe_vectorizer,"matrix":recipe_matrix,"recipes":recipes,"sample":dataset["sample"]}
    ingredient_artifact = {"vectorizer":ingredient_vectorizer,"matrix":ingredient_matrix,"products":products,"sample":dataset["sample"]}
    # Curated recipe-to-catalogue links are evaluation queries. This is a corpus sanity check,
    # not an independent natural-language test set or a claim of real-world matching accuracy.
    hits,total=0,0
    for recipe in recipes:
        for ingredient in recipe["ingredients"]:
            scores = cosine_similarity(ingredient_vectorizer.transform([ingredient["name"]]),ingredient_matrix)[0]
            hits += products[int(np.argmax(scores))]["id"]==ingredient["productId"];total+=1
    metadata={"trainedAt":datetime.now(timezone.utc).isoformat(),"sample":dataset["sample"],"datasetSha256":hashlib.sha256(json.dumps(dataset,sort_keys=True).encode()).hexdigest(),"source":dataset.get("source","user supplied"),"price":{"model":"RandomForestRegressor","selectedForecast":selected,"trainRows":int(sum(train_mask)),"holdoutRows":int(sum(validation_mask)),"holdoutStart":str(cutoff),"holdoutEnd":str(unique_days[-1]),"metrics":metrics,"groups":len(price_artifact["groups"])},"recipes":{"model":"TF-IDF content ranking","corpusSize":len(recipes),"behavioralEvaluation":"Not performed; no labelled user preference dataset supplied"},"ingredients":{"model":"Character TF-IDF retrieval with abstention","catalogueSize":len(products),"linkedIngredientQueries":total,"corpusSanityTop1Hits":int(hits),"independentTestAccuracy":None},"limitations":["Sample-data metrics do not establish real-world accuracy" if dataset["sample"] else "Evaluate on fresh prospective observations before use","No spending or replenishment model is claimed as trained; those require suitable user histories","Forecast intervals are empirical, not guarantees","Serving amounts and pantry subtraction remain exact calculations"]}
    atomic_joblib(price_artifact,directory/"price.joblib")
    atomic_joblib(recipe_artifact,directory/"recipes.joblib")
    atomic_joblib(ingredient_artifact,directory/"ingredients.joblib")
    temp=directory/"metadata.temporary";temp.write_text(json.dumps(metadata,indent=2),encoding="utf-8");temp.replace(directory/"metadata.json")
    return metadata

class ModelRegistry:
    def __init__(self,directory=MODEL_DIRECTORY):
        self.directory=Path(directory);self.loaded={}
    def load(self,name):
        path=self.directory/(name+".joblib")
        if not path.exists():return None
        stamp=path.stat().st_mtime_ns
        if self.loaded.get(name,(None,None))[0]!=stamp:self.loaded[name]=(stamp,joblib.load(path))
        return self.loaded[name][1]
    def status(self):
        path=self.directory/"metadata.json"
        if not path.exists():return {"trained":False}
        metadata=json.loads(path.read_text(encoding="utf-8"))
        metadata["availableArtifacts"]={name:(self.directory/(name+".joblib")).exists() for name in ["price","recipes","ingredients"]}
        metadata["trained"]=all(metadata["availableArtifacts"].values())
        return metadata
    def predict(self,points,product,platform,location,sample=None):
        artifact=self.load("price")
        if not artifact or (sample is not None and artifact["sample"]!=sample) or (product,platform,location) not in artifact["groups"]:return None
        frame=pd.DataFrame({"day":[p.observedAt.date() for p in points],"price":[p.priceMinor for p in points]})
        daily=frame.groupby("day").price.min().sort_index()
        if len(daily)<7:return None
        values=daily.to_numpy(dtype=float);day=daily.index[-1]+timedelta(days=1)
        row=pd.DataFrame([features(values,day,product,platform,location)])
        estimate=float(artifact["estimator"].predict(row)[0]) if artifact["selected"]=="random_forest" else float(row.iloc[0]["lag1" if artifact["selected"]=="last_price" else "mean7"])
        estimate=float(np.clip(estimate,values.min()*.8,values.max()*1.2));width=max(artifact["residual90"],estimate*.03)
        quality=max(0.,min(.95,1-artifact["validationMaeMinor"]/max(1.,float(np.mean(values)))))
        return {"estimateMinor":max(1,round(estimate)),"lowMinor":max(1,round(estimate-width)),"highMinor":max(1,round(estimate+width)),"confidence":round(quality,3),"method":"Saved "+artifact["selected"].replace("_"," ")+ (" — trained on sample data" if artifact["sample"] else " — trained on recorded observations"),"caveat":"Experimental estimate from a saved model. Historical fit quality is not a calibrated probability. Sample-trained models do not establish real-world accuracy; future prices may fall outside the range."}
    def recommend(self,ingredients,sample=None):
        artifact=self.load("recipes")
        if not artifact or (sample is not None and artifact["sample"]!=sample):return []
        vector=artifact["vectorizer"].transform([" ".join(ingredients)])
        scores=cosine_similarity(vector,artifact["matrix"])[0]
        return [{"recipeId":artifact["recipes"][i]["id"],"score":round(float(scores[i]),4)} for i in np.argsort(-scores) if scores[i]>0]
    def match(self,query,sample=None):
        artifact=self.load("ingredients")
        if not artifact or (sample is not None and artifact["sample"]!=sample):return []
        scores=cosine_similarity(artifact["vectorizer"].transform([query]),artifact["matrix"])[0]
        # Retrieval suggestions must be confirmed against variant, brand, pack, and units.
        return [{"productId":artifact["products"][i]["id"],"name":artifact["products"][i]["name"],"score":round(float(scores[i]),4),"requiresConfirmation":True} for i in np.argsort(-scores)[:3] if scores[i]>=.35]

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dataset",type=Path)
    parser.add_argument("--sample-api",help="Local backend base, e.g. http://127.0.0.1:8080/api")
    parser.add_argument("--output",type=Path,default=MODEL_DIRECTORY)
    args=parser.parse_args()
    if bool(args.dataset)==bool(args.sample_api):parser.error("Choose one of --dataset or --sample-api")
    if args.sample_api:
        from urllib.parse import urlparse
        if urlparse(args.sample_api).hostname not in {"localhost","127.0.0.1"}:parser.error("--sample-api only accepts a local development backend")
        with urllib.request.urlopen(args.sample_api.rstrip("/")+"/training/sample",timeout=30) as response:dataset=json.load(response)
    else:dataset=json.loads(args.dataset.read_text(encoding="utf-8"))
    metadata=train(dataset,args.output)
    print(json.dumps(metadata,indent=2))

if __name__=="__main__":main()
