<template>
  <div class="image-upload">
    <div class="upload-head">
      <div>
        <p class="subtitle">{{ subtitleText }}</p>
        <p class="service" :class="{ online: serviceOnline, offline: serviceOnline === false }">
          <span></span>{{ serviceText }}
        </p>
        <p class="service" :class="{ online: difyReady, offline: difyReady === false }">
          <span></span>{{ difyStatusText }}
        </p>
      </div>
      <button class="icon-btn" type="button" title="刷新服务状态" @click="refreshServices">↻</button>
    </div>

    <div class="mode-row">
      <span class="mode-label">检测模式</span>
      <div class="mode-tabs">
        <button type="button" class="mode-tab" :class="{ active: mode === 'yolo' }" @click="mode = 'yolo'">
          YOLO 图片
        </button>
        <button type="button" class="mode-tab" :class="{ active: mode === 'mmdet3d' }" @click="mode = 'mmdet3d'">
          MMDet3D 点云
        </button>
      </div>
    </div>

    <div class="mission-grid">
      <label>
        无人机 ID
        <input v-model="droneId" type="text" placeholder="demo-drone-001">
      </label>
      <label>
        任务上下文
        <textarea v-model="missionContext" rows="3" placeholder="例如：巡检变电站围栏异常，优先保证安全距离"></textarea>
      </label>
    </div>

    <template v-if="isYolo">
      <label class="drop-zone" :class="{ dragging: isDraggingMain }" @dragover.prevent="isDraggingMain = true" @dragleave.prevent="isDraggingMain = false" @drop.prevent="handleMainDrop">
        <input type="file" accept="image/*" @change="handleMainFileChange">
        <span class="upload-icon">+</span>
        <strong>{{ mainFileName || '选择或拖入图片' }}</strong>
        <small>支持 JPG / PNG / WEBP</small>
      </label>

      <div class="control-row">
        <label>
          置信度
          <input v-model.number="conf" type="range" min="0.05" max="0.95" step="0.05">
          <span>{{ conf.toFixed(2) }}</span>
        </label>
        <label>
          IoU
          <input v-model.number="iou" type="range" min="0.10" max="0.90" step="0.05">
          <span>{{ iou.toFixed(2) }}</span>
        </label>
      </div>
    </template>

    <template v-else>
      <div class="mode-row dataset-row">
        <span class="mode-label">KITTI 数据集</span>
        <div class="mode-tabs">
          <button type="button" class="mode-tab" :class="{ active: kittiSplit === 'training' }" @click="setKittiSplit('training')">
            training
          </button>
          <button type="button" class="mode-tab" :class="{ active: kittiSplit === 'testing' }" @click="setKittiSplit('testing')">
            testing
          </button>
        </div>
      </div>

      <label class="drop-zone" :class="{ dragging: isDraggingImage }" @dragover.prevent="isDraggingImage = true" @dragleave.prevent="isDraggingImage = false" @drop.prevent="handleImageDrop">
        <input type="file" accept="image/*" @change="handleImageFileChange">
        <span class="upload-icon">+</span>
        <strong>{{ imageFileName || '选择左相机图片' }}</strong>
        <small>后端会按同名自动匹配点云和 calib</small>
      </label>

      <div class="control-row single">
        <label>
          置信度阈值
          <input v-model.number="scoreThr" type="range" min="0.05" max="0.95" step="0.05">
          <span>{{ scoreThr.toFixed(2) }}</span>
        </label>
      </div>
    </template>

    <div v-if="projectionHint" class="warning">{{ projectionHint }}</div>

    <button class="btn btn-primary run-btn" type="button" :disabled="!canRun || loading" @click="runDetection">
      {{ loading ? '检测与决策中...' : '开始检测并调用 Dify' }}
    </button>

    <div v-if="error" class="error">{{ error }}</div>

    <div v-if="previewImages.length" class="image-grid">
      <figure v-for="(item, index) in previewImages" :key="index" :class="item.className">
        <figcaption>{{ item.title }}</figcaption>
        <button type="button" class="image-button" @click="openViewer(item.src)">
          <img :src="item.src" :alt="item.title">
          <span class="zoom-badge">点击查看大图</span>
        </button>
      </figure>
    </div>

    <div v-if="hasResult" class="result-card">
      <button class="result-top" type="button" @click="showDetails = !showDetails">
        <strong>检测结果</strong>
        <span class="result-meta">{{ detections.length }} 项</span>
        <span class="toggle">{{ showDetails ? '收起' : '展开' }}</span>
      </button>

      <div v-if="showDetails" class="result-list">
        <div class="detection" v-for="(item, index) in detections" :key="index">
          <strong>{{ formatLabel(item, index) }}</strong>
          <span>{{ formatPercent(item.score || item.confidence || item.conf) }}</span>
          <small>{{ formatBoxLabel(item) }}: {{ formatBox(item.bbox_3d || item.bbox || item.box) }}</small>
        </div>
        <div v-if="!detections.length" class="empty-result">未检测到目标，可以尝试降低阈值后重试。</div>
      </div>
    </div>

    <div v-if="showDecisionWarning" class="decision-warning">
      <strong>AI 决策暂不可用</strong>
      <p>{{ decisionErrorText }}</p>
    </div>

    <div v-else-if="difyResult" class="decision-card">
      <div class="decision-head">
        <div>
          <strong>AI 决策 / 安全校验</strong>
          <p>{{ decisionSubtitle }}</p>
        </div>
        <span class="decision-status">{{ difyResult.status || 'unknown' }}</span>
      </div>
      <div class="decision-grid">
        <span>校验</span>
        <strong>{{ difyResult.validated === true ? '通过' : difyResult.validated === false ? '未通过' : '未返回' }}</strong>
        <span>动作</span>
        <strong>{{ commandValue('action') || '-' }}</strong>
        <span>速度</span>
        <strong>{{ commandValue('speed_mps') ?? '-' }}</strong>
        <span>目标坐标</span>
        <strong>{{ formatTarget(commandValue('target_xyz')) }}</strong>
      </div>
      <p v-if="difyResult.reason" class="decision-reason">{{ difyResult.reason }}</p>
      <p v-if="difyResult.error" class="decision-error">{{ difyResult.error }}</p>
      <small v-if="difyResult.workflowRunId">workflow_run_id: {{ difyResult.workflowRunId }}</small>
    </div>

    <div v-if="viewerOpen" class="viewer" @click.self="viewerOpen = false">
      <div class="viewer-panel">
        <button class="viewer-close" type="button" @click="viewerOpen = false">×</button>
        <img :src="viewerSrc" alt="检测结果大图">
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { checkDifyStatus, checkMmdet3dHealth, checkYoloHealth, runDronePipeline } from '../api/image'
import { setLatestPipelineResult } from '../utils/difyPipelineState'

