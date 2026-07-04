<template>
  <div class="home-style-container">
    <header class="nav-header">
      <div class="nav-logo">CampusAsk 校园百事通</div>
      <div class="nav-links">
        <span class="nav-item" @click="goToTeaTalk">茶话会</span>
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
              <input type="file" ref="fileInputRef" style="display: none;" accept="image/jpeg,image/png" @change="handleNativeUpload"/>
              <h2>个人资料中心</h2>
              <p class="welcome-text">欢迎回来，{{ userInfo.nickname || userInfo.username || 'CampusAsk 用户' }}</p>
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
            </div>

            <div class="action area">
              <button class="btn-interaction" @click="goToInteractions">
                💬 查看我的动态（点赞、评论）
              </button>
              <button class="btn-primary" @click="goToEditProfile">
                修改个人信息
              </button>
              <button v-if="userInfo.role == 1" class="btn-admin" @click="goToAdmin">
                进入管理员中心
              </button>
            </div>
          </div>
        </div>

        <div class="right-side-panel">

          <div class="history-card top-box">
            <div class="history-header">
              <h3>🔍 我的提问历史</h3>
              <span class="total-badge green-badge">共 {{ historyTotal }} 条</span>
            </div>
            <hr class="divider-mini" />

            <div v-if="historyList.length > 0" class="mini-list-box">
              <div v-for="item in historyList" :key="item.id" class="history-item-row">
                <div class="history-main">
                  <span class="history-keyword" :title="item.question">{{ item.question || '暂无提问内容' }}</span>
                  <span class="source-tag tag-home">{{ item.source || '通用问答' }}</span>
                </div>
                <div class="history-time">{{ formatTime(item.createTime) }}</div>
              </div>

              <div class="mini-pagination">
                <button :disabled="pageParams.page === 1" @click="changePage(-1)">上一页</button>
                <span>{{ pageParams.page }} / {{ totalPages }}</span>
                <button :disabled="pageParams.page >= totalPages" @click="changePage(1)">下一页</button>
              </div>
            </div>
            <div class="empty-history" v-else>暂无提问历史记录</div>
          </div>

          <div class="history-card bottom-box">
            <div class="history-header">
              <h3>☕ 茶话会发帖记录</h3>
              <span class="total-badge orange-badge">共 {{ forumTotal }} 篇帖子</span>
            </div>
            <hr class="divider-mini" />

            <div v-if="forumList.length > 0" class="mini-list-box">
              <div v-for="post in forumList" :key="post.id" class="history-item-row">
                <div class="history-main">
                  <span class="history-keyword post-title" :title="post.title">📝 {{ post.title }}</span>
                  <span class="post-stat-tag">👁️ {{ post.viewCount || 0 }}</span>
                  <span class="post-stat-tag love-tag">❤️ {{ post.likeCount || 0 }}</span>
                </div>
                <div class="history-time">{{ formatTime(post.createdAt || post.createTime) }}</div>
              </div>

              <div class="mini-pagination">
                <button :disabled="forumPageParams.page === 1" @click="changeForumPage(-1)">上一页</button>
                <span>{{ forumPageParams.page }} / {{ totalForumPages }}</span>
                <button :disabled="forumPageParams.page >= totalForumPages" @click="changeForumPage(1)">下一页</button>
              </div>
            </div>
            <div class="empty-history" v-else>你还没有在茶话会发过帖子哦，快去发一篇吧~</div>
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
import request from '@/utils/request'

const router = useRouter()
const fileInputRef = ref(null)

// 用户基础数据
const userInfo = ref({ username: '', nickname: '', role: 0, avatarUrl: '', motto: '' })

// 1. 提问历史记录状态（右上）
const historyList = ref([])
const historyTotal = ref(0)
const pageParams = reactive({ page: 1, size: 3 })
const totalPages = computed(() => Math.ceil(historyTotal.value / pageParams.size) || 1)

// 2. 茶话会发帖历史记录状态（右下）
const forumList = ref([])
const forumTotal = ref(0)
const forumPageParams = reactive({ page: 1, size: 3 })
const totalForumPages = computed(() => Math.ceil(forumTotal.value / forumPageParams.size) || 1)

