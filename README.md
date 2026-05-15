# Ending_TASK

一个集成了 Vue 3 前端、Spring Boot 后端、YOLO 图像检测服务和 MMDet3D 点云检测服务的多模块项目。

项目当前面向本地联调场景，仓库内已经包含统一启动、停止和状态检查脚本，适合直接在 Windows + PowerShell 环境下运行。

## 项目结构

```text
Ending_TASK/
├─ backend/                   Spring Boot 后端
├─ frontend/                  Vue 3 + Vite 前端
├─ services/
│  ├─ yolo-http/              YOLO FastAPI 服务
│  └─ mmdet3d/                MMDet3D FastAPI 服务
├─ .run/                      运行日志与进程元数据目录
├─ start-all.ps1              一键启动全部服务
├─ stop-all.ps1               一键停止全部服务
├─ status-all.ps1             查看服务状态
├─ README.md
└─ .gitignore
```

## 技术栈

- 前端：Vue 3、Vue Router、Vite、Axios
- 后端：Spring Boot 3、Spring Web、Spring Data JPA、MySQL
- Python 服务：
  - `services/yolo-http`：FastAPI + Ultralytics YOLO
  - `services/mmdet3d`：FastAPI + MMDetection3D / PointPillars

## 服务端口

默认端口如下：

- 前端：`http://127.0.0.1:3000`
- Java 后端：`http://127.0.0.1:8080`
- YOLO 服务：`http://127.0.0.1:9000`
- MMDet3D 服务：`http://127.0.0.1:8000`

## 模块关系

- `web` 前端通过 Vite 代理访问后端和 3D 检测服务
- `backend` 后端通过配置项调用 YOLO 与 MMDet3D 两个 Python 服务
- `services/yolo-http` 负责图像目标检测
- `services/mmdet3d` 负责 KITTI 点云 3D 检测与可视化

当前前端代理配置：

- `/api` -> `http://localhost:8080`
- `/mmdet3d` -> `http://127.0.0.1:8000`

当前后端默认外部服务配置：

- `detection.yolo-base-url=http://127.0.0.1:9000`
- `detection.mmdet3d-base-url=http://127.0.0.1:8000`

## 运行环境

建议环境：

- Windows PowerShell
- JDK 17 或 21
- Maven 3.9+
- Node.js 18+
- Python 3.10/3.11：用于 `services/yolo-http`
- Python 3.7 且可用 `torch`：用于 `services/mmdet3d`
- MySQL：用于 `backend`

## 启动前准备

### 1. 数据库配置

后端默认连接：

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/VUE?useSSL=false&serverTimezone=UTC&characterEncoding=UTF-8
spring.datasource.username=root
spring.datasource.password=***
```

请根据本机环境修改以下文件中的数据库配置：

- `backend/src/main/resources/application.properties`
- `backend/src/main/resources/application-local.properties`

如果不希望把本机配置提交到仓库，建议把敏感信息改用环境变量注入。

### 2. 模型文件

#### YOLO

`services/yolo-http/app.py` 会按下面的顺序查找模型：

- `MODEL_PATH` 环境变量
- `services/yolo-http/best.pt`
- `services/yolo-http/yolo26m.pt`
- 其他回退路径

#### MMDet3D

默认使用：

- 模型权重：`services/mmdet3d/model/best.pth`
- 配置文件：`services/mmdet3d/model/config.py`

如果本地存在 KITTI 数据集，会优先读取：

- `services/mmdet3d/data/kitti`

如果不存在，则启动脚本会尝试旧路径：

- `F:\YOLO\kitty`

### 3. 依赖安装

#### 前端

```powershell
cd .\frontend
npm install
```

#### 后端

```powershell
cd .\backend
mvn -DskipTests package
```

后端的本地敏感配置现在推荐通过环境变量提供，不再把真实数据库密码或 Dify Key 直接写进仓库文件。

如果你使用根目录的一键脚本，推荐先复制根目录的 `.env.local.example` 为 `.env.local`，再按本机环境修改：

```powershell
Copy-Item .\.env.local.example .\.env.local
```

`.env.local.example` 示例内容如下：

```dotenv
DB_USERNAME=root
DB_PASSWORD=你的数据库密码
DIFY_BASE_URL=http://localhost/v1
DIFY_API_KEY=你的DifyKey
DIFY_WORKFLOW_ID=你的WorkflowId
DIFY_WORKFLOW_USER_PREFIX=drone-demo
KITTI_DATASET_ROOT=F:/YOLO/kitty
```

`start-all.ps1` 会自动读取这个文件并把变量注入当前启动流程。

#### YOLO 服务

```powershell
cd .\services\yolo-http
pip install -r requirements.txt
```

#### MMDet3D 服务

请按你本机已有的 MMDetection3D / PyTorch 环境安装依赖，仓库中已有：

- `services/mmdet3d/requirements.txt`
- `services/mmdet3d/run.ps1`

## 一键启动

在仓库根目录执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\start-all.ps1
```

