// src/router/index.js
import { createRouter, createWebHistory } from 'vue-router'

// 1. 引入你和队友的页面组件
import QaPage from '../components/QaPage.vue'
import UserAuth from '../components/UserAuth.vue'
import KnowledgeImport from '../components/KnowledgeImport.vue'

const routes = [
    {
        path: '/',
        name: 'QaPage',
        component: QaPage
    },
    {
        path: '/login',
        name: 'UserAuth',
        component: UserAuth
    },
    {
        path: '/admin/knowledge-import',
        name: 'KnowledgeImport',
        component: KnowledgeImport
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

router.beforeEach((to, from, next) => {
    const token = localStorage.getItem('token')
    if (to.path !== '/login' && !token) {
        next('/login')
    } else {
        next()
    }
})

export default router