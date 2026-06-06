// src/api/auth.js
import request from '@/utils/request.js' // 引入刚才创建的 request

// 登录接口
export function loginAPI(data) {
    return request({
        url: '/auth/login', // 因为前面 baseURL 加了 /api，这里只需要写后面的部分
        method: 'post',
        data // 包含 username 和 password
    })
}

// 注册接口
export function registerAPI(data) {
    return request({
        url: '/auth/register',
        method: 'post',
        data // 包含 username、password 等
    })
}

// 更新用户信息接口
export function updateUserProfile(data) {
    return request({
        url: '/auth/profile', // 剥离掉 /api/user，与后端精准对齐
        method: 'put',
        data: data
    })
}

// 获取当前登录用户信息的接口（GET 请求）
export function getUserProfile() {
    return request({
        url: '/auth/profile',
        method: 'get'
    })
}