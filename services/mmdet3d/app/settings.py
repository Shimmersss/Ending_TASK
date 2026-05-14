from __future__ import annotations

import os
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[1]

DEFAULT_MMDET3D_ROOT = PROJECT_ROOT / "third_party" / "mmdetection3d"
MMDET3D_ROOT = Path(os.getenv("MMDET3D_ROOT", DEFAULT_MMDET3D_ROOT)).resolve()
MODEL_CONFIG = PROJECT_ROOT / "model" / "config.py"
MODEL_CHECKPOINT = PROJECT_ROOT / "model" / "best.pth"
KITTI_INFO_FILE = PROJECT_ROOT / "data" / "kitti_infos_val.pkl"
KITTI_DATASET_ROOT = Path(os.getenv("KITTI_DATASET_ROOT", PROJECT_ROOT / "data" / "kitti")).resolve()
TESTING_ROOT = KITTI_DATASET_ROOT / "testing"
TESTING_CALIB_DIR = TESTING_ROOT / "calib"

TMP_DIR = PROJECT_ROOT / "tmp"
OUTPUT_DIR = PROJECT_ROOT / "outputs"

DEVICE = os.getenv("DEVICE", "cuda:0")
DEFAULT_SCORE_THR = float(os.getenv("DEFAULT_SCORE_THR", "0.3"))

CLASSES = ["Pedestrian", "Cyclist", "Car"]
BOX_EDGES = [
    (0, 1),
    (1, 2),
    (2, 3),
    (3, 0),
    (4, 5),
    (5, 6),
    (6, 7),
    (7, 4),
    (0, 4),
    (1, 5),
    (2, 6),
    (3, 7),
]
