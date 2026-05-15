<template>
  <div class="model-chat">
    <div v-if="summary" class="summary-card">
      <div class="summary-grid">
        <div class="summary-row">
          <span>状态</span>
          <strong :class="statusClass">{{ summary.dify?.status || '-' }}</strong>
        </div>
        <div class="summary-row">
          <span>校验</span>
          <strong>{{ validatedText }}</strong>
        </div>
        <div class="summary-row">
          <span>动作</span>
          <strong>{{ summary.dify?.command?.action || '-' }}</strong>
        </div>
        <div class="summary-row wide">
          <span>说明</span>
          <strong>{{ summary.dify?.reason || summary.dify?.error || '-' }}</strong>
        </div>
      </div>

      <details v-if="rawOutputsText" class="raw-panel">
        <summary>查看原始输出</summary>
        <pre>{{ rawOutputsText }}</pre>
      </details>
    </div>

    <div v-else class="empty-state">
      完成一次检测后，这里会自动同步 Dify 的工作流结果。
    </div>

    <form class="follow-up-card" @submit.prevent="sendFollowUp">
      <div class="follow-up-head">
        <div>
          <strong>继续追问巡检建议</strong>
          <p>把当前检测结果连同你的问题一起发给 Dify。</p>
        </div>
        <span class="chip muted">Follow-up</span>
      </div>

      <textarea
        v-model="followUpText"
        rows="3"
        class="follow-up-input"
        placeholder="例如：如果要继续巡检，下一步应该先检查什么？"
      ></textarea>

      <div class="follow-up-actions">
        <button class="btn btn-primary" type="submit" :disabled="!canFollowUp || followUpLoading">
          {{ followUpLoading ? '正在追问...' : '发送追问' }}
        </button>
        <button class="btn btn-ghost" type="button" @click="applyQuickPrompt">
          一键补全建议
        </button>
      </div>

      <p v-if="followUpError" class="follow-up-error">{{ followUpError }}</p>
    </form>

    <div class="chat-box">
      <div class="chat-bubble system">
        {{ promptText }}
      </div>
      <div v-if="summary && promptDetails" class="chat-bubble user">
        {{ promptDetails }}
      </div>
      <div v-for="(item, index) in followUpMessages" :key="index" class="chat-bubble" :class="item.role">
        <strong class="bubble-label">{{ item.role === 'user' ? '追问' : 'Dify 回复' }}</strong>
        <p>{{ item.text }}</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { chatWithDify } from '../api/model'
import { latestPipelineResult } from '../utils/difyPipelineState'

const summary = ref(latestPipelineResult.value)
const followUpText = ref('')
const followUpLoading = ref(false)
const followUpError = ref('')
const followUpMessages = ref([])

const promptText = computed(() => {
  if (!summary.value) return '等待下一次检测结果。'
  if (summary.value.dify?.status === 'pending') return '检测结果已返回，Dify 正在后台补充。'
  if (summary.value.dify?.status === 'failed') return 'Dify 调用失败，但检测结果仍然保留。'
  if (summary.value.dify?.validated === false) return '工作流已拒绝高风险动作。'
  return '可以基于当前结果继续追问巡检建议。'
})

const promptDetails = computed(() => {
  const reason = summary.value?.dify?.reason
  const action = summary.value?.dify?.command?.action
  return [reason ? `说明：${reason}` : '', action ? `动作：${action}` : ''].filter(Boolean).join('   ')
})

const validatedText = computed(() => {
  if (!summary.value?.dify) return '-'
  if (summary.value.dify.validated === true) return '通过'
  if (summary.value.dify.validated === false) return '未通过'
  return '-'
})

const statusClass = computed(() => {
  const status = summary.value?.dify?.status
  return {
    ok: status === 'succeeded',
    pending: status === 'pending',
    warn: status === 'failed',
    mute: !status || status === 'skipped'
  }
})

const rawOutputsText = computed(() => {
  const raw = summary.value?.dify?.rawOutputs
  return raw ? JSON.stringify(raw, null, 2) : ''
})

const canFollowUp = computed(() => !!summary.value && !!followUpText.value.trim())

const buildFollowUpPrompt = () => {
  const current = summary.value?.dify || {}
  const detection = summary.value?.detection || {}
  const question = followUpText.value.trim()
  const contextParts = [
    '请基于以下无人机巡检结果继续给出建议。',
    `检测状态：${current.status || '-'}`,
    `校验结果：${current.validated === true ? '通过' : current.validated === false ? '未通过' : '-'}`,
    `动作：${current.command?.action || '-'}`,
    `说明：${current.reason || current.error || '-'}`,
    `目标数量：${Array.isArray(detection.detections) ? detection.detections.length : 0}`,
    `追问：${question}`
  ]
  return contextParts.join('\n')
}

