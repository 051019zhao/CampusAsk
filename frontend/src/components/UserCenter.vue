<template>
  <div class="uc-page">
    <!-- 顶部导航 -->
    <header class="uc-header">
      <span class="uc-logo" @click="$router.push('/')">CampusAsk</span>
      <nav class="uc-nav">
        <span @click="$router.push('/')">首页问答</span>
        <span @click="$router.push('/teatalk')">茶话会</span>
        <span class="active">个人中心</span>
      </nav>
    </header>

    <!-- 内容区 -->
    <div class="uc-main">
      <!-- 左侧：资料卡 -->
      <div class="uc-profile-card">
        <div class="uc-avatar" @click="triggerFileSelect" title="更换头像">
          <img v-if="userInfo.avatarUrl" :src="userInfo.avatarUrl" />
          <span v-else class="uc-avatar-text">{{ (userInfo.nickname||userInfo.username||'?').charAt(0) }}</span>
          <div class="uc-avatar-overlay">换头像</div>
        </div>
        <input ref="fileInputRef" type="file" accept="image/jpeg,image/png" hidden @change="handleNativeUpload" />

        <div class="uc-name">{{ userInfo.nickname || '未设置昵称' }}</div>
        <div class="uc-id">@{{ userInfo.username }}</div>
        <div class="uc-motto">{{ userInfo.motto || '这个人很懒，什么都没写~' }}</div>

        <div class="uc-stats">
          <div class="uc-stat"><strong>{{ historyTotal }}</strong><span>次提问</span></div>
          <div class="uc-stat"><strong>{{ forumTotal }}</strong><span>篇帖子</span></div>
          <div class="uc-stat"><strong>{{ browsePosts.length }}</strong><span>次浏览</span></div>
        </div>

        <div class="uc-actions">
          <button class="btn-pill green" @click="$router.push('/profile')">✏️ 编辑资料</button>
          <button class="btn-pill blue" @click="$router.push('/my-interactions')">💬 我的互动</button>
          <button v-if="userInfo.role==1" class="btn-pill gold" @click="$router.push('/admin')">👑 管理后台</button>
          <button class="btn-pill red" @click="handleLogout">🚪 退出登录</button>
        </div>
      </div>

      <!-- 右侧：内容 Tab -->
      <div class="uc-content">
        <div class="uc-tabs">
          <button :class="{active:activeTab==='questions'}" @click="activeTab='questions'">🔍 提问历史</button>
          <button :class="{active:activeTab==='posts'}" @click="activeTab='posts'">☕ 我的发帖</button>
          <button :class="{active:activeTab==='browse'}" @click="activeTab='browse'">👁 浏览记录</button>
        </div>

        <!-- 提问历史 -->
        <template v-if="activeTab==='questions'">
          <div v-if="historyList.length===0" class="uc-empty">还没有提问过，去首页试试吧~</div>
          <div v-else class="uc-list">
            <div v-for="item in historyList" :key="item.id" class="uc-item">
              <span class="uc-item-text">{{ item.question }}</span>
              <span class="tag tag-src">{{ item.source || '问答' }}</span>
              <span class="uc-item-time">{{ formatTime(item.createTime) }}</span>
            </div>
            <div class="uc-pager">
              <button :disabled="pageParams.page<=1" @click="changePage(-1)">‹</button>
              <span>{{ pageParams.page }}/{{ totalPages }}</span>
              <button :disabled="pageParams.page>=totalPages" @click="changePage(1)">›</button>
            </div>
          </div>
        </template>

        <!-- 发帖 -->
        <template v-if="activeTab==='posts'">
          <div v-if="forumList.length===0" class="uc-empty">还没有发过帖子~</div>
          <div v-else class="uc-list">
            <div v-for="post in forumList" :key="post.id" class="uc-item">
              <span class="uc-item-text">📝 {{ post.title }}</span>
              <span class="tag tag-view">👁 {{ post.viewCount||0 }}</span>
              <span class="tag tag-like">❤ {{ post.likeCount||0 }}</span>
              <span class="uc-item-time">{{ formatTime(post.createdAt) }}</span>
            </div>
            <div class="uc-pager">
              <button :disabled="forumPageParams.page<=1" @click="changeForumPage(-1)">‹</button>
              <span>{{ forumPageParams.page }}/{{ totalForumPages }}</span>
              <button :disabled="forumPageParams.page>=totalForumPages" @click="changeForumPage(1)">›</button>
            </div>
          </div>
        </template>

        <!-- 浏览 -->
        <template v-if="activeTab==='browse'">
          <div v-if="browsePosts.length===0" class="uc-empty">还没有浏览过帖子~</div>
          <div v-else class="uc-list">
            <div v-for="post in browsePosts" :key="post.id" class="uc-item" @click="$router.push('/teatalk')" style="cursor:pointer">
              <span class="uc-item-text">📝 {{ post.title }}</span>
              <span class="uc-item-time">{{ formatTime(post.createdAt) }}</span>
            </div>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { getUserProfile } from '../api/auth.js'
