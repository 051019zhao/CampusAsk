// src/utils/request.js
import axios from 'axios'

axios.defaults.baseURL = ''  // 使用相对路径

// 请求拦截器：自动携带 Token
axios.interceptors.request.use(
    config => {
        // 从 localStorage 获取 Token
        const token = localStorage.getItem('token')
        if (token) {
            // 将 Token 添加到请求头
            config.headers['token'] = token
        }
        return config
    },
    error => {
        return Promise.reject(error)
    }
)

// 响应拦截器
axios.interceptors.response.use(
    response => {
        return response.data // 直接返回后端的数据包
    },
    error => {
        // 处理 Token 过期或未授权的情况
        if (error.response?.status === 401) {
            alert('登录已过期，请重新登录')
            // 跳转到登录页
            window.location.href = '/login'
        } else {
            alert(error.response?.data?.message || '网络请求错误')
        }
        return Promise.reject(error)
    }
)

export default axios