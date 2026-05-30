// src/utils/request.js
import axios from 'axios'

axios.defaults.baseURL = 'http://localhost:8080'

// 响应拦截器（直接挂载到全局 axios 上）
axios.interceptors.response.use(
    response => {
        return response.data // 直接返回后端的数据包
    },
    error => {
        alert(error.response?.data?.message || '网络请求错误')
        return Promise.reject(error)
    }
)

// 直接导出原生的 axios
export default axios