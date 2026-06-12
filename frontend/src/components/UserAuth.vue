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
      <div class="two-column-layout">

        <div class="left-side-panel">
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
        </div>

        <div class="right-side-panel">
          <div class="history-card">
            <div class="history-header">
              <h3>🔍 我的提问历史</h3>
              <span class="total-badge">共 {{ historyTotal }} 条记录</span>
            </div>

            <hr class="divider" />

            <div v-if="historyList.length > 0" class="history-list">
              <div v-for="item in historyList" :key="item.id" class="history-item-row">
                <div class="history-main">
                  <span class="history-keyword" :title="item.keyword">{{ item.keyword }}</span>
                  <span class="source-tag" :class="tagClass(item.sourceTag)">
                    {{ item.sourceTag || '通用问答' }}
                  </span>
                </div>
                <div class="history-time">
                  {{ formatTime(item.createTime) }}
                </div>
              </div>
            </div>

            <div v-else class="empty-history">
              <div class="empty-icon">📂</div>
              <p>您最近还没有提问过任何问题哦~</p>
            </div>

            <div v-if="historyTotal > 0" class="pagination-area">
              <button
                  class="page-btn"
                  :disabled="pageParams.page === 1"
                  @click="changePage(-1)"
              >
                上一页
              </button>
              <span class="page-indicator">{{ pageParams.page }} / {{ totalPages }}</span>
              <button
                  class="page-btn"
                  :disabled="pageParams.page >= totalPages"
                  @click="changePage(1)"
              >
                下一页
              </button>
            </div>
          </div>
        </div>

      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { getUserProfile } from '../api/auth.js'
import request from '@/utils/request' // 确保路径对应你封装的拦截器实例

const router = useRouter()
const fileInputRef = ref(null)

// 用户基本信息
const userInfo = ref({
  username: '',
  nickname: '',
  role: 0,
  avatarUrl: '',
  motto: ''
})

// 历史提问分页数据
const historyList = ref([])
const historyTotal = ref(0)
const pageParams = reactive({
  page: 1,
  size: 6
})

const totalPages = computed(() => {
  return Math.ceil(historyTotal.value / pageParams.size) || 1
})

onMounted(async () => {
  const token = localStorage.getItem('token')
  if (!token) {
    router.push('/login')
    return
  }

  // A. 拉取个人基本资料
  try {
    const res = await getUserProfile()
    if (res && res.data) {
      userInfo.value.username = res.data.username
      userInfo.value.nickname = res.data.nickname
      userInfo.value.role = res.data.role
      userInfo.value.avatarUrl = res.data.avatarUrl
      userInfo.value.motto = res.data.motto
    }
  } catch (error) {
    // ✨ 核心修复：移除重复的 alert 弹窗，将弹窗职责完全交给全局 request.js 统一处理！
    console.error('拉取用户信息在页面组件级捕获到异常:', error)
  }

  // B. 拉取历史提问记录
  fetchHistoryData()
})

const fetchHistoryData = async () => {
  try {
    const res = await request.get('/auth/history', {
      params: {
        page: pageParams.page,
        size: pageParams.size
      }
    })
    if (res && res.code === 200) {
      historyList.value = res.data.records || []
      historyTotal.value = res.data.total || 0
    }
  } catch (error) {
    // ✨ 核心修复：组件内只管打印控制台日志，拒绝二次重力弹窗弹死用户
    console.error('获取历史记录在页面组件级捕获到异常:', error)
  }
}

const changePage = (move) => {
  pageParams.page += move
  fetchHistoryData()
}

const formatTime = (timeStr) => {
  if (!timeStr) return ''
  return timeStr.replace('T', ' ').substring(0, 16)
}

const tagClass = (tag) => {
  if (!tag) return 'tag-default'
  if (tag.includes('首页') || tag.includes('问答')) return 'tag-home'
  if (tag.includes('贡献') || tag.includes('投稿')) return 'tag-contribute'
  if (tag.includes('茶话会')) return 'tag-talk'
  return 'tag-default'
}

// 原生选择图片与写库持久化
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
      headers: { 'Content-Type': 'multipart/form-data' }
    })

    if (res && res.code === 200) {
      userInfo.value.avatarUrl = res.data

      try {
        await request.put('/auth/profile', {
          nickname: userInfo.value.nickname,
          motto: userInfo.value.motto,
          avatarUrl: userInfo.value.avatarUrl
        })
        alert('头像已成功上传并保存！')
      } catch (dbError) {
        console.error('组件内无感同步数据库异常:', dbError)
      }
    }
  } catch (error) {
    console.error('上传头像组件级拦截异常:', error)
  } finally {
    event.target.value = ''
  }
}

const goToAdmin = () => { router.push('/admin') }
const goToEditProfile = () => { router.push('/profile') }
const goToHome = () => { router.push('/') }
</script>

<style scoped>
/* ==========================================================================
   手写原生绿调风格——高级感左右分栏弹性 CSS
   ========================================================================== */
