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
            <span class="info-value">{{ userInfo.role === 1 ? '系统管理员' : '普通学生' }}</span>
          </div>
        </div>

        <div class="action-area">
          <button class="btn-primary" @click="goToEditProfile">
            修改个人信息（昵称/密码/名言）
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
import request from '../utils/request.js'

const router = useRouter()
const fileInputRef = ref(null) // 用于绑定隐藏的 input 节点

// 初始化用户信息载体（✨ 本周追加 avatarUrl 和 motto 默认字段）
const userInfo = ref({
  username: '',
  nickname: '',
  role: 0,
  avatarUrl: '',
  motto: ''
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
      // ✨ 补丁 3：顺手接住后端返回的最新头像和名言，塞给响应式变量
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
// 原生图片选择与上传核心逻辑（完全避开外部组件库）
// ==========================================================================
const triggerFileSelect = () => {
  // 模拟点击隐藏的 input 按钮
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
    // 1. 发送请求把图片上传到 OSS
    const res = await request.put('/auth/avatar', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })

    console.log("上传成功，后端返回的数据：", res)

    // 2. 上传 OSS 成功后进入核心落库逻辑
    if (res && res.code === 200) {
      // 步骤 A：把完整的阿里云 URL 赋给变量，让页面上的头像瞬间回显
      userInfo.value.avatarUrl = res.data

      // 步骤 B：✨【当场写库补丁】直接调用更新资料接口，把新头像 URL 焊死在数据库里！
      try {
        const updateRes = await request.put('/auth/profile', {
          nickname: userInfo.value.nickname, // 带着当前的昵称
          motto: userInfo.value.motto,       // 带着当前的名言
          avatarUrl: userInfo.value.avatarUrl // 带着刚刚热乎拿到的最新头像链接
        })

        if (updateRes && (updateRes.code === 200 || updateRes.code === 1)) {
          alert('头像已成功上传并保存！下次刷新也不会丢失啦~')
        } else {
          alert('头像已传至云端，但同步到数据库失败：' + (updateRes.message || '未知错误'))
        }
      } catch (dbError) {
        console.error('同步头像到数据库异常:', dbError)
        alert('网络异常，头像持久化保存失败')
      }
    }

  } catch (error) {
    console.error('上传头像异常:', error)
    alert('网络异常，头像上传失败')
  } finally {
    event.target.value = ''
  }
}

// 路由跳转：去往修改个人信息页面
const goToEditProfile = () => {
  // 💡 跳转时建议把当前已经回显出的新头像链接也传给修改页（或者在修改页重新读一次接口）
  router.push('/profile')
}

// 路由跳转：返回主页
const goToHome = () => {
  router.push('/')
}
</script>

<style scoped>
/* ==========================================================================
   原有 CSS 全部完美保留，以下为针对新头像架构与名言追补的原生绿调样式
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

/* ✨ 新增：头像容器特殊设计（支持悬浮和裁剪圆角） */
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

/* ✨ 新增：云端真实头像图片渲染 */
.real-avatar {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

/* ✨ 新增：保底默认图标 */
.avatar-placeholder-icon {
  font-size: 40px;
  background-color: #e6f7f2;
  width: 100%;
  height: 100%;
  line-height: 76px;
  text-align: center;
}

/* ✨ 新增：原生滑过遮罩提示效果 */
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
  border-left: 3px solid #11b980;
}

/* ✨ 新增：针对较长名言的特定自适应排列排版 */
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