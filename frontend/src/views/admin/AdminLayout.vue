<template>
  <div class="admin-layout">
    <!-- 侧边栏 -->
    <aside class="sidebar">
      <h3>后台管理系统</h3>
      <ul>
        <li>
          <router-link to="/admin/stats">数据统计</router-link>
        </li>
        <li>
          <router-link to="/admin/knowledge-import">知识导入</router-link>
        </li>
        <li>
          <router-link to="/admin/feedback">用户反馈</router-link>
        </li>
        <li>
          <router-link to="/admin/contribution-review">投稿审核</router-link>
        </li>
        <li>
          <router-link to="/admin/gap-review">知识缺口</router-link>
        </li>
      </ul>
      <router-link to="/" class="home-btn">🏠 返回主页</router-link>
    </aside>
    <!-- 主内容区（子页面会渲染在这里） -->
    <main class="main-content">
      <router-view />
    </main>
  </div>
</template>

<style scoped>
.admin-layout {
  display: flex;
  min-height: 100vh;
}
.sidebar {
  width: 200px;
  background: #2d3748;
  color: white;
  padding: 20px;
}
.sidebar h3 {
  margin-bottom: 30px;
}
.sidebar ul {
  list-style: none;
  padding: 0;
}
.sidebar li {
  margin-bottom: 15px;
}
.sidebar a {
  color: white;
  text-decoration: none;
}
.home-btn {
  display: block;
  margin-top: 30px;
  padding: 8px 12px;
  border-radius: 8px;
  background: rgba(255,255,255,0.12);
  color: #cbd5e0;
  text-decoration: none;
  font-size: 13px;
  text-align: center;
}
.home-btn:hover {
  background: rgba(255,255,255,0.22);
  color: #fff;
}
.main-content {
  flex: 1;
  padding: 20px;
  background: #f5f7fa;
}
</style>
