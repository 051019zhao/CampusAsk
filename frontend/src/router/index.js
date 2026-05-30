// src/router/index.js
import { createRouter, createWebHistory } from 'vue-router'

// 1. 引入你和队友的页面组件
import QaPage from '../components/QaPage.vue' // 假设这是你队友写的首页
import UserAuth from '../components/UserAuth.vue' // 你写的登录注册页
import ContributePage from '../components/ContributePage.vue'

const routes = [
    {
        path: '/',         // 默认访问根路径
        name: 'QaPage',
        component: QaPage
    },
    {
        path: '/contribute',
        name: 'ContributePage',
        component: ContributePage
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