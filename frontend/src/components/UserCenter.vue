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
          <div class="avatar-wrapper" @click="triggerFileSelect" title="点击更换头像">
            <img v-if="userInfo.avatarUrl" :src="userInfo.avatarUrl" class="real-avatar" />
            <div v-else class="avatar-placeholder-icon">🎓</div>
            <div class="avatar-hover-mask">更换头像</div>
          </div>
          <input
              type="file"
              ref="fileInputRef"
              style="display: none;"
              accept="image/jpeg,image/png"
              @change="handleNativeUpload"
          />

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
          <div class="info-item motto-item">
            <span class="info-label">个性名言</span>
            <span class="info-value motto-value">{{ userInfo.motto || '这个同学很懒，什么都没留下~' }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">账户角色</span>
            <span class="info-value">{{ userInfo.role == 1 ? '系统管理员' : '普通学生' }}</span>
          </div>
        </div>

        <div class="action-area">
          <button class="btn-primary" @click="goToEditProfile">
            修改个人信息（昵称/密码/名言）
          </button>

          <button v-if="userInfo.role == 1" class="btn-admin" @click="goToAdmin">
            进入管理员中心
          </button>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getUserProfile } from '../api/auth.js' // 复用你调通的获取信息接口
import request from '@/utils/request' // 引入带 token 请求拦截器的实例

const router = useRouter()
const fileInputRef = ref(null) // 用于绑定隐藏的 input 节点

// 初始化用户信息载体
const userInfo = ref({
  username: '',
  nickname: '',
  role: 0,
  avatarUrl: '',
  motto: ''
})

// 页面加载时实时从数据库拉取最新数据
onMounted(async () => {
  const token = localStorage.getItem('token')
  if (!token) {
    alert('登录已过期，请重新登录')
    router.push('/login')
    return
  }

  try {
    const res = await getUserProfile()
    if (res && res.data) {
      userInfo.value.username = res.data.username
      userInfo.value.nickname = res.data.nickname
      userInfo.value.role = res.data.role // 核心权限凭证
      userInfo.value.avatarUrl = res.data.avatarUrl
      userInfo.value.motto = res.data.motto
    } else {
      alert('获取用户信息结构异常')
    }
  } catch (error) {
    console.error('动态拉取用户中心数据失败:', error)
  }
})

// ==========================================================================
// ✨ 头像文件选择与当场落库保存逻辑
// ==========================================================================
const triggerFileSelect = () => {
  fileInputRef.value.click()
}

const handleNativeUpload = async (event) => {
  const file = event.target.files[0]
  if (!file) return

  if (file.type !== 'image/jpeg' && file.type !== 'image/png') {
    alert('头像图片必须是 JPG 或 PNG 格式!')
    return
  }
  if (file.size / 1024 / 1024 > 2) {
    alert('头像图片大小不能超过 2MB!')
    return
  }

  const formData = new FormData()
  formData.append('file', file)

  try {
    const res = await request.put('/auth/avatar', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })

    if (res && res.code === 200) {
      userInfo.value.avatarUrl = res.data // 实时回显

      // 紧接着无感同步到数据库持久化
      try {
        const updateRes = await request.put('/auth/profile', {
          nickname: userInfo.value.nickname,
          motto: userInfo.value.motto,
          avatarUrl: userInfo.value.avatarUrl
        })

        if (updateRes && (updateRes.code === 200 || updateRes.code === 1)) {
          alert('头像已成功上传并保存！')
        } else {
          alert('头像已传至云端，但同步至数据库失败')
        }
      } catch (dbError) {
        console.error('同步数据库异常:', dbError)
      }
    }
  } catch (error) {
    console.error('上传头像异常:', error)
    alert('网络异常，头像上传失败')
  } finally {
    event.target.value = ''
  }
}

// ==========================================================================
// ✨ 路由跳转逻辑
// ==========================================================================
// 新增：管理员中心大按钮点击直达 /admin
const goToAdmin = () => {
  router.push('/admin')
}

const goToEditProfile = () => {
  router.push('/profile')
}

const goToHome = () => {
  router.push('/')
}
</script>

<style scoped>
/* ==========================================================================
   原生 CSS 绿调风格与追补的管理按钮样式
   ========================================================================== */
.home-style-container {
  font-family: 'Helvetica Neue', Helvetica, 'PingFang SC', 'Hiragino Sans GB', Arial, sans-serif;
  background-color: #f5f7f6;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

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
  color: #11b980;
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

.main-content {
  flex: 1;
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 40px 20px;
}

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

.avatar-wrapper {
  position: relative;
  width: 80px;
  height: 80px;
  margin: 0 auto 15px;
  border-radius: 50%;
  overflow: hidden;
  cursor: pointer;
  border: 2px solid #e6f7f2;
  transition: border-color 0.3s;
}

.avatar-wrapper:hover {
  border-color: #11b980;
}

.real-avatar {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.avatar-placeholder-icon {
  font-size: 40px;
  background-color: #e6f7f2;
  width: 100%;
  height: 100%;
  line-height: 76px;
  text-align: center;
}

.avatar-hover-mask {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: rgba(0, 0, 0, 0.5);
  color: #ffffff;
  font-size: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity 0.3s;
}

.avatar-wrapper:hover .avatar-hover-mask {
  opacity: 1;
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
  border-left: 3px solid #11b980;
}

.motto-item {
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
}

.motto-value {
  font-style: italic;
  color: #555555 !important;
  font-weight: normal !important;
  font-size: 14px !important;
  word-break: break-all;
  text-align: left;
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

.action-area {
  text-align: center;
  display: flex;
  flex-direction: column;
  gap: 12px; /* 让两个按钮之间有清爽的间距 */
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

/* --------------------------------------------------------------------------
   ✨ 新增：手写专属管理员中心大按钮样式（亮橘调/金调，极具点击欲和区分度）
   -------------------------------------------------------------------------- */
.btn-admin {
  background-color: #e6a23c;
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

.btn-admin:hover {
  background-color: #cf8e2e;
}
</style>