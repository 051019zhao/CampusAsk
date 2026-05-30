// src/router/index.js
import { createRouter, createWebHistory } from "vue-router";

// 1. 引入你和队友的页面组件
import QaPage from "../components/QaPage.vue"; // 假设这是你队友写的首页
import UserAuth from "../components/UserAuth.vue"; // 你写的登录注册页

import AdminLayout from "../views/admin/AdminLayout.vue";
import KnowledgeImport from "../views/admin/KnowledgeImport.vue";

const routes = [
  {
    path: "/", // 默认访问根路径
    name: "QaPage",
    component: QaPage,
  },
  {
    path: "/login", // 访问 /login
    name: "UserAuth",
    component: UserAuth,
  },
  // 新增：后台管理路由
  {
    path: "/admin",
    component: AdminLayout,
    children: [
      {
        path: "knowledge-import",
        name: "KnowledgeImport",
        component: KnowledgeImport,
      },
    ],
  },
  {
    path: "/:pathMatch(.*)*",
    redirect: "/login",
  },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});

export default router;