const applyQuickPrompt = () => {
  if (!summary.value) return
  followUpText.value = '请基于当前检测结果，给出下一步巡检建议和优先级排序。'
}

const sendFollowUp = async () => {
  if (!canFollowUp.value || followUpLoading.value) return
  try {
    followUpLoading.value = true
    followUpError.value = ''
    const userQuestion = followUpText.value.trim()
    followUpMessages.value.push({ role: 'user', text: userQuestion })
    const response = await chatWithDify({
      message: buildFollowUpPrompt(),
      userId: 'demo-user',
      droneId: summary.value?.droneId || 'demo-drone-001',
      mediaType: 'image'
    })
    const dify = response?.data?.dify || {}
    followUpMessages.value.push({
      role: 'assistant',
      text: dify.reason || dify.error || 'Dify 已收到追问，但未返回可读建议。'
    })
  } catch (err) {
    followUpError.value = err?.response?.data?.message || err.message || '追问发送失败'
  } finally {
    followUpLoading.value = false
  }
}

const syncSummary = (event) => {
  summary.value = event?.detail ?? latestPipelineResult.value
}

onMounted(() => {
  window.addEventListener('dify-pipeline-result', syncSummary)
})

onUnmounted(() => {
  window.removeEventListener('dify-pipeline-result', syncSummary)
})
</script>

<style scoped>
.model-chat {
  display: grid;
  gap: 14px;
}

.summary-card,
.empty-state,
.chat-bubble,
.raw-panel {
  border: 1px solid var(--line);
  border-radius: var(--radius-lg);
  background: rgba(255, 255, 255, 0.03);
  backdrop-filter: var(--blur);
  -webkit-backdrop-filter: var(--blur);
}

.summary-card {
  display: grid;
  gap: 12px;
  padding: 16px;
}

.summary-grid {
  display: grid;
  gap: 10px;
}

.summary-row {
  display: grid;
  grid-template-columns: 64px minmax(0, 1fr);
  gap: 12px;
  align-items: start;
}

.summary-row span {
  color: var(--text-muted);
}

.summary-row strong {
  min-width: 0;
  word-break: break-word;
  line-height: 1.5;
}

.summary-row strong.ok {
  color: #85ffe2;
}

.summary-row strong.pending {
  color: #8ecbff;
}

.summary-row strong.warn {
  color: #ffaaaa;
}

.summary-row strong.mute {
  color: var(--text-muted);
}

.raw-panel {
  padding: 12px 14px;
}

.raw-panel summary {
  cursor: pointer;
  color: var(--text-muted);
  font-weight: 700;
}

.raw-panel pre {
  margin: 10px 0 0;
  max-height: 240px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 12px;
  line-height: 1.6;
  color: var(--text-main);
}

.empty-state {
  padding: 14px;
  color: var(--text-muted);
}

.follow-up-card {
  display: grid;
  gap: 12px;
  padding: 16px;
  border: 1px solid var(--line);
  border-radius: var(--radius-lg);
  background: rgba(255, 255, 255, 0.04);
}

.follow-up-head {
  display: flex;
  align-items: start;
  justify-content: space-between;
  gap: 12px;
}

.follow-up-head strong {
  font-size: 15px;
}

.follow-up-head p {
  margin-top: 4px;
  color: var(--text-muted);
  font-size: 13px;
}

.follow-up-input {
  width: 100%;
  border: 1px solid rgba(255, 255, 255, 0.14);
  border-radius: var(--radius-md);
  padding: 12px 14px;
  background: rgba(5, 12, 20, 0.34);
  color: var(--text-main);
  resize: vertical;
}

.follow-up-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.follow-up-error {
  color: #ffb8b8;
  font-size: 13px;
}

.chat-box {
  display: grid;
  gap: 10px;
}

.chat-bubble {
  padding: 14px;
  line-height: 1.6;
}

.chat-bubble.system {
  background: rgba(66, 233, 208, 0.06);
}

.chat-bubble.user {
  background: rgba(109, 178, 255, 0.06);
}

.chat-bubble p {
  margin-top: 4px;
}

.bubble-label {
  display: inline-block;
  margin-bottom: 6px;
  color: var(--text-muted);
  font-size: 12px;
}

@media (max-width: 760px) {
  .summary-row {
    grid-template-columns: 52px minmax(0, 1fr);
  }
}
</style>
