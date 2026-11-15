<template>
  <div class="qa-page">
    <div class="qa-shell">
      <aside class="side-panel">
        <div class="brand">
          <span class="brand-badge" aria-hidden="true">问</span>
          <div>
            <div class="brand-title">CampusAsk</div>
            <div class="brand-sub">校园生活指南</div>
          </div>
        </div>
        <nav class="side-nav">
          <button
            v-for="item in visibleNavItems"
            :key="item.label"
            class="nav-item"
            :class="{ 'nav-item-admin': item.admin, 'is-current': $route.path === item.route }"
            :aria-current="$route.path === item.route ? 'page' : undefined"
            @click="onNavClick(item)"
          >
            <span class="nav-icon">{{ item.icon }}</span>
            {{ item.label }}
          </button>
        </nav>
        <div class="side-group">
          <div class="side-title">最近搜索</div>
          <div class="side-list">
            <button v-for="item in recentList" :key="item" type="button" class="side-chip" @click="applyHot(item)">
              {{ item }}
            </button>
          </div>
        </div>
        <button class="contribute-btn" @click="goContribute">我要投稿</button>
      </aside>

      <main class="content-panel">
        <div v-if="chatMessages.length === 0" class="card hero-card">
          <div class="hero-kicker">CAMPUSASK <span>·</span> 校园问答</div>
          <div class="hero-title">在校园，<br>找到答案<span class="hero-stop">。</span></div>
          <div class="hero-desc">课程、办事、校园生活。用一个问题，找到有出处的回答。</div>
          <div class="hero-rule"><span>从这里开始提问</span><span>↘</span></div>
        </div>

        <!-- V3: 首页快捷提问卡片 -->
        <div v-if="chatMessages.length === 0" class="quick-cards">
          <button v-for="(q, index) in quickQuestions" :key="q" class="quick-card" @click="applyHot(q)"><span class="quick-number">{{ String(index + 1).padStart(2, '0') }}</span><span>{{ q }}</span><span class="quick-arrow" aria-hidden="true">↗</span></button>
        </div>

        <div class="chat-panel">
          <div class="chat-header">
            <div class="chat-title">问答记录 <span class="chat-title-en">CONVERSATION</span></div>
            <button v-if="chatMessages.length > 0" :disabled="loading || documentLoading" class="new-topic-btn" @click="newTopic" title="开启新话题">＋ 新话题</button>
          </div>
          <div class="chat-list">
            <div
              v-for="message in chatMessages"
              :key="message.id"
              class="chat-bubble"
              :class="message.role"
            >
              <span
                v-if="message.role === 'bot' && message.source"
                class="source-tag"
                :class="'tag-' + message.source"
              >
                {{ sourceLabel(message.source) }}
              </span>
              <!-- V3: bot 回答用 Markdown 渲染，user 消息纯文本 -->
              <div v-if="message.role === 'bot'" class="chat-text" v-html="renderMd(message.text)"></div>
              <div v-else class="chat-text">{{ message.text }}</div>
              <div v-if="linkedSources(message.citations).length" class="source-links">
                <span class="source-links-title">原文出处</span>
                <a
                  v-for="source in linkedSources(message.citations)"
                  :key="source.url"
                  :href="source.url"
                  target="_blank"
                  rel="noopener noreferrer"
                  class="source-link"
                >{{ source.ids.join(' ') }} {{ source.title }} ↗</a>
              </div>
              <details v-if="message.citations && message.citations.length" class="rag-sources">
                <summary>查看检索来源（{{ message.citations.length }}）</summary>
                <div v-for="citation in message.citations" :key="citation.id">
                  <strong>[{{ citation.id }}] {{ citation.title }} · 片段 {{ citation.chunk }}</strong>
                  <pre>{{ citation.excerpt }}</pre>
                </div>
              </details>
              <small v-if="message.agentSteps && message.agentSteps.length" class="agent-steps">查证过程 · {{ message.agentSteps.join(' → ') }}</small>
              <small v-if="message.role === 'bot' && message.memorySaved === false && message.requestedMemory">本轮未保存到长时记忆。</small>
              <div v-if="message.role === 'bot'" class="msg-actions">
                <button class="copy-btn" @click="copyText(message.text)" title="复制回答">复制</button>
              </div>
              <div v-if="message.role === 'bot'" class="feedback-row">
                <button
                  class="fb-btn"
                  :class="{ active: message.rated === 'up' }"
                  :disabled="!!message.rated"
                  @click="sendFeedback(message, true)"
                >👍 有帮助</button>
                <button
                  class="fb-btn"
                  :class="{ active: message.rated === 'down' }"
                  :disabled="!!message.rated"
                  @click="sendFeedback(message, false)"
                >👎 没帮助</button>
                <span v-if="message.rated" class="fb-done">已反馈</span>
              </div>
              <div v-if="message.urls && message.urls.length" class="source-links">
                <span class="source-links-title">参考来源</span>
                <a
                  v-for="(u, i) in message.urls.slice(0,3)"
                  :key="i"
                  :href="u"
                  target="_blank"
                  class="source-link"
                >{{ urlHost(u) }}</a>
              </div>
              <!-- V3: 相关问题推荐（在最后一条 bot 消息内展示） -->
              <div v-if="message.role === 'bot' && suggestedQuestions.length > 0 && message.id === lastBotId" class="suggestions-box">
                <div class="suggestions-label">继续了解</div>
                <div class="suggestions-list">
                  <button
                    v-for="(sq, i) in suggestedQuestions"
                    :key="'sq-' + i"
                    class="suggestion-chip"
                    @click="applyHot(formatSuggestion(sq))"
                  >{{ formatSuggestion(sq) }}</button>
                </div>
              </div>
            </div>
            <div v-if="loading" class="chat-bubble bot loading-bubble">
              正在查找资料…
            </div>
            <div v-if="!loading && chatMessages.length === 0" class="chat-empty">
              你的问题会出现在这里
            </div>
          </div>
        </div>

        <div class="composer">
          <div class="rag-toolbar">
            <label><input type="checkbox" v-model="agentEnabled" :disabled="loading || !!qaImageUrl" /> 多步查证</label>
            <label><input type="checkbox" v-model="memoryEnabled" :disabled="loading" /> 使用长时记忆</label>
            <button :disabled="loading || documentLoading" @click="forgetMemory">删除我的历史记忆</button>
            <span>登录后可跨话题召回过往提问，保存 90 天；关闭后本轮不读取或保存长时记忆。</span>
          </div>
          <div v-for="doc in documents" :key="doc.id" class="rag-document">
            <span>资料 · {{ doc.title }}</span>
            <button :disabled="loading || documentLoading" @click="removeDocument(doc.id)">移除</button>
          </div>
          <div v-if="documentStatus" role="status" class="rag-status">{{ documentStatus }}</div>
          <textarea v-model="keyword" aria-label="输入校园问题" placeholder="写下你想了解的校园问题…" rows="2" @keyup.enter.exact.prevent="search"></textarea>
          <!-- V4: 图片预览 -->
          <img v-if="qaImageUrl" :src="qaImageUrl" class="qa-preview-img" @click="qaImageUrl=''" title="点击移除" />
          <div class="composer-actions">
            <div class="composer-left">
              <input type="file" accept="image/*" ref="qaImageInput" hidden @change="handleQaImage" />
              <button class="img-btn" :disabled="agentEnabled" @click="$refs.qaImageInput.click()" title="上传图片">图片</button>
              <input ref="documentInput" type="file" accept=".csv,.docx,.txt,.md" hidden @change="uploadDocument" />
              <button :disabled="loading || documentLoading" class="img-btn" @click="$refs.documentInput.click()">{{ documentLoading ? '正在导入…' : '导入资料' }}</button>
              <span v-if="qaUploading" class="uploading-hint">上传中...</span>
            </div>
            <div class="composer-hint">按 Enter 发送，Shift + Enter 换行</div>
            <button class="send-btn" :disabled="loading || documentLoading || !keyword.trim()" @click="search">发送 <span aria-hidden="true">↗</span></button>
          </div>
        </div>
      </main>

      <aside class="hot-panel">
        <!-- 用户信息区：已登录显示头像+昵称，未登录显示登录按钮 -->
        <div class="user-block">
          <div v-if="isLoggedIn" class="user-info-wrap">
            <div class="user-info" @click="$router.push('/user-center')">
              <div class="user-avatar">
                <img v-if="userInfo.avatarUrl" :src="userInfo.avatarUrl" alt="avatar" class="avatar-img" />
                <span v-else class="avatar-initial">{{ avatarInitial }}</span>
              </div>
              <div class="user-meta">
                <span class="user-name">{{ userInfo.nickname || userInfo.username || '用户' }}</span>
                <span class="user-role-tag">{{ isAdmin ? '管理员' : '校园用户' }}</span>
              </div>
            </div>
            <button class="logout-btn" type="button" @click="logout">退出登录</button>
          </div>
          <!-- V4: 通知铃铛 -->
          <div v-if="isLoggedIn" class="notif-bell" @click="showNotifs=!showNotifs" title="通知">
            通知<span v-if="unreadCount>0" class="notif-badge">{{ unreadCount }}</span>
          </div>
          <button v-else class="login-btn" @click="$router.push('/login')">登录</button>
        </div>

        <!-- V4: 通知下拉 -->
        <div v-if="showNotifs" class="notif-dropdown">
          <div v-if="notifications.length===0" class="notif-empty">暂无通知</div>
          <div v-for="n in notifications" :key="n.id" class="notif-item" :class="{unread:!n.is_read}" @click="readNotif(n)">{{ n.content }}<span class="notif-time">{{ formatNotifTime(n.created_at) }}</span></div>
        </div>

        <div class="hot-title">
          大家在问 <span class="hot-title-en">TRENDING</span>
          <div class="hot-tabs">
            <button :class="{active: selectedRange==='today'}" @click="changeRange('today')">今日</button>
            <button :class="{active: selectedRange==='7d'}" @click="changeRange('7d')">近7日</button>
            <button :class="{active: selectedRange==='30d'}" @click="changeRange('30d')">近30日</button>
          </div>
        </div>
        <div class="hot-list">
          <div v-if="hotLoading" class="hot-state ui-state ui-state-loading">加载中...</div>
          <button v-else-if="hotError" class="hot-state is-error ui-state ui-state-error" type="button" @click="loadHotData(selectedRange)">获取失败，点击重试</button>
          <div v-else-if="questionRanking.length === 0 && hotList.length === 0" class="hot-state ui-state ui-state-empty">暂无数据</div>
          <template v-else>
            <!-- V4: 常见问题排行榜（带进度条 + 同义归并） -->
            <template v-if="questionRanking.length > 0">
              <button v-for="(item, index) in questionRanking" :key="'q-' + index" class="ranking-card" type="button" @click="applyHot(item.name)">
                <div class="ranking-header">
                  <span class="hot-rank" :class="'rank-' + (index + 1)">{{ rankMedal(index) }}</span>
                  <span class="ranking-name">{{ truncateName(item.name, 12) }}</span>
                  <span class="ranking-count">{{ item.value }}次</span>
                </div>
                <div class="ranking-bar-track">
                  <div class="ranking-bar-fill" :style="{ width: rankingBarWidth(item) }"></div>
                </div>
              </button>
            </template>
            <!-- 兜底：热词列表 -->
            <template v-else>
              <button v-for="(item, index) in hotList" :key="item.keyword + '-' + index" class="hot-item" type="button" @click="applyHot(item.keyword)">
                <span class="hot-rank">{{ index + 1 }}</span>
                <span class="hot-text">{{ item.keyword }}</span>
                <span class="hot-count">{{ item.freq }}</span>
              </button>
            </template>
          </template>
        </div>
      </aside>
    </div>
  </div>
