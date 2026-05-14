import api from '../utils/axios'

export const chatWithModel = (data) => {
  return api.post('/model/chat', data)
}

export const generateContent = (data) => {
  return api.post('/model/generate', data)
}

export const chatWithDify = (data) => {
  return api.post('/external/dify/chat', data)
}