import request from '@/utils/request'

const router = useRouter()
const fileInputRef = ref(null)

const userInfo = ref({ username:'', nickname:'', role:0, avatarUrl:'', motto:'' })
const activeTab = ref('questions')
const browsePosts = ref([])

const historyList = ref([])
const historyTotal = ref(0)
const pageParams = reactive({ page:1, size:5 })
const totalPages = computed(() => Math.ceil(historyTotal.value/pageParams.size)||1)

const forumList = ref([])
const forumTotal = ref(0)
const forumPageParams = reactive({ page:1, size:5 })
const totalForumPages = computed(() => Math.ceil(forumTotal.value/forumPageParams.size)||1)

onMounted(async () => {
  const token = localStorage.getItem('token')
  if (!token) { router.push('/login'); return }
  try {
    const res = await getUserProfile()
    if (res?.data) {
      const d = res.data
      userInfo.value = { username:d.username||'', nickname:d.nickname||'', role:d.role||0, avatarUrl:d.avatarUrl||'', motto:d.motto||'' }
      if (d.username) { localStorage.setItem('userId',d.username); localStorage.setItem('username',d.username) }
      if (d.nickname) localStorage.setItem('nickname',d.nickname)
      if (d.avatarUrl) localStorage.setItem('avatarUrl',d.avatarUrl)
      if (d.role!==undefined) localStorage.setItem('role',String(d.role))
      fetchHistory(); fetchForum(); fetchBrowse()
    }
  } catch(e) { console.error(e) }
})

const fetchHistory = async () => {
  try {
    const res = await request.get('/auth/history',{params:{page:pageParams.page,size:pageParams.size}})
    if (res?.code===200) { historyList.value=res.data.records||[]; historyTotal.value=res.data.total||0 }
  } catch(e){}
}
const fetchForum = async () => {
  try {
    const res = await request.get('/auth/forum-history',{params:{page:forumPageParams.page,size:forumPageParams.size}})
    if (res?.code===200) { forumList.value=res.data.records||[]; forumTotal.value=res.data.total||0 }
  } catch(e){}
}
const fetchBrowse = async () => {
  const key = 'browseHistory_'+(userInfo.value.username||'guest')
  const ids = JSON.parse(localStorage.getItem(key)||'[]')
  if (!ids.length) { browsePosts.value=[]; return }
  try {
    const res = await request.get('/auth/forum-history',{params:{page:1,size:20}})
    if (res?.code===200) { const all=(res.data.records||[]); browsePosts.value=ids.map(id=>all.find(p=>p.id===id)).filter(Boolean) }
  } catch(e){ browsePosts.value=[] }
}

const changePage = (m) => { pageParams.page+=m; fetchHistory() }
const changeForumPage = (m) => { forumPageParams.page+=m; fetchForum() }
const formatTime = (t) => t ? t.replace('T',' ').substring(0,16) : ''

const handleLogout = () => { localStorage.clear(); router.push('/login') }
const triggerFileSelect = () => fileInputRef.value.click()
const handleNativeUpload = async (e) => {
  const file = e.target.files[0]; if (!file) return
  if (!['image/jpeg','image/png'].includes(file.type)) { return }
  if (file.size/1024/1024>2) { return }
  const fd = new FormData(); fd.append('file',file)
  try {
    const r = await request.put('/auth/avatar',fd,{headers:{'Content-Type':'multipart/form-data'}})
    if (r?.code===200) {
      userInfo.value.avatarUrl = r.data; localStorage.setItem('avatarUrl',r.data)
      await request.put('/auth/profile',{nickname:userInfo.value.nickname,motto:userInfo.value.motto,avatarUrl:r.data})
    }
  } catch(e){}
  finally { e.target.value='' }
}
</script>