</template>

<script>
import { clearAuthState } from '@/utils/request'
import { toastError } from '@/utils/toast'
import { marked } from 'marked'
import { safeMarkdown } from '@/utils/safeMarkdown'

// 配置 marked 安全渲染
marked.setOptions({ breaks: true, gfm: true })

export default {
  name: "QaPage",
  data() {
    return {
      keyword: "",
      results: [],
      aiAnswer: "",
      loading: false,
      noResult: false,
      chatMessages: [],
      sessionId: '',           // V3: 多轮对话会话ID
      documents: [],
      documentLoading: false,
      documentStatus: '',
      memoryEnabled: true,
      agentEnabled: true,
      suggestedQuestions: [],  // V3: 相关问题推荐
      qaImageUrl: '',          // V4: 问答图片
      qaUploading: false,      // V4: 图片上传中
      showNotifs: false,       // V4: 通知面板
      notifications: [],       // V4: 通知列表
      unreadCount: 0,          // V4: 未读数
      // V3: 首页快捷提问卡片（默认兜底）
      quickQuestions: [
        '食堂几点开门？',
        '图书馆在哪里？',
        '校车时刻表',
        '宿舍门禁是几点？',
        '一卡通怎么补办？',
        '怎么选课？'
      ],
      DEFAULT_QUICK_QUESTIONS: null,  // 保存默认值用于兜底
      navItems: [
        { label: '茶话会',   icon: '01', route: '/teatalk' },
        { label: '校园问答', icon: '02', route: '/' },
        { label: '个人中心', icon: '03', route: '/user-center' },
      ],
      userInfo: {
        nickname: localStorage.getItem('nickname') || '',
        avatarUrl: localStorage.getItem('avatarUrl') || '',
        username: localStorage.getItem('userId') || localStorage.getItem('username') || '',
        role: localStorage.getItem('role') || ''
      },
      recentList: [],
      hotList: [],
      hotLoading: false,
      hotError: '',
      selectedRange: '7d',
      questionRanking: [],   // V4: 常见问题排行榜
      authExpiredHandler: null,
      profileUpdatedHandler: null
    }
  },
  computed: {
    isLoggedIn() { return !!localStorage.getItem('token') },
    isAdmin() { return String(this.userInfo.role) === '1' },
    visibleNavItems() {
      if (!this.isAdmin) return this.navItems
      return [
        ...this.navItems,
        { label: '管理员统计', icon: '04', route: '/admin/stats', admin: true }
      ]
    },
    avatarInitial() {
      const name = this.userInfo.nickname || this.userInfo.username || 'U'
      return name.slice(0, 1).toUpperCase()
    },
    // V3: 最后一条 bot 消息的 ID，用于定位推荐问题展示位置
    lastBotId() {
      const bots = this.chatMessages.filter(m => m.role === 'bot')
      return bots.length > 0 ? bots[bots.length - 1].id : null
    }
  },
  mounted() {
    this.authExpiredHandler = () => {
      this.sessionId = ''; this.documents = []; this.chatMessages = []
      this.resetUserInfo()
    }
    this.profileUpdatedHandler = () => {
      this.loadUserInfo()
    }
    window.addEventListener('auth-expired', this.authExpiredHandler)
    window.addEventListener('profile-updated', this.profileUpdatedHandler)
    this.refreshMeta()
    this.loadUserInfo()
    this.sessionId = sessionStorage.getItem(this.ragSessionKey()) || ''
    if (this.sessionId && localStorage.getItem('token')) {
      this.ragRequest(`/documents?sessionId=${encodeURIComponent(this.sessionId)}`)
        .then(docs => { this.documents = docs })
        .catch(error => { this.documentStatus = error.message })
    }
  },
  beforeUnmount() {
    if (this.authExpiredHandler) {
      window.removeEventListener('auth-expired', this.authExpiredHandler)
    }
    if (this.profileUpdatedHandler) {
      window.removeEventListener('profile-updated', this.profileUpdatedHandler)
    }
  },
  methods: {
    ragSessionKey() { return `ragSession:${this.getCurrentUsername() || 'guest'}` },
    ensureSession() {
      if (!this.sessionId) this.sessionId = `sess_${crypto.randomUUID()}`
      sessionStorage.setItem(this.ragSessionKey(), this.sessionId)
      return this.sessionId
    },
    async ragRequest(path, options = {}) {
      const token = localStorage.getItem('token')
      if (!token) throw new Error('请先登录，再使用资料和长时记忆')
      const response = await fetch(`/api/qa/rag${path}`, { ...options, headers: { token, ...(options.headers || {}) } })
      const body = await response.json()
      if (!response.ok || body.code !== 200) throw new Error(body.message || '操作失败，请重试')
      return body.data
    },
    async uploadDocument(event) {
      const file = event.target.files[0]
      if (!file) return
      if (file.size > 5 * 1024 * 1024) { this.documentStatus = '文件不能超过 5 MB'; event.target.value = ''; return }
      this.documentLoading = true
      this.documentStatus = '正在解析、分块并建立语义索引…'
      try {
        const session = this.ensureSession()
        const form = new FormData()
        form.append('file', file); form.append('sessionId', session)
        const doc = await this.ragRequest('/documents', { method: 'POST', body: form })
        this.documents = await this.ragRequest(`/documents?sessionId=${encodeURIComponent(session)}`)
        this.documentStatus = doc.duplicate ? '该资料已经导入' : `已导入 ${doc.title}（${doc.chunks} 个片段），可以开始提问`
      } catch (error) { this.documentStatus = error.message }
      finally { this.documentLoading = false; event.target.value = '' }
    },
    async removeDocument(id) {
      this.documentLoading = true
      try {
        await this.ragRequest(`/documents/${encodeURIComponent(id)}?sessionId=${encodeURIComponent(this.sessionId)}`, { method: 'DELETE' })
        this.documents = this.documents.filter(doc => doc.id !== id)
        this.documentStatus = '资料及其索引已移除'
      } catch (error) { this.documentStatus = error.message }
      finally { this.documentLoading = false }
    },
    async forgetMemory() {
      try { await this.ragRequest('/memory', { method: 'DELETE' }); this.documentStatus = '你的历史记忆已删除' }
      catch (error) { this.documentStatus = error.message }
    },
    async handleQaImage(e) {
      const file = e.target.files[0]; if (!file) return
      this.qaUploading = true
      try {
        const fd = new FormData(); fd.append('file', file)
        const token = localStorage.getItem('token'); const headers = token ? { token } : {}
        const r = await fetch('/api/qa/upload-image', { method: 'POST', headers, body: fd })
        const d = await r.json()
        if (d.code === 200) this.qaImageUrl = d.data
        else alert(d.message || '上传失败')
      } catch (ex) { alert('上传失败') }
      finally { this.qaUploading = false; e.target.value = '' }
    },
    async search() {
      if (this.loading || this.documentLoading) return
      const question = this.keyword.trim()
      if (!question) return

      // V3: 初始化 sessionId（首次提问时生成）
      this.ensureSession()

      this.chatMessages.push({
        id: `${Date.now()}-user`,
        role: 'user',
        text: question
      })
      this.loading = true
      this.noResult = false
      this.results = []
      this.aiAnswer = ''
      this.suggestedQuestions = []
      let replyText = ''
      let source = ''
      let searchUrls = []
      let citations = []
      let memorySaved = true
      let agentSteps = []
      const useAgent = this.agentEnabled && !this.qaImageUrl
      const requestedMemory = this.memoryEnabled
      try {
        const token = localStorage.getItem('token')
        const headers = { 'Content-Type': 'application/json' }
        if (token) headers['token'] = token
        // V3: 传递 sessionId 支持多轮对话; V4: 图片URL
        const res = await fetch(useAgent ? '/api/qa/agent/ask' : '/api/qa/ask', {
          method: 'POST',
          headers,
          body: JSON.stringify({ keyword: question, sessionId: this.sessionId, imageUrl: this.qaImageUrl, memoryEnabled: requestedMemory })
        })
        const data = await res.json()
        if (data.code === 200 && data.data) {
          source = data.data.source || ''
          searchUrls = data.data.searchUrls || []
          citations = data.data.citations || []
          memorySaved = data.data.memorySaved !== false
          agentSteps = data.data.agentSteps || []
          // V3: 接收推荐问题
          if (data.data.suggestions && Array.isArray(data.data.suggestions)) {
            this.suggestedQuestions = data.data.suggestions
          }
          const ans = data.data.answer
          if (Array.isArray(ans)) {
            this.results = ans
            if (this.results.length === 0) this.noResult = true
          } else if (typeof ans === 'string') {
            this.aiAnswer = ans
          } else {
            this.noResult = true
          }
        } else {
          this.noResult = true
          this.aiAnswer = data.message || '请求未完成，请重试'
        }
        replyText = this.buildAnswerText()
      } catch (e) {
        this.noResult = true
        replyText = '智能回答暂时不可用，请稍后再试。'
      } finally {
        if (replyText) {
          this.chatMessages.push({
            id: `${Date.now()}-bot`,
            role: 'bot',
            text: replyText,
            source: source,
            urls: searchUrls,
            citations,
            memorySaved,
            agentSteps,
            requestedMemory,
            question: question  // 绑定对应的问题
          })
        }
        this.loading = false
      }
      // 保存到本地最近搜索（按账号隔离，去重，最多6条）
      const local = JSON.parse(localStorage.getItem(this.getRecentSearchStorageKey()) || '[]')
      const idx = local.indexOf(question)
      if (idx >= 0) local.splice(idx, 1)
      local.unshift(question)
      localStorage.setItem(this.getRecentSearchStorageKey(), JSON.stringify(local.slice(0, 6)))
      this.keyword = ''; this.qaImageUrl = ''
      this.refreshMeta()
    },
    buildAnswerText() {
      if (this.aiAnswer) {
        // 清理来源标记和多余空白，这些信息已在标签中展示
        return this.aiAnswer
          .replace(/\[来源:.*?\]/g, '')
          .replace(/\n{3,}/g, '\n\n')
          .trim()
      }
      if (Array.isArray(this.results) && this.results.length > 0) {
        return this.results
          .map((item) => `${item.title}\n${item.content}`)
          .join('\n\n')
      }
      if (this.noResult) return '暂无相关答案，请尝试其他关键词'
      return '暂无可用答案'
    },
    getCurrentUsername() {
      return localStorage.getItem('userId') || localStorage.getItem('username') || ''
    },
    getRecentSearchStorageKey() {
      const username = this.getCurrentUsername()
      return username ? `recentSearches:${username}` : 'recentSearches:guest'
    },
    getUserId() {
      const userId = this.getCurrentUsername()
      return userId || null
    },
    async refreshMeta() {
      await Promise.all([this.loadHotData(), this.loadHistory(), this.loadNotifications()])
    },
    async loadHotData(range = '7d') {
      await Promise.all([this.loadHotList(range), this.loadQuestionRanking(range)])
    },
    applyHot(item) {
      if (!item) return
      this.keyword = item
      this.search()
    },
    resetUserInfo() {
      this.userInfo.nickname = ''
      this.userInfo.avatarUrl = ''
      this.userInfo.username = ''
      this.userInfo.role = ''
    },
    loadUserInfo() {
      const token = localStorage.getItem('token')
      if (!token) {
        this.resetUserInfo()
        return
      }
      this.userInfo.nickname = localStorage.getItem('nickname') || ''
      this.userInfo.avatarUrl = localStorage.getItem('avatarUrl') || ''
      this.userInfo.username = localStorage.getItem('userId') || localStorage.getItem('username') || ''
      this.userInfo.role = localStorage.getItem('role') || ''
      // 后台静默刷新，保证头像昵称是最新的
      fetch('/api/auth/profile', { headers: { token } })
        .then(async (r) => {
          if (r.status === 401) {
            clearAuthState()
            this.resetUserInfo()
            toastError('登录已过期，请重新登录')
            if (this.$route.path !== '/login') {
              this.$router.replace('/login')
            }
            return null
          }
          return r.json()
        })
        .then(data => {
          if (!data) return
          if (data.code === 200 && data.data) {
            const p = data.data
            if (p.nickname) { this.userInfo.nickname = p.nickname; localStorage.setItem('nickname', p.nickname) }
            if (p.avatarUrl) { this.userInfo.avatarUrl = p.avatarUrl; localStorage.setItem('avatarUrl', p.avatarUrl) }
            if (p.username) {
              this.userInfo.username = p.username
              localStorage.setItem('userId', p.username)
              localStorage.setItem('username', p.username)
            }
            if (p.role !== undefined && p.role !== null) {
              this.userInfo.role = String(p.role)
              localStorage.setItem('role', String(p.role))
            }
          }
        })
        .catch(() => {
          // 网络失败不强制登出，保留本地已登录态等待后续请求校验
        })
    },
    onNavClick(item) {
      if (item.route) {
        this.$router.push(item.route)
      }
    },
    goContribute() {
      this.$router.push({ name: 'ContributePage' })
    },
    logout() {
      clearAuthState()
      this.resetUserInfo()
      this.recentList = []
      window.dispatchEvent(new CustomEvent('auth-expired'))
      this.$router.replace('/login')
    },
    async sendFeedback(message, helpful) {
      try {
        const res = await fetch('/api/qa/feedback', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            question: message.question || '',
            answer: message.text,
            source: message.source || '',
            helpful: helpful
          })
        })
        if (res.ok) { message.rated = helpful ? 'up' : 'down' }
        else { toastError('反馈提交失败，请稍后重试') }
      } catch (e) { toastError('反馈提交失败：' + e.message) }
    },
    urlHost(url) {
      try { return new URL(url).hostname } catch { return url }
    },
    // V3: Markdown 渲染
    renderMd(text) {
      if (!text) return ''
      // 先清理来源标记（已在标签展示）
      const cleaned = text.replace(/\[来源:.*?\]/g, '').trim()
      return safeMarkdown(marked.parse(cleaned))
    },
    // V3: 一键复制
    async copyText(text) {
      try {
        await navigator.clipboard.writeText(text)
        window.dispatchEvent(new CustomEvent('app-toast', { detail: { message: '已复制到剪贴板', type: 'success' } }))
      } catch {
        // fallback for older browsers
        const ta = document.createElement('textarea')
        ta.value = text
        ta.style.position = 'fixed'; ta.style.left = '-9999px'
        document.body.appendChild(ta)
        ta.select()
        document.execCommand('copy')
        document.body.removeChild(ta)
        window.dispatchEvent(new CustomEvent('app-toast', { detail: { message: '已复制到剪贴板', type: 'success' } }))
      }
    },
    // V4: 格式化建议文本，让知识库标题看起来像自然问题
    formatSuggestion(s) {
      if (!s) return ''
      // 已经是问句直接返回
      if (s.endsWith('？') || s.endsWith('?') || s.endsWith('吗')) return s
      // 知识库标题 → 加"？"变成问句
      if (s.length <= 15) return s + '？'
      return s
    },
    linkedSources(citations) {
      const links = new Map()
      for (const citation of citations || []) {
        try {
          const url = new URL(citation.url)
          if (url.protocol !== 'https:' || url.username || url.password) continue
          if (!links.has(url.href)) links.set(url.href, { url: url.href, title: citation.title, ids: [] })
          links.get(url.href).ids.push(`[${citation.id}]`)
        } catch { /* User uploads have no external source URL. */ }
      }
      return [...links.values()]
    },
    sourceLabel(source) {
      const map = {
        knowledge:    '知识库',
        ai_knowledge: '资料回答',
        ai_search:    '联网检索',
        ai_web:       '智能回答',
        fallback:     '检索片段',
        rejected:     '超出范围',
        ai:           '智能回答',
        agent:        '多步查证',
      }
      return map[source] || source
    },
    async loadHotList(range = '7d') {
      this.hotLoading = true
      this.hotError = ''
      try {
        const res = await fetch(`/api/hotspot/top10?range=${range}`)
        const data = await res.json()
        if (data.code === 200 && Array.isArray(data.data)) {
          const parsed = data.data.map(item => {
            if (typeof item === 'string') {
              const m = item.match(/^(.*)\s*\((\d+)\)\s*$/)
              if (m) return { keyword: m[1].trim(), freq: Number(m[2]) }
              return { keyword: item, freq: null }
            }
            return { keyword: item.keyword || item.name || String(item), freq: item.freq || null }
          })
          this.hotList = parsed
          // V4: 动态更新快捷提问卡片（取 Top 6，不够用默认兜底）
          const topKeywords = parsed.slice(0, 6).map(p => p.keyword).filter(Boolean)
          if (topKeywords.length >= 3) {
            this.quickQuestions = topKeywords
          }
        } else {
          this.hotList = []
          this.hotError = 'error'
        }
      } catch (e) {
        this.hotList = []
        this.hotError = 'error'
      } finally {
        this.hotLoading = false
      }
    },
    changeRange(range) {
      this.selectedRange = range
      this.loadHotData(range)
    },
    async loadQuestionRanking(range) {
      try {
        const res = await fetch(`/api/hotspot/questions?range=${range}`)
        const data = await res.json()
        if (data.code === 200 && Array.isArray(data.data)) {
          this.questionRanking = data.data
        } else {
          this.questionRanking = []
        }
      } catch (e) {
        this.questionRanking = []
      }
    },
    rankMedal(index) {
      return String(index + 1).padStart(2, '0')
    },
    rankingBarWidth(item) {
      const max = this.questionRanking[0]?.value || 1
      return Math.round((item.value / max) * 100) + '%'
    },
    truncateName(name, max) {
      if (!name) return ''
      return name.length > max ? name.slice(0, max) + '...' : name
    },
    // V3: 开启新话题，清除会话上下文
    newTopic() {
      if (this.loading || this.documentLoading) return
      sessionStorage.removeItem(this.ragSessionKey())
      this.sessionId = ''
      this.documents = []
      this.documentStatus = '已开启新话题。旧资料仅属于原会话，历史记忆可跨话题召回。'
      this.suggestedQuestions = []
      this.chatMessages = []
      this.results = []
      this.aiAnswer = ''
      this.noResult = false
    },
    async loadNotifications() {
      try { const r = await fetch('/api/notifications', { headers: { token: localStorage.getItem('token') || '' } }); const d = await r.json(); if (d.code === 200) this.notifications = d.data || [] } catch (e) { }
      try { const r = await fetch('/api/notifications/unread-count', { headers: { token: localStorage.getItem('token') || '' } }); const d = await r.json(); if (d.code === 200) this.unreadCount = d.data?.count || 0 } catch (e) { }
    },
    async readNotif(n) { if (!n.is_read) { try { await fetch(`/api/notifications/${n.id}/read`, { method: 'PUT', headers: { token: localStorage.getItem('token') || '' } }); n.is_read = 1; this.unreadCount = Math.max(0, this.unreadCount - 1) } catch (e) { } } },
    formatNotifTime(t) { if (!t) return ''; const d = new Date(t); const now = new Date(); const diff = now - d; if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前'; if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前'; return d.toLocaleDateString() },
    async loadHistory() {
      // 先从本地取（按账号隔离）
      const local = JSON.parse(localStorage.getItem(this.getRecentSearchStorageKey()) || '[]')
      this.recentList = local.slice(0, 6)
      // 如果有 userId，再尝试从后端补充历史
      const userId = this.getUserId()
      if (!userId) return
      try {
        const res = await fetch(`/api/qa/history?userId=${userId}`)
        const data = await res.json()
        if (data.code === 200 && Array.isArray(data.data) && data.data.length > 0) {
          // 合并去重
          const merged = [...new Set([...data.data, ...this.recentList])]
          this.recentList = merged.slice(0, 6)
        }
      } catch (e) { /* 后端不可用时用本地数据兜底 */ }
    }
  }
}
</script>

