import api from '../utils/axios'

export const login = (data) => {
  return api.post('/user/login', data)
}

export const register = (data) => {
  return api.post('/user', data)
}
