# YOLO HTTP Service

Standalone FastAPI service for YOLO inference.

## Files

- `app.py` - service entrypoint
- `requirements.txt` - runtime dependencies
- `pyproject.toml` - package metadata and console script

## Setup

```bash
python -m pip install -U pip
pip install -r requirements.txt
```

## Model

Set `MODEL_PATH` to your trained weight file, or place `best.pt` / `yolo26m.pt` in the project root.

Examples:

```bash
set MODEL_PATH=F:\path\to\best.pt
```

```bash
export MODEL_PATH=/home/guest/lzx/YOLO/best.pt
```

## Run

```bash
python app.py
```

or

```bash
python -m uvicorn app:app --host 0.0.0.0 --port 8000
```

The service exposes:

- `GET /health`
- `POST /predict`
- `POST /predict_url`
- `POST /predict_base64`

## Request format

`/predict` accepts `multipart/form-data` with:

- `file`
- `conf`
- `iou`

`/predict_url` accepts JSON:

```json
{"image_url":"https://example.com/image.jpg","conf":0.25,"iou":0.45}
```

`/predict_base64` accepts JSON:

```json
{"image_base64":"<base64 string>","conf":0.25,"iou":0.45}
```