const mode = ref('yolo')
const conf = ref(0.25)
const iou = ref(0.45)
const scoreThr = ref(0.3)
const kittiSplit = ref('training')
const missionContext = ref('')
const droneId = ref('demo-drone-001')
const loading = ref(false)
const error = ref('')
const serviceOnline = ref(null)
const serviceDevice = ref('')
const difyReady = ref(null)
const difyStatus = ref(null)
const hasResult = ref(false)
const showDetails = ref(false)
const viewerOpen = ref(false)
const viewerSrc = ref('')
const detections = ref([])
const difyResult = ref(null)

const mainFile = ref(null)
const imageFile = ref(null)
const imagePathHint = ref('')
const previewImages = ref([])

const isDraggingMain = ref(false)
const isDraggingImage = ref(false)

const isYolo = computed(() => mode.value === 'yolo')
const mainFileName = computed(() => (mainFile.value ? mainFile.value.name : ''))
const imageFileName = computed(() => (imageFile.value ? imageFile.value.name : ''))
const canRun = computed(() => {
  if (isYolo.value) {
    return !!mainFile.value
  }
  return !!imageFile.value
})
const serviceLabel = computed(() => (isYolo.value ? 'YOLO' : 'MMDet3D'))
const serviceText = computed(() => {
  if (serviceOnline.value === true) {
    return serviceLabel.value + ' 服务已连接' + (serviceDevice.value ? ' · ' + serviceDevice.value : '')
  }
  if (serviceOnline.value === false) {
    return serviceLabel.value + ' 服务未连接'
  }
  return '正在检查 ' + serviceLabel.value + ' 服务'
})
const difyStatusText = computed(() => {
  if (difyReady.value === true) return 'Dify workflow ready'
  if (difyReady.value === false) return 'Dify offline, decision skipped'
  return 'Checking Dify workflow'
})
const subtitleText = computed(() => {
  return isYolo.value
    ? '后端统一调用 YOLO 检测，并把结果交给 Dify 工作流生成决策'
    : '后端统一调用 MMDet3D 检测，并把点云结果交给 Dify 工作流生成决策'
})
const decisionSubtitle = computed(() => {
  if (!difyResult.value) return ''
  if (difyResult.value.status === 'skipped') return difyResult.value.reason || 'Dify 未配置，已跳过'
  if (difyResult.value.status === 'failed') return 'Dify 调用失败，检测结果仍然保留'
  return difyResult.value.validated === false ? '工作流已拒绝高风险指令' : '工作流已返回安全校验结果'
})
const showDecisionWarning = computed(() => difyResult.value && difyResult.value.status === 'failed')
const decisionErrorText = computed(() => {
  if (!difyResult.value) return ''
  return difyResult.value.error || 'Dify 调用失败，检测结果仍然保留'
})
const projectionHint = computed(() => {
  if (!isYolo.value && !imageFile.value) {
    return '请选择左相机图片，后端会按文件名自动匹配同名点云和 calib。'
  }
  return ''
})

