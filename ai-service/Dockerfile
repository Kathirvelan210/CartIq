FROM python:3.13-slim
WORKDIR /app
COPY requirements.lock.txt ./
RUN pip install --no-cache-dir -r requirements.lock.txt
RUN useradd --create-home cartiq
COPY app.py ./
COPY training.py ./
COPY recipe_generation.py ./
COPY price_prediction.py ./
RUN mkdir -p ./models && chmod 755 ./models
USER cartiq
EXPOSE 8001
CMD ["uvicorn","app:app","--host","0.0.0.0","--port","8001","--no-access-log"]

