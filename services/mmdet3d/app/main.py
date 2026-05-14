from __future__ import annotations

from pathlib import Path
from typing import Optional
from uuid import uuid4

import numpy as np
from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from fastapi.middleware.cors import CORSMiddleware

from .detector import PointPillarsDetector
from .schemas import PredictResponse
from .settings import DEFAULT_SCORE_THR, DEVICE, TMP_DIR


app = FastAPI(title="KITTI PointPillars Realtime Inference")
app.add_middleware(
    CORSMiddleware,
    allow_origins=[
        "http://localhost:3000",
        "http://127.0.0.1:3000",
    ],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)
detector = PointPillarsDetector(device=DEVICE)


@app.on_event("startup")
def startup() -> None:
    TMP_DIR.mkdir(parents=True, exist_ok=True)
    detector.load()


@app.get("/health")
def health() -> dict:
    return {"status": "ok", "device": DEVICE}


def _safe_unlink(path: Path) -> None:
    try:
        path.unlink()
    except FileNotFoundError:
        pass


@app.post("/predict", response_model=PredictResponse)
async def predict(
    point_cloud_file: Optional[UploadFile] = File(None),
    image_file: Optional[UploadFile] = File(None),
    calib_file: Optional[UploadFile] = File(None),
    file: Optional[UploadFile] = File(None),
    score_thr: float = Form(DEFAULT_SCORE_THR),
) -> PredictResponse:
    point_cloud = point_cloud_file or file
    if point_cloud is None:
        raise HTTPException(status_code=400, detail="point_cloud_file is required")

    point_name = point_cloud.filename or "upload.bin"
    if not point_name.lower().endswith(".bin"):
        raise HTTPException(status_code=400, detail="point_cloud_file must be a KITTI .bin file")

    stem = Path(point_name).stem
    if not stem.isdigit():
        raise HTTPException(status_code=400, detail="point_cloud_file name must be numeric, e.g. 000001.bin")
    sample_idx = int(stem)

    point_suffix = Path(point_name).suffix or ".bin"
    point_tmp = TMP_DIR / ("%s%s" % (uuid4().hex, point_suffix))
    point_tmp.write_bytes(await point_cloud.read())

    image_tmp = None
    calib_tmp = None
    try:
        detections, corners, labels, scores = detector.predict(point_tmp, score_thr=score_thr)
        pointcloud_visualization = detector.render_pointcloud_visualization(
            point_cloud_path=point_tmp,
            corners=corners,
            labels=labels,
            scores=scores,
        )

        image_visualization = None
        if image_file is not None:
            image_name = image_file.filename or "upload.png"
            if Path(image_name).stem and Path(image_name).stem != stem:
                raise HTTPException(
                    status_code=400,
                    detail="image_file must match point_cloud_file by filename stem",
                )
            image_suffix = Path(image_name).suffix or ".png"
            image_tmp = TMP_DIR / ("%s%s" % (uuid4().hex, image_suffix))
            image_tmp.write_bytes(await image_file.read())
            if calib_file is not None:
                calib_name = calib_file.filename or "calib.txt"
                if not calib_name.lower().endswith(".txt"):
                    raise HTTPException(status_code=400, detail="calib_file must be a KITTI .txt file")
                calib_tmp = TMP_DIR / ("%s.txt" % uuid4().hex)
                calib_tmp.write_bytes(await calib_file.read())
            image_visualization = detector.render_image_visualization(
                image_path=image_tmp,
                corners=corners,
                labels=labels,
                scores=scores,
                sample_idx=sample_idx,
                calib_path=calib_tmp,
            )

        return PredictResponse(
            filename=point_name,
            num_detections=len(detections),
            detections=detections,
            pointcloud_visualization=pointcloud_visualization,
            image_visualization=image_visualization,
        )
    finally:
        _safe_unlink(point_tmp)
        if image_tmp is not None:
            _safe_unlink(image_tmp)
        if calib_tmp is not None:
            _safe_unlink(calib_tmp)
