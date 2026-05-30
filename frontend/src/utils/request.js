// src/utils/request.js
import axios from 'axios'

const request = axios.create({
    baseURL: '/api'
})

// 请求拦截器：自动带上 token
request.interceptors.request.use(config => {
    const token = localStorage.getItem('token')
    if (token) {
        config.headers['token'] = token
    }
    return config
})

// 响应拦截器
request.interceptors.response.use(
    response => {
        return response.data
    },
    error => {
        alert(error.response?.data?.message || '网络请求错误')
        return Promise.reject(error)
    }
)

export default request