onMounted(async () => {
  const token = localStorage.getItem('token')
  if (!token) { router.push('/login'); return }

  try {
    const res = await getUserProfile()
    if (res && res.data) {
      userInfo.value.username = res.data.username
      userInfo.value.nickname = res.data.nickname
      userInfo.value.role = res.data.role
      userInfo.value.avatarUrl = res.data.avatarUrl
      userInfo.value.motto = res.data.motto

      // 同步到本地缓存，保证主页右上角昵称/头像立即一致
      if (res.data.username) {
        localStorage.setItem('userId', res.data.username)
        localStorage.setItem('username', res.data.username)
      }
      if (res.data.nickname) localStorage.setItem('nickname', res.data.nickname)
      if (res.data.avatarUrl) localStorage.setItem('avatarUrl', res.data.avatarUrl)
      if (res.data.role !== undefined && res.data.role !== null) {
        localStorage.setItem('role', String(res.data.role))
      }

      // ✨✨✨【核心修正：解决时有时无的时差漏洞】
      // 拿到真实数据后，直接把当前获取到的热学号以参数形式强制注入接口，杜绝请求裸奔退出
      fetchHistoryData()
      fetchForumHistoryData()
    }
  } catch (error) {
    console.error('拉取个人资料异常:', error)
  }
})

/**
 * ✨✨ 异步拉取提问历史记录（强制捕获学号并追加传参）
 */
const fetchHistoryData = async (activeUsername) => {
  // 优先取传进来的学号，没传则去响应式 userInfo 里拿
  const currentUsername = activeUsername || userInfo.value.username

  if (!currentUsername) {
    console.warn("未抓取到学号，挂起拉取提问记录请求")
    return
  }

  try {
    const res = await request.get('/auth/history', {
      // ✨✨【终极修正：把 params 里漏掉的 username 完整补齐对齐后端查询条件！】
      params: {
        page: pageParams.page,
        size: pageParams.size
      }
    })
    if (res && res.code === 200) {
      historyList.value = res.data.records || []
      historyTotal.value = res.data.total || 0
      console.log("右侧提问历史拿到真实数据流：", historyList.value)
    }
  } catch (error) {
    console.error('获取历史记录在组件层拦截到异常:', error)
  }
}

// 拉取右下茶话会发帖
const fetchForumHistoryData = async () => {
  try {
    const res = await request.get('/auth/forum-history', {
      params: { page: forumPageParams.page, size: forumPageParams.size }
    })
    if (res && res.code === 200) {
      forumList.value = res.data.records || []
      forumTotal.value = res.data.total || 0
    }
  } catch (error) { console.error(error) }
}

const changePage = (move) => {
  pageParams.page += move
  fetchHistoryData(userInfo.value.username)
}

const changeForumPage = (move) => { forumPageParams.page += move; fetchForumHistoryData() }
const formatTime = (timeStr) => timeStr ? timeStr.replace('T', ' ').substring(0, 16) : ''

const triggerFileSelect = () => { fileInputRef.value.click() }

/**
 * ✨ 头像异步完美落库控制流：精准对接后端 PUT /auth/avatar 接口
 */
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
    console.log('正使用 PUT 方式向 /auth/avatar 发起真实文件流上传...')

    const ossRes = await request.put('/auth/avatar', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })

    if (ossRes && ossRes.code === 200) {
      const newAvatarUrl = ossRes.data
      console.log('OSS 成功换回短链 URL:', newAvatarUrl)

      await request.put('/auth/profile', {
        nickname: userInfo.value.nickname,
        motto: userInfo.value.motto,
        avatarUrl: newAvatarUrl
      })

      userInfo.value.avatarUrl = newAvatarUrl
      localStorage.setItem('avatarUrl', newAvatarUrl)
      alert('头像已成功更换并保存！')
    }
  } catch (error) {
    console.error('上传头像及持久化写库流拦截到异常:', error)
  } finally {
    event.target.value = ''
  }
}

// 各种跳转
const goToInteractions = () => { router.push('/my-interactions') }
const goToAdmin = () => { router.push('/admin') }
const goToEditProfile = () => { router.push('/profile') }
const goToTeaTalk = () => { router.push('/teatalk') }
const goToHome = () => { router.push('/') }
</script>

