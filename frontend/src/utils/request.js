// src/utils/request.js
import axios from 'axios'

const request = axios.create({
    // baseURI 会自动加在所有请求路径的前面
    baseURL: '/api',
    timeout: 5000 // 超时时间
})

// 响应拦截器
request.interceptors.response.use(
    response => {
        return response.data // 直接返回后端的数据包
    },
    error => {
        alert(error.response?.data?.message || '网络请求错误')
        return Promise.reject(error)
    }
)

export default request