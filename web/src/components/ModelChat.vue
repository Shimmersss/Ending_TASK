<template>
  <div class="model-chat">
    <div class="chat-head">
      <div>
        <p class="subtitle">Dify 决策摘要</p>
        <p class="hint">这里展示最近一次检测后的工作流结果，并保留继续追问的入口。</p>
      </div>
    </div>

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
      先完成一次检测，这里会自动同步 Dify 的结果。
    </div>

    <div class="chat-box">
      <div class="chat-bubble system">
        {{ promptText }}
      </div>
      <div v-if="summary && promptDetails" class="chat-bubble user">
        {{ promptDetails }}
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { latestPipelineResult } from '../utils/difyPipelineState'

const summary = ref(latestPipelineResult.value)

const promptText = computed(() => {
  if (!summary.value) return '等待一次检测结果。'
  if (summary.value.dify?.status === 'failed') return 'Dify 调用失败，但检测结果仍然保留。'
  if (summary.value.dify?.validated === false) return '工作流拒绝了高风险动作。'
  return '可继续基于当前结果追问任务建议。'
})

const promptDetails = computed(() => {
  const reason = summary.value?.dify?.reason
  const action = summary.value?.dify?.command?.action
  if (!reason && !action) return ''
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
    warn: status === 'failed',
    mute: !status || status === 'skipped'
  }
})

const rawOutputsText = computed(() => {
  const raw = summary.value?.dify?.rawOutputs
  return raw ? JSON.stringify(raw, null, 2) : ''
})

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
  gap: 12px;
}

.subtitle {
  color: var(--text-muted);
  font-weight: 700;
}

.hint {
  color: var(--text-muted);
  font-size: 13px;
}

.summary-card,
.empty-state,
.chat-bubble,
.raw-panel {
  border: 1px solid var(--line);
  border-radius: 8px;
  background: #fff;
}

.summary-card {
  display: grid;
  gap: 12px;
  padding: 12px;
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

.summary-row.wide {
  grid-template-columns: 64px minmax(0, 1fr);
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
  color: #11694e;
}

.summary-row strong.warn {
  color: #9b2c2c;
}

.summary-row strong.mute {
  color: #40566f;
}

.raw-panel {
  padding: 10px 12px;
}

.raw-panel summary {
  cursor: pointer;
  color: var(--text-muted);
  font-weight: 700;
}

.raw-panel pre {
  margin: 10px 0 0;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 240px;
  overflow: auto;
  font-size: 12px;
  line-height: 1.6;
  color: #334155;
}

.empty-state {
  padding: 12px;
  color: var(--text-muted);
}

.chat-box {
  display: grid;
  gap: 10px;
}

.chat-bubble {
  padding: 12px;
  line-height: 1.5;
}

.chat-bubble.system {
  background: #f8fbff;
}

.chat-bubble.user {
  background: #eef9f6;
}
</style>
