// src/main.js
import { createApp } from 'vue'
import App from './App.vue'
import router from './router' // 引入刚写好的路由

const app = createApp(App)

app.use(router) // 让 Vue 启用路由功能
app.mount('#app')