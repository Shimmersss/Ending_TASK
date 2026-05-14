import json
import math
from pathlib import Path

import matplotlib.pyplot as plt
import numpy as np


ROOT = Path(__file__).resolve().parents[1]
POINTS_PATH = ROOT / 'demo' / 'data' / 'kitti' / '000008.bin'
PRED_PATH = ROOT / 'demo_outputs' / 'preds' / '000008.json'
OUT_PATH = ROOT / 'demo_outputs' / '000008_bev.png'


def rotated_box_corners(x, y, dx, dy, yaw):
    hx = dx / 2.0
    hy = dy / 2.0
    corners = np.array([
        [hx, hy],
        [hx, -hy],
        [-hx, -hy],
        [-hx, hy],
        [hx, hy],
    ])
    c = math.cos(yaw)
    s = math.sin(yaw)
    rot = np.array([[c, -s], [s, c]])
    corners = corners @ rot.T
    corners[:, 0] += x
    corners[:, 1] += y
    return corners


def main():
    points = np.fromfile(POINTS_PATH, dtype=np.float32).reshape(-1, 4)

    with open(PRED_PATH, 'r', encoding='utf-8') as f:
        pred = json.load(f)

    # pcd_demo saves a flat dict in preds/000008.json for single-sample inference.
    bboxes = pred['bboxes_3d']
    scores = pred['scores_3d']

    mask = (
        (points[:, 0] >= 0.0) & (points[:, 0] <= 70.0) &
        (points[:, 1] >= -40.0) & (points[:, 1] <= 40.0)
    )
    p = points[mask]

    if len(p) > 25000:
        idx = np.random.choice(len(p), 25000, replace=False)
        p = p[idx]

    fig, ax = plt.subplots(figsize=(11, 6.5), dpi=160)
    ax.scatter(p[:, 0], p[:, 1], s=0.15, c='black', alpha=0.35)

    for box, score in zip(bboxes, scores):
        if score < 0.3:
            continue
        x, y, _z, dx, dy, _dz, yaw = box
        corners = rotated_box_corners(x, y, dx, dy, yaw)
        ax.plot(corners[:, 0], corners[:, 1], color='#d7191c', linewidth=1.5)
        ax.text(x, y, f'{score:.2f}', color='#2c7bb6', fontsize=7)

    ax.set_title('MMDetection3D PointPillars Demo (BEV) - 000008', fontsize=12)
    ax.set_xlabel('X (forward, m)')
    ax.set_ylabel('Y (left, m)')
    ax.set_xlim(0, 70)
    ax.set_ylim(-40, 40)
    ax.set_aspect('equal', adjustable='box')
    ax.grid(True, linewidth=0.35, alpha=0.35)

    fig.tight_layout()
    fig.savefig(OUT_PATH)
    print(str(OUT_PATH))


if __name__ == '__main__':
    main()
