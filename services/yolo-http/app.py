from __future__ import annotations

import base64
import os
import urllib.request
from pathlib import Path
from typing import Any

import cv2
import numpy as np
from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from ultralytics import YOLO


def _resolve_model_path() -> str:
    env_path = os.environ.get("MODEL_PATH")
    if env_path:
        return env_path

    script_dir = Path(__file__).resolve().parent
    fallback_candidates = [
        script_dir / "best.pt",
        script_dir / "yolo26m.pt",
        script_dir / "weights" / "best.pt",
        Path.cwd() / "best.pt",
        Path.cwd() / "yolo26m.pt",
    ]
    for candidate in fallback_candidates:
        if candidate.exists():
            return str(candidate)

    raise FileNotFoundError(
        "Cannot find a model file. Set MODEL_PATH or place best.pt / yolo26m.pt in the project root."
    )


MODEL_PATH = _resolve_model_path()
MODEL = YOLO(MODEL_PATH)

app = FastAPI(title="YOLO HTTP Service", version="1.0.0")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


class UrlRequest(BaseModel):
    image_url: str
    conf: float = 0.25
    iou: float = 0.45


class Base64Request(BaseModel):
    image_base64: str
    conf: float = 0.25
    iou: float = 0.45


def _load_image_from_bytes(data: bytes) -> np.ndarray:
    array = np.frombuffer(data, dtype=np.uint8)
    image = cv2.imdecode(array, cv2.IMREAD_COLOR)
    if image is None:
        raise HTTPException(status_code=400, detail="Invalid image payload")
    return image


def _fetch_image_bytes(image_url: str) -> bytes:
    try:
        with urllib.request.urlopen(image_url, timeout=30) as response:
            return response.read()
    except Exception as exc:
        raise HTTPException(status_code=400, detail=f"Failed to fetch image_url: {exc}") from exc


def _decode_base64_image(image_base64: str) -> bytes:
    payload = image_base64.strip()
    if payload.startswith("data:") and "," in payload:
        payload = payload.split(",", 1)[1]
    try:
        return base64.b64decode(payload)
    except Exception as exc:
        raise HTTPException(status_code=400, detail=f"Invalid base64 image payload: {exc}") from exc


def _infer(image: np.ndarray, conf: float, iou: float) -> dict[str, Any]:
    results = MODEL.predict(source=image, conf=conf, iou=iou, verbose=False)
    result = results[0]
    annotated = result.plot()
    success, buffer = cv2.imencode(".png", annotated)
    if not success:
        raise HTTPException(status_code=500, detail="Failed to encode annotated image")

    annotated_image_base64 = base64.b64encode(buffer.tobytes()).decode("utf-8")
    return {
        "model": MODEL_PATH,
        "detections": result.summary(normalize=False),
        "annotated_image": f"data:image/png;base64,{annotated_image_base64}",
    }


@app.get("/")
def root() -> dict[str, str]:
    return {"name": "YOLO HTTP Service", "status": "ready"}


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok", "model": MODEL_PATH}


@app.post("/predict")
async def predict(file: UploadFile = File(...), conf: float = Form(0.25), iou: float = Form(0.45)) -> dict[str, Any]:
    image_bytes = await file.read()
    image = _load_image_from_bytes(image_bytes)
    return _infer(image, conf, iou)


@app.post("/predict_url")
def predict_url(payload: UrlRequest) -> dict[str, Any]:
    image_bytes = _fetch_image_bytes(payload.image_url)
    image = _load_image_from_bytes(image_bytes)
    return _infer(image, payload.conf, payload.iou)


@app.post("/predict_base64")
def predict_base64(payload: Base64Request) -> dict[str, Any]:
    image_bytes = _decode_base64_image(payload.image_base64)
    image = _load_image_from_bytes(image_bytes)
    return _infer(image, payload.conf, payload.iou)


def main() -> None:
    import subprocess
    import sys

    port = int(os.environ.get("PORT", 8000))
    host = os.environ.get("HOST", "0.0.0.0")

    subprocess.run(
        [
            sys.executable,
            "-m",
            "uvicorn",
            "--app-dir",
            str(Path(__file__).resolve().parent),
            "app:app",
            "--host",
            host,
            "--port",
            str(port),
        ],
        check=True,
    )


if __name__ == "__main__":
    main()