<style scoped>
.qa-page {
  height: 100vh;
  background: linear-gradient(135deg, #e8f0f5 0%, #d5e3ec 30%, #c8dbe6 60%, #dce8ef 100%);
  padding: 0;
  box-sizing: border-box;
  font-family: "Microsoft YaHei", "PingFang SC", "Segoe UI", sans-serif;
  color: #1c2b35;
  overflow: hidden;
}

.qa-shell {
  max-width: none;
  height: 100vh;
  margin: 0;
  display: grid;
  grid-template-columns: 200px 1fr 200px;
  gap: 14px;
  background: #f8fbfd;
  border-radius: 0;
  padding: 12px;
  box-shadow: 0 20px 48px rgba(39, 66, 80, 0.18);
  align-items: start;
}

.side-panel {
  background: #3c6476;
  border-radius: 16px;
  padding: 12px 12px;
  color: #e8f5fb;
  display: flex;
  flex-direction: column;
  gap: 10px;
  position: sticky;
  top: 12px;
  height: calc(100vh - 24px);
  overflow-y: auto;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
}

.brand-badge {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  background: #b5d7e6;
  object-fit: cover;
}

.brand-title {
  font-size: 18px;
  font-weight: 600;
}

.brand-sub {
  font-size: 12px;
  opacity: 0.8;
}

.side-nav {
  display: grid;
  gap: 10px;
}

.nav-item {
  background: rgba(255, 255, 255, 0.12);
  border: none;
  border-radius: 10px;
  padding: 9px 12px;
  color: #eef7fb;
  text-align: left;
  font-size: 13px;
  cursor: pointer;
}

.nav-item:hover {
  background: rgba(255, 255, 255, 0.22);
}

.side-group {
  display: grid;
  gap: 10px;
}

.side-title {
  font-size: 12px;
  opacity: 0.85;
}

.side-list {
  display: grid;
  gap: 8px;
}

.side-chip {
  background: rgba(255, 255, 255, 0.15);
  border-radius: 10px;
  padding: 6px 10px;
  font-size: 12px;
}

.contribute-btn {
  border: none;
  background: #2b4857;
  color: #eef7fb;
  border-radius: 12px;
  padding: 9px 12px;
  font-size: 13px;
  cursor: pointer;
  margin-top: auto;
  width: 100%;
}

.content-panel {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 0;
  height: calc(100vh - 24px);
  overflow: hidden;
}

/* 中间可滚动区域：header 固定 + 对话列表滚动 */
.chat-list {
  flex: 1;
  overflow-y: auto;
  min-height: 0;
}

.card {
  background: #ffffff;
  border-radius: 16px;
  padding: 16px;
  box-shadow: 0 2px 16px rgba(60,80,90,.06);
  transition: box-shadow .2s;
}
.card:hover { box-shadow: 0 4px 20px rgba(60,80,90,.1); }

.hero-card {
  min-height: 90px;
  display: grid;
  gap: 6px;
  background: linear-gradient(135deg, #e6eef3 0%, #f0f5f8 100%);
  border: 1px solid rgba(43,85,105,.08);
}

.hero-title {
  font-size: 18px;
  font-weight: 600;
}

.hero-desc {
  color: #4b6575;
  font-size: 13px;
}

/* V3: 首页快捷提问卡片 */
.quick-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
}
.quick-card {
  border: 1px solid #e0e8ed;
  border-radius: 14px;
  padding: 14px 12px;
  background: #fff;
  font-size: 13px;
  color: #2b5569;
  cursor: pointer;
  text-align: center;
  transition: all .25s;
  box-shadow: 0 1px 8px rgba(60,80,90,.04);
}
.quick-card:hover {
  background: #f4f7f9;
  border-color: #2b5569;
  box-shadow: 0 6px 20px rgba(43,85,105,.12);
  transform: translateY(-2px);
}

/* V3: 复制按钮 */
.msg-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 2px;
}
.copy-btn {
  border: 1px solid #d5dce3;
  border-radius: 6px;
  padding: 2px 8px;
  background: #fff;
  font-size: 12px;
  cursor: pointer;
  color: #6b8290;
  opacity: 0;
  transition: opacity 0.2s;
}
.chat-bubble:hover .copy-btn {
  opacity: 1;
}
.copy-btn:hover {
  background: #f0f4f7;
  color: #2b5569;
}