.home-style-container {
  font-family: 'Helvetica Neue', Helvetica, 'PingFang SC', Arial, sans-serif;
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

.nav-logo { color: #11b980; font-size: 20px; font-weight: bold; }
.nav-links { display: flex; gap: 30px; }
.nav-item { color: #666; cursor: pointer; font-size: 15px; padding: 20px 0; border-bottom: 2px solid transparent; transition: all 0.3s; }
.nav-item:hover, .nav-item.active { color: #11b980; border-bottom: 2px solid #11b980; font-weight: 500; }

.main-content {
  flex: 1;
  display: flex;
  justify-content: center;
  align-items: flex-start;
  padding: 40px 40px;
}

/* 左右布局大容器架构 */
.two-column-layout {
  display: flex;
  flex-direction: row;
  justify-content: center;
  align-items: flex-start;
  gap: 30px;
  width: 100%;
  max-width: 1100px;
}

/* 左侧固定 420 像素 */
.left-side-panel {
  flex: 0 0 420px;
  width: 420px;
}

/* 右侧自适应宽面板 */
.right-side-panel {
  flex: 1;
  min-width: 0;
}

.profile-card {
  background-color: #ffffff;
  border-radius: 12px;
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.05);
  padding: 35px;
  box-sizing: border-box;
}

.card-header { text-align: center; margin-bottom: 20px; }

.avatar-wrapper {
  position: relative; width: 80px; height: 80px; margin: 0 auto 15px; border-radius: 50%; overflow: hidden; cursor: pointer; border: 2px solid #e6f7f2; transition: border-color 0.3s;
}
.avatar-wrapper:hover { border-color: #11b980; }
.real-avatar { width: 100%; height: 100%; object-fit: cover; display: block; }
.avatar-placeholder-icon { font-size: 40px; background-color: #e6f7f2; width: 100%; height: 100%; line-height: 76px; text-align: center; }
.avatar-hover-mask { position: absolute; top: 0; left: 0; width: 100%; height: 100%; background: rgba(0, 0, 0, 0.5); color: #ffffff; font-size: 12px; display: flex; align-items: center; justify-content: center; opacity: 0; transition: opacity 0.3s; }
.avatar-wrapper:hover .avatar-hover-mask { opacity: 1; }

h2 { color: #333333; margin: 0 0 8px 0; font-size: 24px; }
.welcome-text { color: #999999; font-size: 14px; margin: 0; }
.divider { border: none; border-top: 1px solid #eeeeee; margin: 18px 0; }

.info-list { display: flex; flex-direction: column; gap: 16px; margin-bottom: 25px; }
.info-item { display: flex; justify-content: space-between; align-items: center; background-color: #fcfdfd; padding: 12px 16px; border-radius: 6px; border-left: 3px solid #11b980; }
.motto-item { flex-direction: column; align-items: flex-start; gap: 6px; }
.motto-value { font-style: italic; color: #555555 !important; font-weight: normal !important; font-size: 14px !important; word-break: break-all; text-align: left; }
.info-label { color: #666666; font-size: 14px; }
.info-value { color: #333333; font-weight: bold; font-size: 15px; }

.action-area { display: flex; flex-direction: column; gap: 12px; }
.btn-primary { background-color: #11b980; color: #ffffff; border: none; padding: 12px 24px; font-size: 15px; border-radius: 6px; cursor: pointer; width: 100%; font-weight: 500; transition: background-color 0.3s; }
.btn-primary:hover { background-color: #0fa26f; }
.btn-admin { background-color: #e6a23c; color: #ffffff; border: none; padding: 12px 24px; font-size: 15px; border-radius: 6px; cursor: pointer; width: 100%; font-weight: 500; transition: background-color 0.3s; }
.btn-admin:hover { background-color: #cf8e2e; }

/* 右侧提问历史卡片样式 */
.history-card {
  background-color: #ffffff;
  border-radius: 12px;
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.05);
  padding: 30px 35px;
  box-sizing: border-box;
  min-height: 520px;
  display: flex;
  flex-direction: column;
}

.history-header { display: flex; justify-content: space-between; align-items: center; }
.history-header h3 { margin: 0; color: #333333; font-size: 19px; font-weight: 600; }
.total-badge { font-size: 12px; background-color: #e6f7f2; color: #11b980; padding: 4px 12px; border-radius: 20px; font-weight: 500; }

.history-list { display: flex; flex-direction: column; flex-grow: 1; }
.history-item-row { display: flex; justify-content: space-between; align-items: center; padding: 15px 0; border-bottom: 1px dashed #eef1f0; }
.history-item-row:last-child { border-bottom: none; }

.history-main { display: flex; align-items: center; gap: 12px; flex: 1; min-width: 0; }
.history-keyword { font-size: 14px; color: #333333; font-weight: 500; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: 380px; text-align: left; }

.source-tag { font-size: 11px; padding: 2px 7px; border-radius: 4px; white-space: nowrap; }
.tag-default { background-color: #f0f2f5; color: #909399; }
.tag-home { background-color: #e6f7f2; color: #11b980; }
.tag-contribute { background-color: #e8f4ff; color: #1890ff; }
.tag-talk { background-color: #fdf6ec; color: #e6a23c; }

.history-time { font-size: 13px; color: #999999; white-space: nowrap; }

.empty-history { text-align: center; padding: 80px 0; color: #999999; flex-grow: 1; }
.empty-icon { font-size: 42px; margin-bottom: 12px; }
.empty-history p { margin: 0; font-size: 14px; }

.pagination-area { display: flex; justify-content: center; align-items: center; gap: 15px; margin-top: auto; padding-top: 20px; }
.page-indicator { font-size: 14px; color: #666666; font-weight: 500; }
.page-btn { background-color: #ffffff; border: 1px solid #dcdfe6; color: #606266; padding: 6px 14px; font-size: 13px; border-radius: 4px; cursor: pointer; transition: all 0.2s; }
.page-btn:hover:not(:disabled) { color: #11b980; border-color: #11b980; background-color: #e6f7f2; }
.page-btn:disabled { background-color: #f5f7fa; border-color: #e4e7ed; color: #c0c4cc; cursor: not-allowed; }
</style>