const resetResult = () => {
  detections.value = []
  previewImages.value = []
  difyResult.value = null
  hasResult.value = false
  showDetails.value = false
  error.value = ''
  viewerOpen.value = false
  viewerSrc.value = ''
  setLatestPipelineResult(null)
}

const resetFiles = () => {
  mainFile.value = null
  imageFile.value = null
  imagePathHint.value = ''
  isDraggingMain.value = false
  isDraggingImage.value = false
}

const setMainFile = (file) => {
  if (!file) return
  if (!file.type.startsWith('image/')) {
    error.value = '请选择图片文件'
    return
  }
  mainFile.value = file
  resetResult()
}

const setImageFile = (file) => {
  if (!file) return
  if (!file.type.startsWith('image/')) {
    error.value = '请选择图片文件'
    return
  }
  imageFile.value = file
  imagePathHint.value = buildImagePathHint(file)
  resetResult()
}

const buildImagePathHint = (file) => {
  const relativePath = file.webkitRelativePath || ''
  if (relativePath.includes('/')) {
    return relativePath
  }
  return `${kittiSplit.value}/image_2/${file.name || ''}`
}

const setKittiSplit = (split) => {
  kittiSplit.value = split
  if (imageFile.value) {
    imagePathHint.value = buildImagePathHint(imageFile.value)
  }
  resetResult()
}

const handleMainFileChange = (event) => {
  setMainFile(event.target.files && event.target.files[0])
  event.target.value = ''
}
const handleImageFileChange = (event) => {
  setImageFile(event.target.files && event.target.files[0])
  event.target.value = ''
}
const handleMainDrop = (event) => {
  isDraggingMain.value = false
  setMainFile(event.dataTransfer.files && event.dataTransfer.files[0])
}
const handleImageDrop = (event) => {
  isDraggingImage.value = false
  setImageFile(event.dataTransfer.files && event.dataTransfer.files[0])
}

const loadHealth = async () => {
  try {
    serviceOnline.value = null
    serviceDevice.value = ''
    const response = isYolo.value ? await checkYoloHealth() : await checkMmdet3dHealth()
    const data = response?.data || {}
    serviceOnline.value = data.status === 'ok'
    if (data.device) {
      serviceDevice.value = 'device: ' + data.device
    }
  } catch {
    serviceOnline.value = false
  }
}

const loadDifyStatus = async () => {
  try {
    difyReady.value = null
    const response = await checkDifyStatus()
    const data = response?.data || {}
    difyStatus.value = data
    difyReady.value = data.online === true && data.ready === true
  } catch {
    difyStatus.value = null
    difyReady.value = false
  }
}

const refreshServices = () => {
  loadHealth()
  loadDifyStatus()
}

const runDetection = async () => {
  if (!canRun.value) return
  try {
    loading.value = true
    error.value = ''
    previewImages.value = []
    detections.value = []
    difyResult.value = null
    hasResult.value = false
    showDetails.value = false

    const response = await runDronePipeline({
      mediaType: isYolo.value ? 'image' : 'pointcloud',
      file: isYolo.value ? mainFile.value : imageFile.value,
      imageFile: null,
      calibFile: null,
      imagePathHint: isYolo.value ? '' : imagePathHint.value,
      conf: conf.value,
      iou: iou.value,
      scoreThr: scoreThr.value,
      missionContext: missionContext.value,
      droneId: droneId.value
    })

    const data = response?.data || {}
    const detection = data.detection || {}
    difyResult.value = data.dify || null
    detections.value = Array.isArray(detection.detections) ? detection.detections : []
    previewImages.value = buildPreviewImages(detection)
    serviceOnline.value = true
    hasResult.value = true
    setLatestPipelineResult(data)
  } catch (err) {
    const detail = err && err.response && err.response.data && (err.response.data.message || err.response.data.detail)
    error.value = detail || err.message || '检测失败，请确认后端、检测服务和 Dify 配置'
  } finally {
    loading.value = false
  }
}

