import axios from 'axios'

// 1. 创建一个名为 request 的自定义 Axios 实例（统一 baseURL）
const request = axios.create({
    baseURL: 'http://localhost:8080',
    timeout: 5000 // 建议加个 5 秒超时，防止请求死等
})

// 2. 核心修复：请求拦截器（将之前的 request 错误完美闭环）
request.interceptors.request.use(
    config => {
        // 从本地缓存中获取登录时存进去的 Token
        const token = localStorage.getItem('token')
        // request.js
        if (token) {
            config.headers['token'] = token; // 去掉 "Bearer " 前缀
        }

        return config
    },
    error => {
        return Promise.reject(error)
    }
)

// 3. 响应拦截器（无缝迁移你原本的全局弹窗逻辑）
request.interceptors.response.use(
    response => {
        const res = response.data; // 后端的 Result 对象

        // 1. 判断后端定义的成功状态码（假设 1 或 200 是成功，根据你们项目 Result.success() 的定义来）
        // 如果后端成功是 code == 1
        if (res.code === 1 || res.code === 200) {
            return res; // ★ 核心修复：直接返回里面的 data 核心数据！
        }

        // 2. 如果后端返回的是错误（比如未登录、无权限等）
        alert(res.message || '系统错误');
        return Promise.reject(new Error(res.message || 'Error'));
    },
    error => {
        // 捕获网络、403、500 等 HTTP 异常错误
        alert(error.response?.data?.message || '网络请求错误');
        return Promise.reject(error);
    }
)

// 4. 关键点：导出这个封装好的 request 实例，供你的 api/auth.js 使用
export default request