/* V3: Markdown 渲染样式 */
.chat-text :deep(p) { margin: 0 0 6px; }
.chat-text :deep(ul), .chat-text :deep(ol) { margin: 4px 0; padding-left: 18px; }
.chat-text :deep(li) { margin: 2px 0; }
.chat-text :deep(a) { color: #2E75B6; }
.chat-text :deep(code) { background: #edf2f7; padding: 1px 5px; border-radius: 4px; font-size: 12px; }
.chat-text :deep(pre) { background: #edf2f7; padding: 8px; border-radius: 8px; overflow-x: auto; }
.chat-text :deep(strong) { font-weight: 600; }
.chat-text :deep(blockquote) { border-left: 3px solid #c5d4dc; padding-left: 10px; margin: 6px 0; color: #6b8290; }

.results {
  display: grid;
  gap: 12px;
}

.result-card {
  display: grid;
  gap: 8px;
}

.result-title {
  font-weight: 600;
  font-size: 15px;
}

.result-content {
  font-size: 13px;
  color: #394f5b;
}

.result-tag {
  align-self: start;
  font-size: 12px;
  background: #e6f3ed;
  color: #2a6a52;
  padding: 2px 10px;
  border-radius: 999px;
}

.ai-card {
  background: #eef4ff;
  border: 1px solid #d5e1ff;
}

.ai-title {
  font-weight: 600;
  margin-bottom: 8px;
  font-size: 14px;
}

.ai-content {
  color: #2f3f6b;
  line-height: 1.6;
  font-size: 13px;
}

.chat-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px 12px 14px;
  background: #ffffff;
  border-radius: 0;
  box-shadow: none;
  min-height: 0;
  overflow: hidden;
}

.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
  position: sticky;
  top: 0;
  background: #fff;
  z-index: 10;
  padding-bottom: 8px;
  border-bottom: 1px solid #f0f2f5;
}

.chat-title {
  font-size: 14px;
  font-weight: 600;
  color: #3c5461;
}

.new-topic-btn {
  border: 1px solid #c5d4dc;
  border-radius: 8px;
  padding: 4px 12px;
  background: #fff;
  color: #5b7686;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
}
.new-topic-btn:hover {
  background: #f0f4f7;
  border-color: #2b5569;
  color: #2b5569;
}

.chat-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 200px;
  padding-right: 4px;
}

.chat-bubble {
  max-width: 70%;
  padding: 10px 14px;
  border-radius: 16px;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  box-shadow: 0 8px 16px rgba(41, 59, 69, 0.08);
}

.chat-bubble.user {
  align-self: flex-end;
  background: #2b5569;
  color: #fff;
  border-bottom-right-radius: 6px;
}

.chat-bubble.bot {
  align-self: flex-start;
  background: #eaf1f4;
  color: #2f4450;
  border-bottom-left-radius: 6px;
}

.loading-bubble {
  opacity: 0.8;
}

.chat-empty {
  color: #7b8b94;
  font-size: 12px;
}

.composer {
  background: #f7f3f2;
  border-radius: 16px;
  padding: 10px 12px;
  display: grid;
  gap: 6px;
  box-shadow: inset 0 0 0 1px rgba(106, 129, 140, 0.2);
  flex-shrink: 0;
}

.composer textarea {
  border: none;
  background: transparent;
  resize: none;
  font-size: 13px;
  font-family: inherit;
  outline: none;
  color: #2c3e50;
  min-height: 58px;
}

.composer-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.composer-left { display:flex; align-items:center; gap:6px; }
.img-btn { border:1px solid #d5dce3; border-radius:8px; padding:4px 10px; background:#fff; font-size:16px; cursor:pointer; }
.uploading-hint { font-size:11px; color:#909399; }
.qa-preview-img { max-height:120px; border-radius:8px; margin-bottom:6px; cursor:pointer; }
.composer-hint {
  font-size: 11px;
  color: #6a7f8a;
}

.send-btn {
  border: none;
  border-radius: 12px;
  padding: 8px 16px;
  background: #2b5569;
  color: #fff;
  font-size: 13px;
  cursor: pointer;
}

.send-btn:hover {
  background: #24485a;
}

.hot-panel {
  background: #3c6476;
  border-radius: 16px;
  padding: 16px 14px;
  color: #e8f5fb;
  display: flex;
  flex-direction: column;
  gap: 12px;
  position: sticky;
  top: 12px;
  height: calc(100vh - 24px);
  overflow-y: auto;
}

.hot-title {
  font-size: 16px;
  font-weight: 600;
}

.hot-list {
  display: grid;
  gap: 8px;
}

.hot-item {
  display: flex;
  gap: 8px;
  align-items: center;
  background: rgba(255, 255, 255, 0.12);
  border-radius: 10px;
  padding: 7px 9px;
  border: none;
  color: inherit;
  text-align: left;
  cursor: pointer;
  width: 100%;
}

.hot-item:hover {
  background: rgba(255, 255, 255, 0.2);
}

.hot-state {
  padding: 10px 8px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.12);
  font-size: 12px;
  text-align: center;
}

.hot-state.is-error {
  border: none;
  color: #ffe2e2;
  cursor: pointer;
}

.hot-rank {
  width: 22px;
  height: 22px;
  border-radius: 7px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: #b5d7e6;
  color: #2b4857;
  font-size: 11px;
  font-weight: 600;
}

.hot-text {
  font-size: 12px;
}

.loading,
.empty-state {
  text-align: center;
  color: #6e7f88;
  font-size: 14px;
}

@media (max-width: 1100px) {
  .qa-shell {
    grid-template-columns: 200px 1fr;
  }

  .hot-panel {
    display: none;
  }
}

@media (max-width: 900px) {
  .qa-page {
    padding: 0;
  }

  .qa-shell {
    grid-template-columns: 1fr;
  }

  .side-panel {
    flex-direction: row;
    flex-wrap: wrap;
    justify-content: space-between;
  }

  .side-group {
    flex: 1 1 100%;
  }

  .upgrade-btn {
    margin-top: 0;
  }
}

/* 检索来源标签（赵丹丹任务：source 字段展示） */
.source-tag {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 500;
  margin-bottom: 6px;
}
.tag-ai {
  background: #e6f0ff;
  color: #1d4ed8;
}
.tag-knowledge {
  background: #fff4e6;
  color: #b45309;
}
.tag-ai_knowledge {
  background: #f0e6ff;
  color: #6d28d9;
}
.tag-ai_search {
  background: #e6f7ff;
  color: #0369a1;
}
.tag-ai_web {
  background: #f3f4f6;
  color: #6b7280;
}
.tag-fallback {
  background: #fef3c7;
  color: #92400e;
}
.tag-rejected {
  background: #fee2e2;
  color: #991b1b;
}
.source-links {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid #dee6eb;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}
.source-links-title {
  font-size: 11px;
  color: #6b8290;
  margin-right: 4px;
}
.source-link {
  font-size: 11px;
  color: #2E75B6;
  text-decoration: none;
  background: #e8f0f5;
  padding: 2px 8px;
  border-radius: 4px;
}
.source-link:hover {
  text-decoration: underline;
  background: #d0dde5;
}
.feedback-row {
  margin-top: 8px;
  display: flex;
  gap: 8px;
  align-items: center;
}
.fb-btn {
  border: 1px solid #d5dce3;
  background: #fff;
  border-radius: 6px;
  padding: 3px 10px;
  font-size: 11px;
  cursor: pointer;
  color: #6b8290;
}
.fb-btn:hover:not(:disabled) {
  background: #f0f4f7;
}
.fb-btn.active { border-color: #2E75B6; color: #2E75B6; }
.fb-btn:disabled { opacity: 0.5; cursor: default; }
.fb-done { font-size: 11px; color: #67C23A; }

/* ===== 用户信息区（热词面板顶部） ===== */
.user-block {
  background: rgba(255, 255, 255, 0.1);
  border-radius: 12px;
  padding: 10px;
}
.user-info-wrap {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.user-info {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
}
.user-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  overflow: hidden;
  background: linear-gradient(135deg, #48b6b5, #5494b9);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.avatar-initial {
  color: #fff;
  font-size: 15px;
  font-weight: 700;
}
.user-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.user-name {
  font-size: 13px;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 120px;
}
.user-role-tag {
  font-size: 11px;
  opacity: 0.75;
}
.logout-btn {
  width: 100%;
  border: 1px solid rgba(255, 255, 255, 0.28);
  border-radius: 8px;
  padding: 6px 8px;
  background: rgba(255, 255, 255, 0.1);
  color: #e8f5fb;
  font-size: 12px;
  cursor: pointer;
}
.logout-btn:hover {
  background: rgba(255, 255, 255, 0.2);
}
.login-btn {
  display: block;
  width: 100%;
  border: none;
  border-radius: 8px;
  padding: 8px;
  background: rgba(255, 255, 255, 0.18);
  color: #e8f5fb;
  font-size: 13px;
  cursor: pointer;
  text-align: center;
}
.login-btn:hover { background: rgba(255, 255, 255, 0.28); }

/* V4: 通知 */
.notif-bell { position: relative; cursor: pointer; font-size: 18px; padding: 4px; }
.notif-badge { position: absolute; top: -4px; right: -4px; background: #f56c6c; color: #fff; font-size: 10px; border-radius: 10px; padding: 1px 5px; min-width: 16px; text-align: center; }
.notif-dropdown { position: absolute; top: 100%; right: 0; background: #fff; border-radius: 10px; box-shadow: 0 8px 24px rgba(0,0,0,.15); z-index: 100; width: 280px; max-height: 360px; overflow-y: auto; margin-top: 8px; }
.notif-empty { text-align: center; padding: 20px; color: #C0C4CC; font-size: 13px; }
.notif-item { padding: 10px 14px; border-bottom: 1px solid #f5f7fa; font-size: 12px; color: #606266; cursor: pointer; }
.notif-item.unread { background: #e6f7ff; font-weight: 600; }
.notif-item:hover { background: #f5f7fa; }
.notif-time { display: block; font-size: 10px; color: #C0C4CC; margin-top: 3px; }

/* ===== 热词 Tab 切换 ===== */
.hot-title {
  display: flex;
  flex-direction: column;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
}
.hot-tabs {
  display: flex;
  gap: 4px;
}
.hot-tabs button {
  flex: 1;
  border: none;
  border-radius: 8px;
  padding: 5px 0;
  font-size: 11px;
  background: rgba(255, 255, 255, 0.1);
  color: #c8e4f0;
  cursor: pointer;
  transition: all 0.2s;
}
.hot-tabs button.active {
  background: rgba(255, 255, 255, 0.3);
  color: #fff;
  font-weight: 600;
}
.hot-tabs button:hover:not(.active) {
  background: rgba(255, 255, 255, 0.18);
}

/* ===== 热词频次徽章 ===== */
.hot-count {
  margin-left: auto;
  font-size: 10px;
  background: rgba(255, 255, 255, 0.18);
  border-radius: 6px;
  padding: 1px 6px;
  color: #c8e4f0;
  flex-shrink: 0;
}

/* V4: 常见问题排行榜样式 */
.ranking-card {
  display: flex;
  flex-direction: column;
  gap: 4px;
  background: rgba(255,255,255,0.1);
  border: none;
  border-radius: 8px;
  padding: 6px 8px;
  color: inherit;
  text-align: left;
  cursor: pointer;
  width: 100%;
  transition: background 0.15s;
}
.ranking-card:hover { background: rgba(255,255,255,0.2); }
.ranking-header {
  display: flex;
  align-items: center;
  gap: 6px;
}
.ranking-name {
  flex: 1;
  font-size: 12px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ranking-count {
  font-size: 10px;
  background: rgba(251,191,36,0.3);
  border-radius: 6px;
  padding: 1px 6px;
  color: #fde68a;
  flex-shrink: 0;
  font-weight: 600;
}
.ranking-bar-track {
  height: 4px;
  background: rgba(255,255,255,0.12);
  border-radius: 2px;
  overflow: hidden;
}
.ranking-bar-fill {
  height: 100%;
  background: linear-gradient(90deg, rgba(251,191,36,0.6), rgba(251,191,36,0.9));
  border-radius: 2px;
  transition: width 0.5s ease;
}
.ranking-item { gap: 6px; }
.ranking-text { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; line-height: 1.3; }
.hot-rank.rank-1, .hot-rank.rank-2, .hot-rank.rank-3 { background: transparent; font-size: 18px; flex-shrink: 0; width: 24px; text-align: center; }
.hot-rank.rank-1 { color: #FFD700; }
.hot-rank.rank-2 { color: #C0C0C0; }
.hot-rank.rank-3 { color: #CD7F32; }

/* ===== 侧边栏导航图标 & 管理员 ===== */
.nav-icon {
  margin-right: 4px;
}
.nav-item-admin {
  background: rgba(251, 191, 36, 0.2);
  color: #fde68a;
}
.nav-item-admin:hover {
  background: rgba(251, 191, 36, 0.35);
}

/* ===== V3: 相关问题推荐 ===== */
.suggestions-box {
  align-self: flex-start;
  margin-top: 2px;
  padding: 10px 14px;
  background: #f7f9fb;
  border-radius: 12px;
  border: 1px dashed #d5dce3;
}
.suggestions-label {
  font-size: 12px;
  color: #6b8290;
  margin-bottom: 8px;
}
.suggestions-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.suggestion-chip {
  border: 1px solid #c5d4dc;
  border-radius: 999px;
  padding: 5px 14px;
  background: #fff;
  color: #2b5569;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
}
.suggestion-chip:hover {
  background: #eaf1f4;
  border-color: #2b5569;
}
.rag-toolbar { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; font-size: 12px; color: #496575; }
.rag-toolbar label { display: inline-flex; align-items: center; gap: 4px; }
.rag-toolbar span { flex-basis: 100%; font-size: 11px; }
.rag-toolbar button, .rag-document button { border: 1px solid #c5d4dc; border-radius: 6px; background: white; color: #2b5569; cursor: pointer; padding: 4px 8px; }
.rag-document { display: flex; justify-content: space-between; align-items: center; gap: 8px; font-size: 12px; padding: 6px; background: #f0f5f7; border-radius: 6px; }
.rag-document span { overflow-wrap: anywhere; }
.rag-status { font-size: 12px; color: #2b5569; }
.rag-sources { margin-top: 8px; font-size: 12px; border-top: 1px solid #d5dce3; padding-top: 8px; }
.rag-sources pre { white-space: pre-wrap; overflow-wrap: anywhere; font-family: inherit; max-height: 220px; overflow-y: auto; }
button:disabled { opacity: .55; cursor: wait; }

/* CampusAsk editorial palette: paper, ink and a restrained campus green. */
.qa-page {
  --paper: #f7f5ef;
  --surface: #fffefa;
  --ink: #22322d;
  --muted: #68766c;
  --line: #d8ddd2;
  --forest: #315b49;
  --accent: #ba8140;
  background: var(--paper);
  color: var(--ink);
  font-family: "PingFang SC", "Microsoft YaHei", sans-serif;
}
.qa-shell {
  grid-template-columns: 236px minmax(0, 1fr) 260px;
  gap: 0;
  padding: 0;
  background: var(--paper);
  box-shadow: none;
}
.side-panel, .hot-panel {
  top: 0;
  height: 100vh;
  border-radius: 0;
  overflow-y: auto;
}
.side-panel {
  background: #e9eee6;
  color: var(--ink);
  border-right: 1px solid var(--line);
  padding: 29px 18px 22px;
  gap: 34px;
}
.brand { gap: 11px; padding: 0 8px 24px; border-bottom: 1px solid #cdd8cc; }
.brand-badge {
  width: 42px;
  height: 42px;
  border: 1px solid var(--forest);
  border-radius: 50%;
  background: transparent;
  color: var(--forest);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-family: "Songti SC", "SimSun", serif;
  font-size: 23px;
  flex-shrink: 0;
}
.brand-title { font-size: 18px; font-weight: 700; letter-spacing: -.035em; }
.brand-sub { font-size: 10px; letter-spacing: .17em; color: var(--muted); opacity: 1; }
.side-nav { gap: 3px; }
.nav-item {
  display: flex;
  align-items: center;
  gap: 14px;
  background: transparent;
  border-radius: 3px;
  padding: 12px 13px;
  color: #4f6155;
  font-size: 13px;
  font-weight: 500;
  transition: background .15s, color .15s;
}
.nav-item:hover, .nav-item.is-current { background: var(--forest); color: #fffefa; }
.nav-icon { width: 19px; margin: 0; font-size: 10px; letter-spacing: .08em; opacity: .72; }
.side-title { color: var(--muted); opacity: 1; font-size: 11px; letter-spacing: .12em; }
.side-list { gap: 0; }
.side-chip {
  width: 100%;
  background: transparent;
  border: 0;
  border-bottom: 1px solid #d5ded3;
  border-radius: 0;
  padding: 10px 2px;
  text-align: left;
  color: #475b4d;
  cursor: pointer;
}
.side-chip:hover { color: var(--forest); background: #e0e9de; }
.contribute-btn {
  background: transparent;
  color: var(--forest);
  border: 1px solid #aabfac;
  border-radius: 3px;
  text-align: left;
  padding: 12px 14px;
  font-weight: 600;
}
.contribute-btn:hover { background: #dfe9dd; }
.content-panel {
  height: 100vh;
  gap: 0;
  padding: 0 30px 22px;
  background: var(--surface);
}
.hero-card {
  position: relative;
  flex: 0 0 auto;
  min-height: 0;
  margin: 28px 0 22px;
  padding: 22px 25px 15px;
  border: 1px solid var(--line);
  border-left: 4px solid var(--forest);
  border-radius: 2px;
  background: #f3f5ee;
  box-shadow: none;
  gap: 12px;
}
.card:hover { box-shadow: none; }
.hero-kicker { font-size: 10px; letter-spacing: .22em; font-weight: 700; color: var(--forest); }
.hero-kicker span { color: var(--accent); margin: 0 4px; }
.hero-title {
  font-family: "Songti SC", "STSong", "SimSun", serif;
  font-size: clamp(28px, 3vw, 45px);
  line-height: 1.23;
  letter-spacing: -.055em;
  font-weight: 700;
  color: var(--ink);
}
.hero-stop { color: var(--accent); }
.hero-desc { font-size: 12px; color: #617166; letter-spacing: .02em; }
.hero-rule {
  display: flex;
  justify-content: space-between;
  border-top: 1px solid #d5ded1;
  padding-top: 10px;
  margin-top: 5px;
  color: var(--forest);
  font-size: 11px;
  letter-spacing: .08em;
}
.quick-cards { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 20px; margin-bottom: 16px; }
.quick-card {
  display: flex;
  align-items: center;
  gap: 12px;
  border: 0;
  border-bottom: 1px solid var(--line);
  border-radius: 0;
  background: transparent;
  color: var(--ink);
  text-align: left;
  padding: 11px 2px;
  box-shadow: none;
  font-size: 12px;
}
.quick-card:hover { background: #f2f5ef; border-color: var(--forest); box-shadow: none; transform: none; }
.quick-number { color: var(--accent); font-size: 10px; letter-spacing: .1em; }
.quick-arrow { margin-left: auto; color: var(--forest); font-size: 16px; }
.chat-panel { background: transparent; padding: 4px 0 12px; gap: 0; }
.chat-header { background: var(--surface); padding: 8px 0 12px; border-bottom: 1px solid var(--line); }
.chat-title { color: var(--ink); font-size: 13px; letter-spacing: .04em; }
.chat-title-en { margin-left: 8px; color: #9ba99c; font-size: 9px; letter-spacing: .16em; }
.new-topic-btn { border-radius: 2px; border-color: var(--line); color: var(--forest); }
.new-topic-btn:hover { background: #eef2e9; border-color: var(--forest); color: var(--forest); }
.chat-list { gap: 17px; padding: 16px 4px 8px 0; }
.chat-bubble { box-shadow: none; border-radius: 2px; line-height: 1.75; font-size: 13px; }
.chat-bubble.user { background: var(--forest); border-radius: 2px; color: #fffefa; padding: 11px 16px; }
.chat-bubble.bot { max-width: 92%; background: #f5f6f0; border-left: 2px solid var(--accent); color: var(--ink); padding: 14px 18px; }
.chat-empty { font-size: 12px; color: #9aa89b; padding: 22px 3px; }
.source-tag { background: transparent !important; color: var(--forest) !important; padding: 0; border-radius: 0; font-size: 10px; letter-spacing: .1em; font-weight: 700; }
.source-links { border-color: var(--line); gap: 8px; }
.source-links-title, .suggestions-label { color: var(--muted); font-size: 11px; }
.source-link { color: var(--forest); background: #e9eee5; border-radius: 2px; padding: 5px 8px; }
.source-link:hover { background: #dce8db; }
.agent-steps { display: block; color: var(--muted); font-size: 11px; margin-top: 8px; }
.rag-sources { border-color: var(--line); color: #4e6153; }
.rag-sources summary { cursor: pointer; }
.suggestions-box { background: #f3f5ee; border: 1px solid var(--line); border-radius: 2px; }
.suggestion-chip { border-radius: 2px; color: var(--forest); border-color: var(--line); }
.suggestion-chip:hover { background: #e7eee4; border-color: var(--forest); }
.copy-btn, .fb-btn { opacity: 1; border-radius: 2px; border-color: var(--line); color: var(--forest); }
.fb-btn.active { border-color: var(--forest); color: var(--forest); }
.composer {
  background: #f1f3ec;
  border: 1px solid var(--line);
  border-radius: 2px;
  padding: 12px 15px;
  box-shadow: none;
  gap: 10px;
}
.rag-toolbar { color: var(--muted); gap: 7px 18px; }
.rag-toolbar label { cursor: pointer; }
.rag-toolbar input { accent-color: var(--forest); }
.rag-toolbar span { color: #7c8b7e; font-size: 10px; }
.rag-toolbar button, .rag-document button, .img-btn { border-color: var(--line); border-radius: 2px; color: var(--forest); background: var(--surface); }
.rag-document { background: #e6ede3; border-radius: 2px; }
.composer textarea { color: var(--ink); font-size: 13px; min-height: 45px; }
.composer textarea::placeholder { color: #91a091; }
.composer-hint { color: #8c998e; font-size: 10px; }
.send-btn { background: var(--forest); border-radius: 2px; padding: 9px 17px; font-weight: 600; }
.send-btn:hover { background: #234735; }
.send-btn span { margin-left: 12px; }
.hot-panel {
  background: #eeeae0;
  border-left: 1px solid #ddd7c8;
  color: var(--ink);
  padding: 28px 18px;
  gap: 28px;
}
.user-block { background: transparent; border: 1px solid #d9d3c5; border-radius: 2px; padding: 12px; }
.user-avatar { background: var(--forest); }
.user-name { color: var(--ink); }
.user-role-tag { color: var(--muted); }
.logout-btn, .login-btn { background: transparent; color: var(--forest); border: 1px solid #c6cbbd; border-radius: 2px; }
.logout-btn:hover, .login-btn:hover { background: #e1e8db; }
.notif-bell { font-size: 11px; color: var(--forest); }
.hot-title { color: var(--ink); font-family: "Songti SC", "SimSun", serif; font-size: 20px; gap: 15px; }
.hot-title-en { font-family: "PingFang SC", "Microsoft YaHei", sans-serif; color: var(--accent); font-size: 9px; letter-spacing: .18em; }
.hot-tabs { border-bottom: 1px solid #d4cfbf; gap: 14px; }
.hot-tabs button, .hot-tabs button:hover:not(.active) { background: transparent; color: var(--muted); border-radius: 0; padding: 8px 0; }
.hot-tabs button.active { background: transparent; color: var(--forest); border-bottom: 2px solid var(--forest); }
.ranking-card, .hot-item { background: transparent; border: 0; border-bottom: 1px solid #dbd7cb; border-radius: 0; color: var(--ink); padding: 11px 2px; }
.ranking-card:hover, .hot-item:hover { background: #e7e8db; }
.hot-rank, .hot-rank.rank-1, .hot-rank.rank-2, .hot-rank.rank-3 { background: transparent; color: var(--accent); font-family: Georgia, serif; font-size: 12px; width: 19px; }
.ranking-name { font-size: 12px; }
.ranking-count, .hot-count { color: var(--muted); background: transparent; font-size: 10px; }
.ranking-bar-track { height: 2px; border-radius: 0; background: #d8dccf; }
.ranking-bar-fill { border-radius: 0; background: var(--forest); }
.hot-state { background: transparent; color: var(--muted); }
.qa-page button:focus-visible, .qa-page textarea:focus-visible, .qa-page a:focus-visible,
.qa-page summary:focus-visible { outline: 2px solid var(--accent); outline-offset: 2px; }
@media (max-width: 1180px) {
  .qa-shell { grid-template-columns: 210px minmax(0, 1fr); }
  .hot-panel { display: none; }
}
@media (max-width: 760px) {
  .qa-shell { grid-template-columns: minmax(0, 1fr); grid-template-rows: auto minmax(0, 1fr); height: 100dvh; }
  .side-panel { position: static; height: auto; padding: 11px 14px; gap: 8px; overflow: visible; border-right: 0; border-bottom: 1px solid var(--line); }
  .brand { padding: 0; border: 0; }
  .brand-badge { width: 31px; height: 31px; font-size: 17px; }
  .brand-title { font-size: 15px; }
  .brand-sub { display: none; }
  .side-nav { display: flex; gap: 3px; overflow-x: auto; }
  .nav-item { white-space: nowrap; padding: 7px 8px; font-size: 11px; }
  .nav-icon { display: none; }
  .side-group, .contribute-btn { display: none; }
  .content-panel { height: auto; min-height: 0; padding: 0 15px 13px; }
  .hero-card { margin: 14px 0 10px; padding: 13px 15px; gap: 7px; }
  .hero-title { font-size: 28px; }
  .hero-rule { display: none; }
  .quick-cards { gap: 0 10px; margin-bottom: 7px; }
  .quick-card { padding: 8px 1px; gap: 7px; font-size: 11px; }
  .chat-title-en, .composer-hint { display: none; }
  .chat-bubble.bot { max-width: 96%; }
  .composer { padding: 9px 10px; }
  .rag-toolbar span { display: none; }
}
@media (max-width: 420px) {
  .quick-cards { grid-template-columns: minmax(0, 1fr); }
  .quick-card:nth-child(n+5) { display: none; }
  .composer-left { gap: 3px; }
  .composer-actions { gap: 5px; }
  .send-btn { padding: 8px 11px; }
}
</style>