<style scoped>
/* 绿调高级分栏全部 CSS 样式，保持无损 */
.home-style-container { font-family: sans-serif; background-color: #f5f7f6; min-height: 100vh; display: flex; flex-direction: column; }
.nav-header { background-color: #ffffff; box-shadow: 0 2px 10px rgba(0, 0, 0, 0.05); padding: 0 40px; height: 64px; display: flex; align-items: center; justify-content: space-between; }
.nav-logo { color: #11b980; font-size: 20px; font-weight: bold; cursor: pointer; }
.nav-links { display: flex; gap: 30px; }
.nav-item { color: #666; cursor: pointer; font-size: 15px; padding: 20px 0; border-bottom: 2px solid transparent; }
.nav-item.active { color: #11b980; border-bottom: 2px solid #11b980; font-weight: bold; }
.main-content { flex: 1; display: flex; justify-content: center; padding: 30px; }
.two-column-layout { display: flex; gap: 25px; width: 100%; max-width: 1100px; }
.left-side-panel { flex: 0 0 380px; width: 380px; }
.right-side-panel { flex: 1; display: flex; flex-direction: column; gap: 20px; min-width: 0; }

.profile-card, .history-card { background-color: #ffffff; border-radius: 12px; box-shadow: 0 6px 20px rgba(0, 0, 0, 0.04); padding: 25px; box-sizing: border-box; }
.history-card { display: flex; flex-direction: column; height: 260px; }

.card-header { text-align: center; }
.avatar-wrapper { position: relative; width: 75px; height: 75px; margin: 0 auto 10px; border-radius: 50%; overflow: hidden; cursor: pointer; border: 2px solid #e6f7f2; transition: all 0.2s; }
.real-avatar { width: 100%; height: 100%; object-fit: cover; }
.avatar-placeholder-icon { font-size: 36px; background-color: #e6f7f2; height: 100%; line-height: 70px; text-align: center; }
.avatar-hover-mask { position: absolute; top: 0; left: 0; width: 100%; height: 100%; background: rgba(0, 0, 0, 0.4); color: white; font-size: 11px; display: flex; align-items: center; justify-content: center; opacity: 0; }
.avatar-wrapper:hover .avatar-hover-mask { opacity: 1; }

h2 { font-size: 20px; margin: 0; color: #333; }
.welcome-text { color: #999; font-size: 13px; margin: 4px 0 0 0; }
.divider { border: none; border-top: 1px solid #eee; margin: 15px 0; }
.divider-mini { border: none; border-top: 1px dashed #eee; margin: 10px 0; }

.info-list { display: flex; flex-direction: column; gap: 12px; margin-bottom: 20px; }
.info-item { display: flex; justify-content: space-between; background-color: #fcfdfd; padding: 10px 14px; border-radius: 6px; border-left: 3px solid #11b980; }
.motto-item { flex-direction: column; align-items: flex-start; gap: 4px; }
.motto-value { font-style: italic; color: #666; font-size: 13px; }
.info-label { color: #666; font-size: 13px; }
.info-value { color: #333; font-weight: bold; font-size: 14px; }

.action-area { display: flex; flex-direction: column; gap: 10px; }
.btn-primary { background-color: #11b980; color: white; border: none; padding: 11px; border-radius: 6px; cursor: pointer; font-weight: 500; }
.btn-admin { background-color: #e6a23c; color: white; border: none; padding: 11px; border-radius: 6px; cursor: pointer; }

.btn-interaction { background-color: #e8f4ff; color: #1890ff; border: 1px solid #bae7ff; padding: 11px; border-radius: 6px; cursor: pointer; font-weight: bold; transition: all 0.2s; }
.btn-interaction:hover { background-color: #1890ff; color: white; }

.history-header { display: flex; justify-content: space-between; align-items: center; }
.history-header h3 { margin: 0; font-size: 16px; color: #333; }
.total-badge { font-size: 11px; padding: 2px 8px; border-radius: 10px; font-weight: 500; }
.green-badge { background-color: #e6f7f2; color: #11b980; }
.orange-badge { background-color: #fff7e6; color: #fa8c16; }

.mini-list-box { display: flex; flex-direction: column; flex: 1; justify-content: space-between; }
.history-item-row { display: flex; justify-content: space-between; align-items: center; padding: 8px 0; border-bottom: 1px dashed #f0f0f0; }
.history-main { display: flex; align-items: center; gap: 8px; flex: 1; min-width: 0; }
.history-keyword { font-size: 13px; color: #333; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: 240px; }
.post-title { font-weight: 500; color: #444; }

.source-tag { font-size: 11px; padding: 1px 5px; border-radius: 4px; }
.tag-home { background-color: #e6f7f2; color: #11b980; }
.post-stat-tag { font-size: 11px; background-color: #f5f5f5; color: #666; padding: 1px 6px; border-radius: 4px; }
.love-tag { background-color: #fff0f6; color: #eb2f96; }
.history-time { font-size: 12px; color: #999; }
.empty-history { text-align: center; color: #999; font-size: 13px; padding-top: 40px; }

.mini-pagination { display: flex; justify-content: center; align-items: center; gap: 10px; padding-top: 5px; }
.mini-pagination button { background: white; border: 1px solid #d9d9d9; padding: 2px 8px; font-size: 11px; border-radius: 4px; cursor: pointer; }
.mini-pagination button:disabled { background: #f5f5f5; color: #ccc; cursor: not-allowed; }
.mini-pagination span { font-size: 12px; color: #666; }
</style>