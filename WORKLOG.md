# Worklog

## 2026-05-15

- 问题：首页背景图显示不明显，右上角还有 Vue DevTools 角标。
- 处理：调整 `frontend/src/views/HomeView.vue` 的背景层层级、透明度和遮罩；移除 `vite-plugin-vue-devtools`。
- 验证：前端重新构建通过，背景图在主页可见，DevTools 角标消失。

- 问题：模型对话区只有静态提示，不能真正继续追问巡检建议。
- 处理：在 `frontend/src/components/ModelChat.vue` 增加追问输入框、发送按钮和消息列表，并接入 `POST /api/external/dify/chat`。
- 验证：构建通过，页面可以提交追问并展示返回内容。

- 问题：Dify 调用偶发超时，且 `media_url` 会触发长度校验失败。
- 处理：将 `DifyWorkflowService` 的读取超时放宽；在后端统一把 `media_url` 收缩成短文本引用，避免超过 512 字符。
- 验证：本地 Dify 参数查询确认 `media_url` 为 `text-input` 且上限 512，后端打包通过。

- 问题：YOLO / MMDet3D 检测和 Dify 串行返回时，用户会一直等完整链路结束。
- 处理：将检测结果与 Dify 结果拆分为两阶段，前端先展示检测结果，再异步补充 Dify，增加 `pending` 状态提示。
- 验证：前后端构建通过，页面会先显示检测，再更新 Dify 结果。

- 额外记录：README 已同步更新为当前目录结构 `backend/`、`frontend/`，并补充了最近几轮改动说明。
