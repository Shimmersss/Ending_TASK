import axios from 'axios'
import api from '../utils/axios'

const yoloApi = axios.create({
  baseURL: import.meta.env.VITE_YOLO_API_BASE || 'http://127.0.0.1:9000',
  timeout: 120000
})

const mmdet3dApi = axios.create({
  baseURL: import.meta.env.VITE_MMDET3D_API_BASE || '/mmdet3d',
  timeout: 120000
})

export const checkYoloHealth = () => yoloApi.get('/health')
export const checkMmdet3dHealth = () => mmdet3dApi.get('/health')
export const checkDifyStatus = () => api.get('/external/dify/status')

export const uploadImage = (file, options = {}) => {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('conf', String(options.conf ?? 0.25))
  formData.append('iou', String(options.iou ?? 0.45))
  return yoloApi.post('/predict', formData)
}

export const uploadPointCloud = (file, options = {}) => {
  const formData = new FormData()
  formData.append('point_cloud_file', file)
  if (options.scoreThr !== undefined) {
    formData.append('score_thr', String(options.scoreThr))
  }
  return mmdet3dApi.post('/predict', formData)
}

export const uploadPointCloudWithImage = (pointCloudFile, imageFile, calibFile, options = {}) => {
  const formData = new FormData()
  formData.append('point_cloud_file', pointCloudFile)
  if (imageFile) {
    formData.append('image_file', imageFile)
  }
  if (calibFile) {
    formData.append('calib_file', calibFile)
  }
  formData.append('score_thr', String(options.scoreThr ?? 0.3))
  return mmdet3dApi.post('/predict', formData)
}

export const runDronePipeline = ({ mediaType, file, imageFile, calibFile, imagePathHint, conf, iou, scoreThr, missionContext, droneId, includeDify = true }) => {
  const formData = new FormData()
  formData.append('mediaType', mediaType)
  formData.append('file', file)
  if (imageFile) {
    formData.append('imageFile', imageFile)
  }
  if (calibFile) {
    formData.append('calibFile', calibFile)
  }
  if (imagePathHint) {
    formData.append('imagePathHint', imagePathHint)
  }
  if (conf !== undefined) {
    formData.append('conf', String(conf))
  }
  if (iou !== undefined) {
    formData.append('iou', String(iou))
  }
  if (scoreThr !== undefined) {
    formData.append('scoreThr', String(scoreThr))
  }
  formData.append('missionContext', missionContext || '')
  formData.append('droneId', droneId || 'demo-drone-001')
  formData.append('includeDify', String(includeDify))
  return api.post('/external/dify/drone-pipeline', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    },
    timeout: 180000
  })
}
