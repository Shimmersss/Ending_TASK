# KITTI PointPillars FastAPI 服务

这是给前端用的 MMDet3D 推理服务。现在支持：
- 只上传点云 `bin`
- 点云 + 可选左相机图片
- 返回检测结果 + 可视化图片 base64

## 依赖

- Python 3.7
- PyTorch 1.13.1+cu117
- MMDetection3D 运行环境

## 启动

推荐直接运行：

```powershell
cd F:\YOLO\kitty\fastapi_realtime_infer
$env:MMDET3D_ROOT = "F:\YOLO\kitty\MMDetection3D\mmdetection3d"
.\run.ps1
```

`run.ps1` 会优先自动寻找可用的 Python：
- `py -3.7`
- `C:\Users\Shienroxic\AppData\Local\Programs\Python\Python37\python.exe`
- `python`

它也会自动寻找 MMDetection3D 源码：
- `third_party\mmdetection3d`
- `F:\YOLO\kitty\MMDetection3D\mmdetection3d`

如果你想手动指定：

```powershell
$env:PYTHON = "C:\Users\Shienroxic\AppData\Local\Programs\Python\Python37\python.exe"
.\run.ps1
```

## 接口

### 健康检查

```text
GET /health
```

### 推理

```text
POST /predict
```

form-data 字段：

| 字段 | 必填 | 说明 |
|---|---|---|
| `point_cloud_file` | 是 | KITTI `.bin` 点云文件 |
| `image_file` | 否 | 左相机图片，上传后会输出图片投影可视化 |
| `score_thr` | 否 | 置信度阈值，默认 `0.3` |

兼容旧字段：
- `file` 也可以作为点云字段

### 返回

```json
{
  "filename": "000001.bin",
  "num_detections": 3,
  "detections": [
    {
      "label": 0,
      "class_name": "Car",
      "score": 0.91,
      "bbox_3d": [1.0, 2.0, 3.0, 3.9, 1.6, 1.56, 0.0]
    }
  ],
  "pointcloud_visualization": "data:image/png;base64,...",
  "image_visualization": "data:image/png;base64,..."
}
```

## 测试样例

只点云：

```powershell
curl.exe -X POST "http://127.0.0.1:8000/predict" `
  -F "point_cloud_file=@F:\YOLO\kitty\testing\velodyne\000001.bin" `
  -F "score_thr=0.3"
```

点云 + 图片：

```powershell
curl.exe -X POST "http://127.0.0.1:8000/predict" `
  -F "point_cloud_file=@F:\YOLO\kitty\testing\velodyne\000001.bin" `
  -F "image_file=@F:\YOLO\kitty\testing\image_2\000001.png" `
  -F "score_thr=0.3"
```

## 说明

- 图片投影优先使用 `data/kitti_infos_val.pkl` 里的 `CAM2.lidar2img`
- 如果对应样本不在 pkl 中，会自动尝试读取 `F:\YOLO\kitty\testing\calib\{stem}.txt`
- 前端已经改成点云必传、图片可选
