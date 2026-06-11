// src/router/index.js
import { createRouter, createWebHistory } from 'vue-router'

// 1. 引入你和队友的页面组件
import UserCenter from '@/components/UserCenter.vue'
import QaPage from '../components/QaPage.vue' // 假设这是你队友写的首页
import UserAuth from '../components/UserAuth.vue' // 你写的登录注册页
import ContributePage from '../components/ContributePage.vue' // 你的贡献页
import Profile from '@/components/Profile.vue'
import TeaTalkPage from '@/components/TeaTalkPage.vue' // 茶话会页面
// 队友新增的后台管理组件
import AdminLayout from "../views/admin/AdminLayout.vue";
import KnowledgeImport from "../views/admin/KnowledgeImport.vue";
import ContributionReview from "../views/admin/ContributionReview.vue";
import AdminStats from "../views/admin/AdminStats.vue";
import FeedbackReview from "../views/admin/FeedbackReview.vue";

const routes = [
    {
        path: '/',         // 默认访问根路径
        name: 'QaPage',
        component: QaPage
    },
    {
        path: '/user-center',
        name: 'UserCenter',
        component: UserCenter
    },
    {
        path: '/teatalk',
        name: 'TeaTalkPage',
        component: TeaTalkPage
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
        path: '/profile',
        name: 'Profile',
        component: Profile
        //meta: { requiresAuth: true } // 标识：必须登录后才能访问！
    },
    // 后台管理路由
    {
        path: "/admin",
        component: AdminLayout,
        children: [
            {
                path: "stats",
                name: "AdminStats",
                component: AdminStats,
            },
            {
                path: "knowledge-import",
                name: "KnowledgeImport",
                component: KnowledgeImport,
            },
            {
                path: "feedback",
                name: "FeedbackReview",
                component: FeedbackReview,
            },
            {
                path: "contribution-review",
                name: "ContributionReview",
                component: ContributionReview,
            },
        ],
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



export default router