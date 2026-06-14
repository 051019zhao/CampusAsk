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
      </ul>
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
.main-content {
  flex: 1;
  padding: 20px;
  background: #f5f7fa;
}
</style>
