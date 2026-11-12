// src/router/index.js
import { createRouter, createWebHistory } from 'vue-router'

// 1. 引入你和队友的页面组件
const UserCenter = () => import('@/components/UserCenter.vue')
const QaPage = () => import('../components/QaPage.vue')
const UserAuth = () => import('../components/UserAuth.vue')
const ContributePage = () => import('../components/ContributePage.vue')
const Profile = () => import('@/components/Profile.vue')
const TeaTalkPage = () => import('@/components/TeaTalkPage.vue')
const MyInteraction = () => import('@/components/MyInteractions.vue')
// 队友新增的后台管理组件
const AdminLayout = () => import('../views/admin/AdminLayout.vue')
const KnowledgeImport = () => import('../views/admin/KnowledgeImport.vue')
const ContributionReview = () => import('../views/admin/ContributionReview.vue')
const AdminStats = () => import('../views/admin/AdminStats.vue')
const FeedbackReview = () => import('../views/admin/FeedbackReview.vue')
const GapReview = () => import('../views/admin/GapReview.vue')
const UserManage = () => import('../views/admin/UserManage.vue')

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
        path: '/my-interactions',
        name: 'MyInteractions',
        component: MyInteraction
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
            { path: "crawler", name: "CrawlReview", component: () => import("../views/admin/CrawlReview.vue") },
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
            {
                path: "gap-review",
                name: "GapReview",
                component: GapReview,
            },
            {
                path: "users",
                name: "UserManage",
                component: UserManage,
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
