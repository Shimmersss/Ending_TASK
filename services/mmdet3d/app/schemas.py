from __future__ import annotations

from typing import List, Optional

from pydantic import BaseModel


class Detection(BaseModel):
    label: int
    class_name: str
    score: float
    bbox_3d: List[float]
    corners_3d: List[List[float]]
    box_type_3d: str = "LiDAR"


class PredictResponse(BaseModel):
    filename: str
    num_detections: int
    detections: List[Detection]
    pointcloud_visualization: str
    image_visualization: Optional[str] = None
