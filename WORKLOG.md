# Worklog

## 2026-05-16

- 问题：README 和 WORKLOG 中文内容出现乱码，且 Dify 的调用方式说明不够准确，容易和“前端直连 Dify”“Dify OpenAI 兼容接口”混淆。
- 处理：重写 `README.md` 为干净的 UTF-8 双语文档，修复中文乱码，并同步更新中英文结构。
- 处理：重新审阅前后端与配置代码，确认实际链路是“前端调用本项目后端接口，后端再用 Dify Console 应用 API Key 调用 Dify Service API”。
- 处理：把当前实际调用方式补充到 README 中，包括 `GET /info` 探测、`POST /workflows/{workflowId}/run` 执行、`Bearer` 鉴权、`response_mode=blocking`，以及本地 `/external/dify/*` 只是后端代理入口这一点。
- 处理：补充 agent service 抽象说明，说明 Dify 只是当前 provider，实现上已通过 `AgentDecisionService` 做了解耦。
- 验证：文档内容已更新，中文可读，且调用说明与当前后端代码一致。

- 问题：后端的决策能力直接依赖 `DifyWorkflowService`，控制器和检测流水线都被 Dify 命名与实现细节绑定，后续替换成其它 agent 成本较高。
- 处理：新增 `backend/src/main/java/org/soft/softrear/service/agent/AgentDecisionService.java` 作为统一抽象，约定 `runDecision`、`probeStatus`、`getProviderName` 三个能力入口。
- 处理：将 `backend/src/main/java/org/soft/softrear/service/dify/DifyWorkflowService.java` 改为实现 `AgentDecisionService`，保留现有 Dify 调用逻辑作为默认 provider。
- 处理：将 `backend/src/main/java/org/soft/softrear/service/dify/DetectionPipelineService.java` 与 `backend/src/main/java/org/soft/softrear/controller/ExternalServiceController.java` 改成依赖 `AgentDecisionService`，去掉对具体 Dify 实现的直接耦合。
- 处理：新增通用接口 `GET /external/agent/status` 和 `POST /external/agent/chat`，同时保留 `GET /external/dify/status` 和 `POST /external/dify/chat` 作为兼容别名。
- 处理：更新 `backend/src/main/java/org/soft/softrear/pojo/dto/dify/DronePipelineResult.java`，新增通用 `agent` 字段，并继续保留 `dify` 字段，确保旧前端不需要同步改造也能运行。
- 处理：更新 README，补充 agent service 抽象、替换方法、兼容策略和后续迁移建议，方便后续接入其它 agent。
- 验证：在 `backend/` 下执行 `mvn.cmd -q -DskipTests compile` 编译通过，说明接口抽象和 Spring 注入关系正常。

## 2026-05-15

- 问题：首页背景图显示不明显，右上角还残留 Vue DevTools 角标。
- 处理：调整 `frontend/src/views/HomeView.vue` 的背景层级、透明度和遮罩，并移除 `vite-plugin-vue-devtools`。
- 验证：前端重新构建通过，背景图在主页可见，DevTools 角标消失。

- 问题：模型对话区只有静态提示，不能继续追问巡检建议。
- 处理：在 `frontend/src/components/ModelChat.vue` 增加追问输入框、发送按钮和消息列表，并接入 `POST /api/external/dify/chat`。
- 验证：构建通过，页面可以提交追问并展示返回内容。

- 问题：Dify 调用偶发超时，且 `media_url` 会触发长度校验失败。
- 处理：将 `DifyWorkflowService` 的读取超时放宽；后端统一把 `media_url` 收缩成短文本引用，避免超过 512 字符。
- 验证：本地确认 `media_url` 为文本输入且有长度限制，后端打包通过。

- 问题：YOLO / MMDet3D 检测和 Dify 串行返回时，用户要等待完整链路结束。
- 处理：将检测结果与 Dify 结果拆分为两阶段，前端先展示检测结果，再异步补充 Dify 结果，并增加 `pending` 状态提示。
- 验证：前后端构建通过，页面会先显示检测，再更新 Dify 结果。

- 额外记录：README 已同步更新为当前目录结构 `backend/`、`frontend/`，并补充最近几轮改动说明。