const buildPreviewImages = (detection) => {
  const images = []
  if (detection.annotated_image) {
    images.push({ title: 'YOLO 检测结果', src: detection.annotated_image, className: 'result-figure' })
  }
  if (detection.pointcloud_visualization) {
    images.push({ title: '点云检测可视化', src: detection.pointcloud_visualization, className: 'result-figure' })
  }
  if (detection.image_visualization) {
    images.push({ title: '左相机投影可视化', src: detection.image_visualization, className: 'result-figure' })
  }
  return images
}

const openViewer = (src) => {
  viewerSrc.value = src
  viewerOpen.value = true
}
const commandValue = (key) => difyResult.value && difyResult.value.command ? difyResult.value.command[key] : null
const formatTarget = (value) => Array.isArray(value) ? value.join(', ') : '-'
const formatPercent = (value) => {
  if (value === undefined || value === null || Number.isNaN(Number(value))) return ''
  return (Number(value) * 100).toFixed(1) + '%'
}
const formatBox = (box) => Array.isArray(box) ? box.map((value) => Number(value).toFixed(2)).join(', ') : ''
const KITTI_LABELS = { 0: 'Pedestrian', 1: 'Cyclist', 2: 'Car' }
const fileStem = (name) => String(name || '').replace(/\.[^.]+$/, '')
const normalizeKittiLabel = (value) => {
  if (value === undefined || value === null) return ''
  const text = String(value).trim()
  if (/^\d+$/.test(text) && KITTI_LABELS[text] !== undefined) return KITTI_LABELS[text]
  const lower = text.toLowerCase()
  if (lower === 'car' || lower === 'pedestrian' || lower === 'cyclist') {
    return text.charAt(0).toUpperCase() + text.slice(1).toLowerCase()
  }
  return text
}
const formatLabel = (item, index) => normalizeKittiLabel(item.class_name || item.class || item.label) || '目标 ' + (index + 1)
const formatBoxLabel = () => 'bbox'

onMounted(() => {
  refreshServices()
})
onUnmounted(() => {
  viewerOpen.value = false
})
watch(mode, () => {
  resetFiles()
  resetResult()
  refreshServices()
})
</script>

<style scoped>
.image-upload {
  display: grid;
  gap: 16px;
}

.upload-head,
.result-top,
.decision-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.subtitle,
.decision-head p {
  color: var(--text-muted);
  margin-bottom: 6px;
}

.service {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  color: var(--text-muted);
  font-size: 13px;
}

.service span {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #9aa4b2;
}

.service.online span {
  background: #13a86b;
}

.service.offline span {
  background: #e5484d;
}

.icon-btn {
  width: 38px;
  height: 38px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: #fff;
  color: var(--brand);
  cursor: pointer;
  font-size: 20px;
}

.mode-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.mode-label {
  color: var(--text-muted);
  font-size: 13px;
  font-weight: 700;
}

.mode-tabs {
  display: inline-flex;
  gap: 8px;
  padding: 4px;
  border-radius: 999px;
  border: 1px solid var(--line);
  background: #fff;
}

.mode-tab {
  border: 0;
  padding: 6px 14px;
  border-radius: 999px;
  background: transparent;
  color: var(--text-muted);
  font-weight: 700;
  cursor: pointer;
}

.mode-tab.active {
  background: #e2f4f0;
  color: var(--brand);
}

.mission-grid {
  display: grid;
  grid-template-columns: minmax(180px, 0.8fr) minmax(240px, 1.2fr);
  gap: 12px;
}

.mission-grid label,
.control-row label {
  border: 1px solid var(--line);
  border-radius: 8px;
  padding: 12px;
  background: #fff;
  display: grid;
  gap: 8px;
  font-weight: 700;
}

.mission-grid input,
.mission-grid textarea {
  width: 100%;
  border: 1px solid #d7e0ec;
  border-radius: 8px;
  padding: 8px 10px;
  font: inherit;
  resize: vertical;
}

