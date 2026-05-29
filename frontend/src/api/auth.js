// src/api/auth.js
import request from '@/utils/request' // 引入刚才创建的 request

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