<style scoped>
.uc-page { min-height:100vh; background:#f0f4f8; font-family:"Microsoft YaHei","PingFang SC",sans-serif; }
.uc-header { background:#fff; padding:0 40px; height:56px; display:flex; align-items:center; justify-content:space-between; box-shadow:0 1px 4px rgba(0,0,0,.06); }
.uc-logo { font-size:20px; font-weight:700; color:#2b5569; cursor:pointer; }
.uc-nav { display:flex; gap:24px; }
.uc-nav span { font-size:14px; color:#909399; cursor:pointer; padding:16px 0; border-bottom:2px solid transparent; }
.uc-nav span.active { color:#2b5569; border-bottom-color:#2b5569; font-weight:600; }
.uc-nav span:hover { color:#2b5569; }

.uc-main { max-width:900px; margin:24px auto; display:flex; gap:20px; padding:0 20px; }
.uc-profile-card { width:260px; flex-shrink:0; background:#fff; border-radius:16px; padding:28px 20px; text-align:center; box-shadow:0 2px 12px rgba(0,0,0,.06); align-self:flex-start; position:sticky; top:20px; }
.uc-avatar { width:80px; height:80px; border-radius:50%; margin:0 auto 12px; background:#d4e0e6; display:flex; align-items:center; justify-content:center; overflow:hidden; cursor:pointer; position:relative; }
.uc-avatar img { width:100%; height:100%; object-fit:cover; }
.uc-avatar-text { font-size:32px; font-weight:700; color:#3c6476; }
.uc-avatar-overlay { position:absolute; inset:0; background:rgba(0,0,0,.4); color:#fff; font-size:12px; display:flex; align-items:center; justify-content:center; opacity:0; transition:opacity .2s; }
.uc-avatar:hover .uc-avatar-overlay { opacity:1; }
.uc-name { font-size:18px; font-weight:700; color:#303133; margin-bottom:2px; }
.uc-id { font-size:12px; color:#909399; margin-bottom:8px; }
.uc-motto { font-size:12px; color:#606266; margin-bottom:16px; min-height:18px; }

.uc-stats { display:flex; justify-content:center; gap:20px; margin-bottom:16px; padding:12px 0; border-top:1px solid #f0f2f5; border-bottom:1px solid #f0f2f5; }
.uc-stat { display:flex; flex-direction:column; align-items:center; }
.uc-stat strong { font-size:20px; color:#2b5569; }
.uc-stat span { font-size:11px; color:#909399; }

.uc-actions { display:flex; flex-direction:column; gap:8px; }
.btn-pill { border:none; border-radius:20px; padding:8px 16px; font-size:13px; cursor:pointer; font-weight:500; transition:all .2s; }
.btn-pill.green { background:#e6f7f2; color:#11b980; } .btn-pill.green:hover { background:#11b980; color:#fff; }
.btn-pill.blue { background:#e8f4ff; color:#1890ff; } .btn-pill.blue:hover { background:#1890ff; color:#fff; }
.btn-pill.gold { background:#fef3e2; color:#e6a23c; } .btn-pill.gold:hover { background:#e6a23c; color:#fff; }
.btn-pill.red { background:#fef2f2; color:#f56c6c; } .btn-pill.red:hover { background:#f56c6c; color:#fff; }

.uc-content { flex:1; background:#fff; border-radius:16px; padding:20px; box-shadow:0 2px 12px rgba(0,0,0,.06); min-width:0; }
.uc-tabs { display:flex; gap:4px; margin-bottom:16px; border-bottom:2px solid #f0f2f5; }
.uc-tabs button { border:none; background:none; padding:8px 16px; font-size:13px; color:#909399; cursor:pointer; border-bottom:2px solid transparent; margin-bottom:-2px; }
.uc-tabs button.active { color:#2b5569; border-bottom-color:#2b5569; font-weight:600; }

.uc-empty { text-align:center; color:#C0C4CC; padding:60px 0; font-size:14px; }
.uc-list { display:flex; flex-direction:column; }
.uc-item { display:flex; align-items:center; gap:10px; padding:10px 0; border-bottom:1px solid #f5f7fa; }
.uc-item:hover { background:#fafbfc; margin:0 -8px; padding-left:8px; padding-right:8px; border-radius:6px; }
.uc-item-text { flex:1; font-size:13px; color:#303133; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.uc-item-time { font-size:12px; color:#C0C4CC; flex-shrink:0; }
.tag { font-size:10px; padding:1px 6px; border-radius:4px; flex-shrink:0; }
.tag-src { background:#e6f7f2; color:#11b980; }
.tag-view { background:#f5f5f5; color:#666; }
.tag-like { background:#fff0f6; color:#eb2f96; }

.uc-pager { display:flex; justify-content:center; align-items:center; gap:12px; margin-top:16px; }
.uc-pager button { width:28px; height:28px; border:1px solid #dcdfe6; border-radius:6px; background:#fff; cursor:pointer; font-size:16px; color:#606266; }
.uc-pager button:disabled { opacity:.4; cursor:default; }
.uc-pager span { font-size:12px; color:#909399; }

@media (max-width:700px) {
  .uc-main { flex-direction:column; }
  .uc-profile-card { width:100%; position:static; }
}
</style>