.drop-zone {
  position: relative;
  min-height: 170px;
  border: 1px dashed #9fb0c7;
  border-radius: 8px;
  background: #f8fbff;
  display: grid;
  place-items: center;
  align-content: center;
  gap: 8px;
  cursor: pointer;
  transition: border-color 180ms ease, background 180ms ease, transform 180ms ease;
  text-align: center;
}

.optional-zone {
  min-height: 150px;
}

.drop-zone.dragging,
.drop-zone:hover {
  border-color: var(--brand);
  background: #eef9f6;
  transform: translateY(-1px);
}

.drop-zone input {
  position: absolute;
  inset: 0;
  opacity: 0;
  cursor: pointer;
}

.upload-icon {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border-radius: 8px;
  background: #e2f4f0;
  color: var(--brand);
  font-size: 28px;
}

.drop-zone small,
.zoom-badge,
.decision-card small {
  color: var(--text-muted);
}

.control-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.control-row.single {
  grid-template-columns: minmax(0, 1fr);
}

.control-row label {
  grid-template-columns: auto 1fr auto;
  align-items: center;
}

.control-row input {
  width: 100%;
  accent-color: var(--brand);
}

.control-row span,
.result-meta {
  color: var(--brand);
  font-weight: 700;
}

.run-btn {
  width: 100%;
}

.error,
.empty-result,
.decision-error {
  border-radius: 8px;
  padding: 10px 12px;
}

.warning {
  border-radius: 8px;
  padding: 10px 12px;
  border: 1px solid #d9b45d;
  background: #fff9eb;
  color: #7a5b14;
}

.error,
.decision-error {
  color: #a11d21;
  background: #fff0f0;
  border: 1px solid #ffc9c9;
}

.image-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

figure,
.result-card,
.decision-card {
  border: 1px solid var(--line);
  border-radius: 8px;
  overflow: hidden;
  background: #fff;
}

figcaption {
  padding: 10px 12px;
  color: var(--text-muted);
  font-size: 13px;
  border-bottom: 1px solid var(--line);
}

figure img,
.viewer-panel img {
  width: 100%;
  height: 280px;
  object-fit: contain;
  display: block;
  background: #f4f7fb;
}

.result-figure {
  padding-bottom: 12px;
}

.image-button {
  width: 100%;
  border: 0;
  padding: 0;
  background: transparent;
  cursor: zoom-in;
  display: grid;
  gap: 8px;
}

.result-top {
  width: 100%;
  padding: 12px;
  border: 0;
  background: transparent;
  cursor: pointer;
}

.toggle {
  color: var(--text-muted);
  font-size: 13px;
}

.result-list {
  border-top: 1px solid var(--line);
}

.detection {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 4px 12px;
  padding: 10px 12px;
  border-top: 1px solid #eef2f7;
}

.detection:first-of-type {
  border-top: 0;
}

.detection small {
  grid-column: 1 / -1;
  color: var(--text-muted);
}

.decision-warning,
.decision-card {
  display: grid;
  gap: 12px;
  padding: 14px;
}

.decision-warning {
  border: 1px solid #ffc9c9;
  border-radius: 8px;
  background: #fff5f5;
}

.decision-warning p {
  color: #7a3035;
}

.decision-status {
  border-radius: 999px;
  border: 1px solid #d7e0ec;
  padding: 5px 10px;
  color: #40566f;
  font-weight: 700;
}

.decision-grid {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 8px 14px;
}

.decision-grid span {
  color: var(--text-muted);
}

.decision-reason {
  color: var(--text);
}

.viewer {
  position: fixed;
  inset: 0;
  background: rgba(9, 14, 25, 0.72);
  display: grid;
  place-items: center;
  z-index: 50;
  padding: 18px;
}

.viewer-panel {
  position: relative;
  width: min(96vw, 1100px);
  max-height: 92vh;
  background: #0f172a;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 28px 70px rgba(0, 0, 0, 0.35);
}

.viewer-panel img {
  height: auto;
  max-height: 92vh;
  object-fit: contain;
  background: #0f172a;
}

.viewer-close {
  position: absolute;
  top: 10px;
  right: 10px;
  width: 36px;
  height: 36px;
  border: 0;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.14);
  color: #fff;
  font-size: 24px;
  cursor: pointer;
  z-index: 1;
}

@media (max-width: 760px) {
  .control-row,
  .image-grid,
  .mission-grid {
    grid-template-columns: 1fr;
  }
}
</style>
