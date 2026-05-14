import { ref } from 'vue'

export const latestPipelineResult = ref(null)

export const setLatestPipelineResult = (result) => {
  latestPipelineResult.value = result
  window.dispatchEvent(new CustomEvent('dify-pipeline-result', { detail: result }))
}
