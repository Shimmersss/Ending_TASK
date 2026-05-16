# DroneInspect-AI

> 无人机巡检检测与 AI 决策平台 / AI-powered drone inspection platform

[中文](#中文) | [English](#english)

## 中文

这是一个集成了 Vue 3 前端、Spring Boot 后端、YOLO 图像检测服务和 MMDet3D 点云检测服务的多模块项目，适合在 Windows + PowerShell 环境下进行本地联调与演示。

### 项目结构

```text
Ending_TASK/
├─ backend/                   Spring Boot 后端
├─ frontend/                  Vue 3 + Vite 前端
├─ services/
│  ├─ dify/                   Dify 工作流配置文件
│  ├─ yolo-http/              YOLO FastAPI 服务
│  └─ mmdet3d/                MMDet3D FastAPI 服务
├─ .run/                      运行日志与进程元数据目录
├─ start-all.ps1              一键启动全部服务
├─ stop-all.ps1               一键停止全部服务
├─ status-all.ps1             查看服务状态
├─ README.md
└─ .gitignore
```

### 技术栈

- 前端：Vue 3、Vue Router、Vite、Axios
- 后端：Spring Boot 3、Spring Web、Spring Data JPA、MySQL
- Python 服务：
- `services/yolo-http`：FastAPI + Ultralytics YOLO
- `services/mmdet3d`：FastAPI + MMDetection3D / PointPillars

### 服务端口

默认端口如下：

- 前端：`http://127.0.0.1:3000`
- Java 后端：`http://127.0.0.1:8080`
- YOLO 服务：`http://127.0.0.1:9000`
- MMDet3D 服务：`http://127.0.0.1:8000`

### 模块关系

- `frontend` 通过 Vite 代理访问 `backend`
- `backend` 通过配置调用 YOLO、MMDet3D 和 Dify agent
- `services/dify` 存放 Dify 工作流导出文件，用于在 Dify Console 中导入或同步工作流配置
- `services/yolo-http` 负责图像目标检测
- `services/mmdet3d` 负责 KITTI 点云 3D 检测与可视化

当前默认配置：

- `/api` -> `http://localhost:8080`
- `detection.yolo-base-url=http://127.0.0.1:9000`
- `detection.mmdet3d-base-url=http://127.0.0.1:8000`

### 运行环境

建议环境：

- Windows PowerShell
- JDK 17 或 21
- Maven 3.9+
- Node.js 18+
- Python 3.10 / 3.11：用于 `services/yolo-http`
- Python 3.7 且可用 `torch`：用于 `services/mmdet3d`
- MySQL：用于 `backend`

### 启动前准备

#### 1. 数据库配置

后端默认连接：

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/VUE?useSSL=false&serverTimezone=UTC&characterEncoding=UTF-8
spring.datasource.username=root
spring.datasource.password=***
```

请根据本机环境修改：

- `backend/src/main/resources/application.properties`
- `backend/src/main/resources/application-local.properties`

如果不想把本机敏感配置提交进仓库，建议改用环境变量。

#### 2. 模型文件

YOLO 模型加载顺序：

- `MODEL_PATH` 环境变量
- `services/yolo-http/best.pt`
- `services/yolo-http/yolo26m.pt`

MMDet3D 默认使用：

- 权重：`services/mmdet3d/model/best.pth`
- 配置：`services/mmdet3d/model/config.py`

如果本地存在 KITTI 数据集，会优先读取：

- `services/mmdet3d/data/kitti`

如果不存在，会回退尝试：

- `F:/YOLO/kitty`

#### 3. 依赖安装

前端：

```powershell
cd .\frontend
npm install
```

后端：

```powershell
cd .\backend
mvn -DskipTests package
```

YOLO 服务：

```powershell
cd .\services\yolo-http
pip install -r requirements.txt
```

MMDet3D 服务请按你本机已安装的 MMDetection3D / PyTorch 环境准备，仓库中已包含：

- `services/mmdet3d/requirements.txt`
- `services/mmdet3d/run.ps1`

#### 4. Dify 工作流文件

仓库当前包含一份 Dify 工作流导出文件：

- `services/dify/Drone Decision Pipeline DeepSeek.yml`

这份文件不是本地 Python/Java 服务，也不会被 `start-all.ps1` 直接启动。
它的用途是作为 Dify Console 中的工作流定义文件，便于你在 Dify 平台导入、复用或版本管理当前巡检决策流程。

#### 5. 本地环境变量

推荐先复制模板：

```powershell
Copy-Item .\.env.local.example .\.env.local
```

示例：

```dotenv
DB_USERNAME=root
DB_PASSWORD=你的数据库密码
DIFY_BASE_URL=http://localhost/v1
DIFY_API_KEY=你的Dify应用APIKey
DIFY_WORKFLOW_ID=你的WorkflowId
DIFY_WORKFLOW_USER_PREFIX=drone-demo
KITTI_DATASET_ROOT=F:/YOLO/kitty
```

`start-all.ps1` 会自动读取 `.env.local` 并注入当前启动流程。

### 一键启动

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\start-all.ps1
```

脚本会尝试完成：

- 启动 Spring Boot 后端
- 启动 Vue 前端开发服务器
- 启动 YOLO FastAPI 服务
- 启动 MMDet3D FastAPI 服务
- 检查服务健康状态
- 如果存在 `.env.local`，自动加载其中变量

### 停止服务

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\stop-all.ps1
```

### 查看状态

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\status-all.ps1
```

### 单独运行模块

前端：

```powershell
cd .\frontend
npm run dev -- --host
```

后端：

```powershell
cd .\backend
mvn spring-boot:run
```

如果不是通过 `start-all.ps1` 启动，而是手动启动后端，请先在当前 PowerShell 会话里设置变量：

```powershell
$env:DB_USERNAME="root"
$env:DB_PASSWORD="你的数据库密码"
$env:DIFY_API_KEY="你的Dify应用APIKey"
$env:DIFY_WORKFLOW_ID="你的WorkflowId"
$env:DIFY_WORKFLOW_USER_PREFIX="drone-demo"
$env:KITTI_DATASET_ROOT="F:/YOLO/kitty"
```

YOLO 服务：

```powershell
cd .\services\yolo-http
python app.py
```

健康检查：

```text
GET http://127.0.0.1:9000/health
```

MMDet3D 服务：

```powershell
cd .\services\mmdet3d
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000
```

健康检查：

```text
GET http://127.0.0.1:8000/health
```

### 常用接口

YOLO 服务：

- `GET /health`
- `POST /predict`
- `POST /predict_url`
- `POST /predict_base64`

MMDet3D 服务：

- `GET /health`
- `POST /predict`

其中 `MMDet3D /predict` 支持：

- 必传：`point_cloud_file`
- 可选：`image_file`
- 可选：`calib_file`
- 可选：`score_thr`

### Dify 调用方式

当前项目不是前端直接调用 Dify，也不是走 Dify 的 OpenAI 兼容接口。
实际链路是：前端调用本项目后端接口，后端再使用 Dify Console 中该应用生成的 API Key 去调用 Dify Service API。

另外，仓库中的 `services/dify/Drone Decision Pipeline DeepSeek.yml` 对应的是当前 Dify 工作流配置文件，可用于在 Dify Console 中导入同一套 workflow 定义。

后端当前实现位置：

- 配置类：`backend/src/main/java/org/soft/softrear/config/DifyProperties.java`
- 调用实现：`backend/src/main/java/org/soft/softrear/service/dify/DifyWorkflowService.java`

当前实际调用逻辑：

- 健康探测：`GET {DIFY_BASE_URL}/info`
- 工作流执行：`POST {DIFY_BASE_URL}/workflows/{DIFY_WORKFLOW_ID}/run`
- 认证方式：`Authorization: Bearer {DIFY_API_KEY}`
- 请求模式：`response_mode=blocking`

当前环境变量含义：

- `DIFY_BASE_URL`：Dify Service API 基础地址，默认是 `http://localhost/v1`
- `DIFY_API_KEY`：Dify Console 里当前应用生成的 API Key
- `DIFY_WORKFLOW_ID`：当前工作流应用的 workflow id
- `DIFY_WORKFLOW_USER_PREFIX`：拼接请求用户标识的前缀

前端和后端各自承担的角色：

- 前端调用本地后端接口：`/external/dify/status`、`/external/dify/chat`、`/external/dify/drone-pipeline`
- 后端负责组装参数、附加 Bearer Token，并请求 Dify Service API

因此当前这套方式更准确的描述是：

- API Key 来自 Dify Console 中当前应用
- 真正对 Dify 发请求的是本项目后端，不是前端
- 后端调用的是 Dify Service API 下的 workflow 相关接口
- 不是 OpenAI Responses API
- 也不是 Dify 的 OpenAI-compatible endpoint

### Agent Service 抽象更新

后端已经把 Dify 的直接依赖抽成可替换的 agent service。

已完成的改造：

- 新增通用接口：`backend/src/main/java/org/soft/softrear/service/agent/AgentDecisionService.java`
- 当前 Dify 实现改为实现该接口：`backend/src/main/java/org/soft/softrear/service/dify/DifyWorkflowService.java`
- `DetectionPipelineService` 与 `ExternalServiceController` 改为依赖 `AgentDecisionService`
- 新增通用接口：
- `GET /external/agent/status`
- `POST /external/agent/chat`
- 保留兼容接口：
- `GET /external/dify/status`
- `POST /external/dify/chat`
- 返回中新增 `agent` 字段，同时保留 `dify` 字段兼容旧前端

如果后续要替换成其它 agent，推荐做法是：

1. 新增一个实现 `AgentDecisionService` 的 provider 类
2. 实现 `runDecision`、`probeStatus`、`getProviderName`
3. 把新 provider 的结果映射到当前兼容的 `DifyDecisionResult`
4. 在 Spring 中切换为新的实现 bean
5. 如果需要，新增独立的配置类和环境变量

这样做的好处是：

- 检测主链路不再依赖 Dify 的具体实现
- 替换 agent 时不需要重写控制器
- 旧前端还能继续吃 `dify` 字段
- 新前端可以逐步迁移到 `agent` 命名

### 日志与运行文件

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

### 注意事项

- `start-all.ps1` 仍包含一些本机路径候选，换机器运行前建议先检查
- 推荐把真实本机配置写在 `.env.local`，不要提交敏感值
- `.env.local.example` 只用于分发模板
- `services/mmdet3d/third_party/mmdetection3d` 体积较大，如需精简仓库，建议改为子模块或外部依赖管理

### 推荐开发流程

1. 确认 MySQL、Python、Node、JDK 都可用
2. 检查模型文件和数据集路径
3. 执行 `start-all.ps1`
4. 打开 `http://127.0.0.1:3000`
5. 若启动失败，优先查看 `.run/` 下日志

## English

This is a multi-module project that combines a Vue 3 frontend, a Spring Boot backend, a YOLO image detection service, and an MMDet3D point-cloud inference service. It is designed for local integration and demo workflows on Windows + PowerShell.

### Project Structure

```text
Ending_TASK/
├─ backend/                   Spring Boot backend
├─ frontend/                  Vue 3 + Vite frontend
├─ services/
│  ├─ yolo-http/              YOLO FastAPI service
│  └─ mmdet3d/                MMDet3D FastAPI service
├─ .run/                      runtime logs and process metadata
├─ start-all.ps1              start all services
├─ stop-all.ps1               stop all services
├─ status-all.ps1             check service status
├─ README.md
└─ .gitignore
```

### Tech Stack

- Frontend: Vue 3, Vue Router, Vite, Axios
- Backend: Spring Boot 3, Spring Web, Spring Data JPA, MySQL
- Python services:
- `services/yolo-http`: FastAPI + Ultralytics YOLO
- `services/mmdet3d`: FastAPI + MMDetection3D / PointPillars

### Service Ports

- Frontend: `http://127.0.0.1:3000`
- Java backend: `http://127.0.0.1:8080`
- YOLO service: `http://127.0.0.1:9000`
- MMDet3D service: `http://127.0.0.1:8000`

### Setup

Recommended environment:

- Windows PowerShell
- JDK 17 or 21
- Maven 3.9+
- Node.js 18+
- Python 3.10 / 3.11 for `services/yolo-http`
- Python 3.7 with `torch` for `services/mmdet3d`
- MySQL for `backend`

### Install Dependencies

Frontend:

```powershell
cd .\frontend
npm install
```

Backend:

```powershell
cd .\backend
mvn -DskipTests package
```

YOLO service:

```powershell
cd .\services\yolo-http
pip install -r requirements.txt
```

### Local Environment Variables

```dotenv
DB_USERNAME=root
DB_PASSWORD=your_db_password
DIFY_BASE_URL=http://localhost/v1
DIFY_API_KEY=your_dify_app_api_key
DIFY_WORKFLOW_ID=your_workflow_id
DIFY_WORKFLOW_USER_PREFIX=drone-demo
KITTI_DATASET_ROOT=F:/YOLO/kitty
```

### Run All

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\start-all.ps1
```

### Dify Integration Method

This project does not call Dify directly from the frontend, and it does not use Dify's OpenAI-compatible API.
The actual flow is: the frontend calls this project's backend endpoints, and the backend uses the API key generated from the Dify Console application to call the Dify Service API.

Current backend behavior:

- Health probe: `GET {DIFY_BASE_URL}/info`
- Workflow run: `POST {DIFY_BASE_URL}/workflows/{DIFY_WORKFLOW_ID}/run`
- Auth: `Authorization: Bearer {DIFY_API_KEY}`
- Mode: `response_mode=blocking`

Frontend and backend responsibilities:

- the frontend calls local backend endpoints: `/external/dify/status`, `/external/dify/chat`, `/external/dify/drone-pipeline`
- the backend assembles inputs, attaches the Bearer token, and calls the Dify Service API

So the current setup should be understood as:

- the API key comes from the Dify Console application
- the backend, not the frontend, sends requests to Dify
- the backend uses workflow-related endpoints in the Dify Service API
- it is not using the OpenAI Responses API
- it is not using Dify's OpenAI-compatible endpoint

The repository also includes a workflow export file:

- `services/dify/Drone Decision Pipeline DeepSeek.yml`

This file is not a local runtime service. It is the Dify workflow definition used for import or synchronization inside Dify Console.

### Agent Service Abstraction Update

The backend no longer lets the controller and detection pipeline depend directly on Dify-specific code.

What changed:

- Added a common interface: `backend/src/main/java/org/soft/softrear/service/agent/AgentDecisionService.java`
- Moved the current Dify integration behind that interface in `backend/src/main/java/org/soft/softrear/service/dify/DifyWorkflowService.java`
- Updated `DetectionPipelineService` and `ExternalServiceController` to depend on `AgentDecisionService`
- Added generic endpoints:
- `GET /external/agent/status`
- `POST /external/agent/chat`
- Kept compatibility endpoints:
- `GET /external/dify/status`
- `POST /external/dify/chat`
- Added a generic `agent` field while preserving the original `dify` field

Recommended replacement path:

1. Create a new provider class that implements `AgentDecisionService`
2. Implement `runDecision`, `probeStatus`, and `getProviderName`
3. Map the new provider response into the current `DifyDecisionResult` compatibility shape
4. Switch the active Spring bean
5. Add a separate properties class and env vars if needed

### Recommended Flow

1. Make sure MySQL, Python, Node.js, and JDK are available
2. Verify model files and dataset paths
3. Run `start-all.ps1`
4. Open `http://127.0.0.1:3000`
5. Check `.run/` logs if startup fails

