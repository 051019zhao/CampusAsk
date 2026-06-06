<template>
  <div class="home-style-container">
    <header class="nav-header">
      <div class="nav-logo">CampusAsk 校园百事通</div>
      <div class="nav-links">
        <span class="nav-item" @click="goToHome">首页问答</span>
        <span class="nav-item active">用户中心</span>
      </div>
    </header>

    <main class="main-content">
      <div class="profile-card">
        <div class="card-header">
          <div class="avatar-placeholder">🎓</div>
          <h2>个人资料中心</h2>
          <p class="welcome-text">欢迎回来，CampusAsk 亲爱的用户</p>
        </div>

        <hr class="divider" />

        <div class="info-list">
          <div class="info-item">
            <span class="info-label">我的学号 / 工号</span>
            <span class="info-value">{{ userInfo.username || '加载中...' }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">当前昵称</span>
            <span class="info-value">{{ userInfo.nickname || '暂无昵称' }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">账户角色</span>
            <span class="info-value">{{ userInfo.role === 1 ? '系统管理员' : '普通学生' }}</span>
          </div>
        </div>

        <div class="action-area">
          <button class="btn-primary" @click="goToEditProfile">
            修改个人信息（昵称/密码）
          </button>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getUserProfile } from '../api/auth.js' // 复用你之前调通的获取信息接口

const router = useRouter()

// 初始化用户信息载体
const userInfo = ref({
  username: '',
  nickname: '',
  role: 0
})

// 页面加载时实时从数据库拉取最新数据
onMounted(async () => {
  // 防御性策略：先看看本地有没有 Token
  const token = localStorage.getItem('token')
  if (!token) {
    alert('登录已过期，请重新登录')
    router.push('/login')
    return
  }

  try {
    // 异步请求后端 GET /auth/profile 接口
    const res = await getUserProfile()
    if (res && res.data) {
      userInfo.value.username = res.data.username
      userInfo.value.nickname = res.data.nickname
      userInfo.value.role = res.data.role
    } else {
      alert('获取用户信息结构异常')
    }
  } catch (error) {
    console.error('动态拉取用户中心数据失败:', error)
  }
})

// 路由跳转：去往修改个人信息页面
const goToEditProfile = () => {
  router.push('/profile')
}

// 路由跳转：返回主页
const goToHome = () => {
  router.push('/')
}
</script>

<style scoped>
/* ==========================================================================
   手写原生 CSS 专属绿调风格（像素级对齐主页与登录注册，无外部组件库依赖）
   ========================================================================== */
.home-style-container {
  font-family: 'Helvetica Neue', Helvetica, 'PingFang SC', 'Hiragino Sans GB', Arial, sans-serif;
  background-color: #f5f7f6;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

/* 顶部导航栏样式 */
.nav-header {
  background-color: #ffffff;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.05);
  padding: 0 40px;
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.nav-logo {
  color: #11b980; /* 标志性校园绿 */
  font-size: 20px;
  font-weight: bold;
}

.nav-links {
  display: flex;
  gap: 30px;
}

.nav-item {
  color: #666;
  cursor: pointer;
  font-size: 15px;
  padding: 20px 0;
  border-bottom: 2px solid transparent;
  transition: all 0.3s;
}

.nav-item:hover, .nav-item.active {
  color: #11b980;
  border-bottom: 2px solid #11b980;
  font-weight: 500;
}

/* 主体布局架构 */
.main-content {
  flex: 1;
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 40px 20px;
}

/* 资料展示卡片 */
.profile-card {
  background-color: #ffffff;
  border-radius: 12px;
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.05);
  width: 100%;
  max-width: 480px;
  padding: 35px;
  box-sizing: border-box;
}

.card-header {
  text-align: center;
  margin-bottom: 20px;
}

.avatar-placeholder {
  font-size: 40px;
  background-color: #e6f7f2;
  width: 80px;
  height: 80px;
  line-height: 80px;
  border-radius: 50%;
  margin: 0 auto 15px;
}

h2 {
  color: #333333;
  margin: 0 0 8px 0;
  font-size: 24px;
}

.welcome-text {
  color: #999999;
  font-size: 14px;
  margin: 0;
}

.divider {
  border: none;
  border-top: 1px solid #eeeeee;
  margin: 20px 0;
}

/* 信息条目列表 */
.info-list {
  display: flex;
  flex-direction: column;
  gap: 18px;
  margin-bottom: 30px;
}

.info-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background-color: #fcfdfd;
  padding: 12px 16px;
  border-radius: 6px;
  border-left: 3px solid #11b980; /* 绿调左边框 */
}

.info-label {
  color: #666666;
  font-size: 14px;
}

.info-value {
  color: #333333;
  font-weight: bold;
  font-size: 15px;
}

/* 交互按钮区域 */
.action-area {
  text-align: center;
}

.btn-primary {
  background-color: #11b980;
  color: #ffffff;
  border: none;
  padding: 12px 24px;
  font-size: 15px;
  border-radius: 6px;
  cursor: pointer;
  width: 100%;
  font-weight: 500;
  transition: background-color 0.3s;
}

.btn-primary:hover {
  background-color: #0fa26f;
}
</style>