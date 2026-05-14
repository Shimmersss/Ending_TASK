from __future__ import annotations

import base64
import os
import pickle
import sys
from io import BytesIO
from pathlib import Path
from typing import Dict, List, Optional, Tuple

import numpy as np
import torch
from PIL import Image, ImageDraw, ImageFont

from .settings import (
    BOX_EDGES,
    CLASSES,
    KITTI_INFO_FILE,
    MMDET3D_ROOT,
    MODEL_CHECKPOINT,
    MODEL_CONFIG,
    PROJECT_ROOT,
    TESTING_CALIB_DIR,
)


class PointPillarsDetector:
    def __init__(self, device: str = "cuda:0") -> None:
        self.device = device
        self.model = None
        self._kitti_infos = None

    def load(self) -> None:
        if self.model is not None and self._kitti_infos is not None:
            return

        if not MMDET3D_ROOT.exists():
            raise FileNotFoundError(f"MMDetection3D root not found: {MMDET3D_ROOT}")
        if not MODEL_CONFIG.exists():
            raise FileNotFoundError(f"Model config not found: {MODEL_CONFIG}")
        if not MODEL_CHECKPOINT.exists():
            raise FileNotFoundError(f"Model checkpoint not found: {MODEL_CHECKPOINT}")
        if not KITTI_INFO_FILE.exists():
            raise FileNotFoundError(f"KITTI info file not found: {KITTI_INFO_FILE}")

        sys.path.insert(0, str(MMDET3D_ROOT))
        os.chdir(str(MMDET3D_ROOT))

        from mmdet3d.apis import init_model

        data_root = str((PROJECT_ROOT / "data").resolve()).replace("\\", "/") + "/"
        ann_file = str(KITTI_INFO_FILE.resolve()).replace("\\", "/")
        cfg_options = {
            "test_dataloader.dataset.data_root": data_root,
            "test_dataloader.dataset.ann_file": ann_file,
            "test_dataloader.dataset.data_prefix.pts": "",
            "test_dataloader.dataset.box_type_3d": "LiDAR",
            "test_dataloader.num_workers": 0,
            "test_evaluator.ann_file": ann_file,
            "val_dataloader.dataset.data_root": data_root,
            "val_dataloader.dataset.ann_file": ann_file,
            "val_dataloader.dataset.data_prefix.pts": "",
            "val_dataloader.dataset.box_type_3d": "LiDAR",
            "val_dataloader.num_workers": 0,
            "val_evaluator.ann_file": ann_file,
            "default_hooks.visualization.draw": False,
            "resume": False,
        }

        self.model = init_model(
            str(MODEL_CONFIG),
            str(MODEL_CHECKPOINT),
            device=self.device,
            cfg_options=cfg_options,
        )
        self._kitti_infos = self._load_kitti_infos()

    def _load_kitti_infos(self) -> Dict[int, Dict]:
        with KITTI_INFO_FILE.open("rb") as f:
            data = pickle.load(f)
        items = data.get("data_list", []) if isinstance(data, dict) else data
        infos = {}
        for item in items:
            try:
                infos[int(item["sample_idx"])] = item
            except Exception:
                continue
        return infos

    def _get_sample_info(self, sample_idx: int) -> Dict:
        if self._kitti_infos is None:
            self._kitti_infos = self._load_kitti_infos()
        if sample_idx not in self._kitti_infos:
            raise ValueError(
                "sample %06d is not present in kitti_infos_val.pkl" % sample_idx
            )
        return self._kitti_infos[sample_idx]

    def predict(self, point_cloud_path: Path, score_thr: float = 0.3):
        self.load()

        from mmdet3d.apis import inference_detector

        with torch.no_grad():
            result, _ = inference_detector(self.model, str(point_cloud_path))

        pred = result.pred_instances_3d
        scores = pred.scores_3d.detach().cpu().numpy()
        labels = pred.labels_3d.detach().cpu().numpy()
        boxes_3d = pred.bboxes_3d
        boxes = boxes_3d.tensor.detach().cpu().numpy()
        corners = boxes_3d.corners.detach().cpu().numpy()

        keep = scores >= score_thr
        scores = scores[keep]
        labels = labels[keep]
        boxes = boxes[keep]
        corners = corners[keep]

        detections = []
        for label, score, box, box_corners in zip(labels.tolist(), scores.tolist(), boxes.tolist(), corners.tolist()):
            class_name = CLASSES[label] if 0 <= label < len(CLASSES) else str(label)
            detections.append(
                {
                    "label": int(label),
                    "class_name": class_name,
                    "score": float(score),
                    "bbox_3d": [float(v) for v in box[:7]],
                    "corners_3d": [
                        [float(coord) for coord in corner]
                        for corner in box_corners
                    ],
                    "box_type_3d": "LiDAR",
                }
            )

        corners = corners.astype(np.float32) if len(corners) else np.zeros((0, 8, 3), dtype=np.float32)
        return detections, corners, labels.astype(np.int32), scores.astype(np.float32)

    def render_pointcloud_visualization(
        self,
        point_cloud_path: Path,
        corners: np.ndarray,
        labels: np.ndarray,
        scores: np.ndarray,
    ) -> str:
        points = np.fromfile(str(point_cloud_path), dtype=np.float32).reshape(-1, 4)
        points = self._downsample_points(points, 50000)

        width, height = 1280, 720
        x_min, x_max = 0.0, 70.0
        y_min, y_max = -40.0, 40.0
        background = Image.new("RGB", (width, height), (15, 19, 26))
        gray = np.zeros((height, width), dtype=np.uint8)

        if len(points) > 0:
            valid = (
                (points[:, 0] >= x_min)
                & (points[:, 0] <= x_max)
                & (points[:, 1] >= y_min)
                & (points[:, 1] <= y_max)
            )
            pts = points[valid]
            if len(pts) > 0:
                xs = ((pts[:, 0] - x_min) / (x_max - x_min) * (width - 1)).astype(np.int32)
                ys = ((1.0 - (pts[:, 1] - y_min) / (y_max - y_min)) * (height - 1)).astype(np.int32)
                intensity = self._normalize_uint8(pts[:, 3])
                flat = ys * width + xs
                np.maximum.at(gray.ravel(), flat, intensity)

        canvas = np.stack([gray, gray, gray], axis=-1)
        canvas = (canvas * 0.85 + np.array([5, 12, 18], dtype=np.uint8)).astype(np.uint8)
        image = Image.fromarray(canvas, mode="RGB")
        draw = ImageDraw.Draw(image)

        self._draw_bev_grid(draw, width, height, x_min, x_max, y_min, y_max)
        for box_corners, label, score in zip(corners, labels, scores):
            poly = [
                self._bev_point(x, y, width, height, x_min, x_max, y_min, y_max)
                for x, y in box_corners[:4, :2]
            ]
            color = self._class_color(int(label))
            draw.line(poly + [poly[0]], fill=color, width=3)
            draw.text(
                (poly[0][0] + 6, poly[0][1] + 4),
                "%s %.2f" % (self._class_name(int(label)), float(score)),
                fill=color,
                font=self._font(),
            )
        return self._image_to_data_url(image)

    def render_image_visualization(
        self,
        image_path: Path,
        corners: np.ndarray,
        labels: np.ndarray,
        scores: np.ndarray,
        sample_idx: int,
        calib_path: Optional[Path] = None,
    ) -> str:
        lidar2img = self._load_lidar2img_from_calib(calib_path) if calib_path else self._resolve_lidar2img(sample_idx)

        image = Image.open(str(image_path)).convert("RGB")
        draw = ImageDraw.Draw(image)
        for box_corners, label, score in zip(corners, labels, scores):
            projected = self._project_corners(box_corners, lidar2img)
            if not projected:
                continue
            color = self._class_color(int(label))
            for start, end in BOX_EDGES:
                p1 = projected[start]
                p2 = projected[end]
                if p1 is None or p2 is None:
                    continue
                draw.line([p1, p2], fill=color, width=3)
            visible = [p for p in projected if p is not None]
            if visible:
                cx = sum(p[0] for p in visible) / len(visible)
                cy = sum(p[1] for p in visible) / len(visible)
                draw.text(
                    (cx + 4, cy + 4),
                    "%s %.2f" % (self._class_name(int(label)), float(score)),
                    fill=color,
                    font=self._font(),
                )
        return self._image_to_data_url(image)

    def _resolve_lidar2img(self, sample_idx: int) -> np.ndarray:
        info = self._kitti_infos.get(sample_idx) if self._kitti_infos else None
        if info:
            cam2 = info.get("images", {}).get("CAM2", {})
            lidar2img = np.asarray(cam2.get("lidar2img"), dtype=np.float32)
            if lidar2img.shape == (4, 4):
                return lidar2img

        calib_path = TESTING_CALIB_DIR / ("%06d.txt" % sample_idx)
        if calib_path.exists():
            return self._load_lidar2img_from_calib(calib_path)

        raise ValueError("sample %06d does not contain CAM2 lidar2img and calib fallback is missing" % sample_idx)

    def _load_lidar2img_from_calib(self, calib_path: Path) -> np.ndarray:
        values = {}
        for line in calib_path.read_text(encoding="utf-8", errors="ignore").splitlines():
            if ":" not in line:
                continue
            key, payload = line.split(":", 1)
            parts = [float(x) for x in payload.strip().split()]
            values[key.strip()] = parts

        p2 = np.asarray(values.get("P2"), dtype=np.float32)
        r0 = np.asarray(values.get("R0_rect"), dtype=np.float32)
        tr = np.asarray(values.get("Tr_velo_to_cam"), dtype=np.float32)
        if p2.size != 12 or r0.size != 9 or tr.size != 12:
            raise ValueError("invalid KITTI calib file: %s" % calib_path)
        p2 = p2.reshape(3, 4)
        r0 = r0.reshape(3, 3)
        tr = tr.reshape(3, 4)
        r0_4 = np.eye(4, dtype=np.float32)
        r0_4[:3, :3] = r0
        tr_4 = np.eye(4, dtype=np.float32)
        tr_4[:3, :] = tr
        return p2 @ r0_4 @ tr_4

    def _box_corners(self, box: np.ndarray) -> np.ndarray:
        x, y, z, dx, dy, dz, yaw = [float(v) for v in box[:7]]
        cos_yaw = np.cos(yaw)
        sin_yaw = np.sin(yaw)
        rot = np.array(
            [
                [cos_yaw, -sin_yaw, 0.0],
                [sin_yaw, cos_yaw, 0.0],
                [0.0, 0.0, 1.0],
            ],
            dtype=np.float32,
        )
        x_corners = np.array(
            [
                dx / 2,
                dx / 2,
                -dx / 2,
                -dx / 2,
                dx / 2,
                dx / 2,
                -dx / 2,
                -dx / 2,
            ],
            dtype=np.float32,
        )
        y_corners = np.array(
            [
                dy / 2,
                -dy / 2,
                -dy / 2,
                dy / 2,
                dy / 2,
                -dy / 2,
                -dy / 2,
                dy / 2,
            ],
            dtype=np.float32,
        )
        z_corners = np.array(
            [
                dz / 2,
                dz / 2,
                dz / 2,
                dz / 2,
                -dz / 2,
                -dz / 2,
                -dz / 2,
                -dz / 2,
            ],
            dtype=np.float32,
        )
        corners = np.stack([x_corners, y_corners, z_corners], axis=1)
        corners = corners @ rot.T
        corners += np.array([x, y, z], dtype=np.float32)
        return corners

    def _project_corners(self, corners: np.ndarray, lidar2img: np.ndarray):
        projected = []
        for corner in corners:
            vec = np.array([corner[0], corner[1], corner[2], 1.0], dtype=np.float32)
            proj = lidar2img @ vec
            depth = float(proj[2])
            if depth <= 1e-3:
                projected.append(None)
                continue
            projected.append((float(proj[0] / depth), float(proj[1] / depth)))
        return projected

    def _downsample_points(self, points: np.ndarray, max_points: int) -> np.ndarray:
        if len(points) <= max_points:
            return points
        rng = np.random.default_rng(0)
        keep = rng.choice(len(points), size=max_points, replace=False)
        return points[keep]

    def _bev_point(
        self,
        x: float,
        y: float,
        width: int,
        height: int,
        x_min: float,
        x_max: float,
        y_min: float,
        y_max: float,
    ) -> Tuple[float, float]:
        px = (x - x_min) / (x_max - x_min) * (width - 1)
        py = (1.0 - (y - y_min) / (y_max - y_min)) * (height - 1)
        return px, py

    def _draw_bev_grid(
        self,
        draw: ImageDraw.ImageDraw,
        width: int,
        height: int,
        x_min: float,
        x_max: float,
        y_min: float,
        y_max: float,
    ) -> None:
        grid_color = (42, 51, 65)
        for x in range(0, 71, 10):
            p1 = self._bev_point(float(x), y_min, width, height, x_min, x_max, y_min, y_max)
            p2 = self._bev_point(float(x), y_max, width, height, x_min, x_max, y_min, y_max)
            draw.line([p1, p2], fill=grid_color, width=1)
        for y in range(-40, 41, 10):
            p1 = self._bev_point(x_min, float(y), width, height, x_min, x_max, y_min, y_max)
            p2 = self._bev_point(x_max, float(y), width, height, x_min, x_max, y_min, y_max)
            draw.line([p1, p2], fill=grid_color, width=1)

    def _normalize_uint8(self, values: np.ndarray) -> np.ndarray:
        values = values.astype(np.float32)
        if values.size == 0:
            return np.zeros((0,), dtype=np.uint8)
        min_v = float(values.min())
        max_v = float(values.max())
        if max_v <= min_v:
            return np.full(values.shape, 160, dtype=np.uint8)
        norm = (values - min_v) / (max_v - min_v)
        return np.clip(norm * 255.0, 0, 255).astype(np.uint8)

    def _image_to_data_url(self, image: Image.Image) -> str:
        buffer = BytesIO()
        image.save(buffer, format="PNG")
        return "data:image/png;base64," + base64.b64encode(buffer.getvalue()).decode("ascii")

    def _class_name(self, label: int) -> str:
        return CLASSES[label] if 0 <= label < len(CLASSES) else str(label)

    def _class_color(self, label: int) -> Tuple[int, int, int]:
        palette = {
            0: (255, 92, 71),
            1: (84, 204, 255),
            2: (72, 220, 131),
        }
        return palette.get(label, (255, 220, 80))

    def _font(self):
        try:
            return ImageFont.load_default()
        except Exception:
            return None
