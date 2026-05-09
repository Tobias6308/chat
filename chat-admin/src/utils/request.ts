import axios from 'axios'

const request = axios.create({
  baseURL: '/api'
})

request.interceptors.request.use(config => {
  const token = sessionStorage.getItem('admin_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

request.interceptors.response.use(
  response => response.data,
  error => {
    console.error('API Error:', error.response?.data || error.message)
    if (error.response?.status === 401) {
      sessionStorage.removeItem('admin_token')
      window.location.href = '/login'
    }
    return Promise.reject(error)
  }
)

export default request