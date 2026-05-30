// src/router/index.js
import { createRouter, createWebHistory } from 'vue-router'

// 1. 引入你和队友的页面组件
import QaPage from '../components/QaPage.vue' // 假设这是你队友写的首页
import UserAuth from '../components/UserAuth.vue' // 你写的登录注册页

const routes = [
    {
        path: '/',         // 默认访问根路径
        name: 'QaPage',
        component: QaPage
    },
    {
        path: '/login',    // 访问 /login
        name: 'UserAuth',
        component: UserAuth
    },
    {
        path: '/:pathMatch(.*)*',
        redirect: '/login'
    }
]

const router = createRouter({
    history: createWebHistory(),
    routes
})

export default router
router.beforeEach((to, from, next) => {
    const token = localStorage.getItem('token')

    // 如果去的目标页面刚好就是登录页 '/login'，必须无条件直接放行！
    if (to.path === '/login') {
        next()
    }
    // 如果去的是其他页面（比如主页 '/'）且没有登录，才拦截它去登录页
    else if (!token) {
        next('/login')
    }
    // 其他情况（已登录，去主页等），直接放行
    else {
        next()
    }
})