脚本会尝试完成以下工作：

- 启动 Spring Boot 后端
- 启动 Vue 前端开发服务器
- 启动 YOLO FastAPI 服务
- 启动 MMDet3D FastAPI 服务
- 检查四个服务是否成功监听并可访问
- 如果存在根目录 `.env.local`，自动加载其中的本地环境变量

## 停止服务

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\stop-all.ps1
```

## 查看状态

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\status-all.ps1
```

## 单独运行各模块

### 前端

```powershell
cd .\frontend
npm run dev -- --host
```

### 后端

```powershell
cd .\backend
mvn spring-boot:run
```

如果你不是通过 `start-all.ps1` 启动，而是手动启动后端，请先在当前 PowerShell 会话中设置需要的环境变量，例如：

```powershell
$env:DB_USERNAME="root"
$env:DB_PASSWORD="你的数据库密码"
$env:DIFY_API_KEY="你的DifyKey"
$env:DIFY_WORKFLOW_ID="你的WorkflowId"
$env:DIFY_WORKFLOW_USER_PREFIX="drone-demo"
$env:KITTI_DATASET_ROOT="F:/YOLO/kitty"
```

### YOLO 服务

```powershell
cd .\services\yolo-http
python app.py
```

健康检查：

```text
GET http://127.0.0.1:9000/health
```

### MMDet3D 服务

```powershell
cd .\services\mmdet3d
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000
```

健康检查：

```text
GET http://127.0.0.1:8000/health
```

## 常用接口

### YOLO 服务

- `GET /health`
- `POST /predict`
- `POST /predict_url`
- `POST /predict_base64`

### MMDet3D 服务

- `GET /health`
- `POST /predict`

其中 `MMDet3D /predict` 支持：

- 必传：`point_cloud_file`
- 可选：`image_file`
- 可选：`calib_file`
- 可选：`score_thr`

## 日志与运行文件

统一启动脚本会把运行信息写入 `.run/`，常见文件包括：

- `dev-processes.json`
- `backend.out.log`
- `backend.err.log`
- `frontend.out.log`
- `frontend.err.log`
- `yolo.out.log`
- `yolo.err.log`
- `mmdet3d.out.log`
- `mmdet3d.err.log`

这些文件属于本地运行产物，不建议提交到 Git。

## 近期更新

- 前端主页恢复了大疆巡检背景，并关闭了 Vue DevTools 角标。
- 模型对话区支持继续追问巡检建议。
- Dify 调用已改为先返回检测结果，再异步补充 Dify 输出。
- `media_url` 已改为短文本引用，避免触发 Dify 512 长度校验。
- Dify 请求超时已放宽，减少本地推理等待时的超时失败。

## 注意事项

- `start-all.ps1` 当前包含明显的本机路径假设，例如 JDK、Maven、KITTI 数据目录等；如果换机器运行，需要先调整脚本中的路径候选项。
- `backend/src/main/resources/application-local.properties` 已改成优先读取环境变量；推荐把真实本机配置写入根目录 `.env.local`，并且不要提交到公共仓库。
- 仓库中的 `.env.local.example` 用于分发配置模板；实际运行请复制为 `.env.local` 后再填写你自己的本机值。
- `services/mmdet3d/third_party/mmdetection3d` 体积较大，若后续需要精简仓库，建议改为子模块或外部依赖管理。

## 推荐开发流程

1. 先确认 MySQL、Python、Node、JDK 都已可用
2. 检查模型文件和数据集路径是否存在
3. 执行 `start-all.ps1`
4. 打开 `http://127.0.0.1:3000`
5. 如果启动失败，优先查看 `.run/` 下对应日志
