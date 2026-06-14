<template>
  <div class="qa-page">
    <div class="qa-shell">
      <aside class="side-panel">
        <div class="brand">
          <div class="brand-badge"></div>
          <div>
            <div class="brand-title">CampusAsk</div>
            <div class="brand-sub">校园智能问答</div>
          </div>
        </div>
        <nav class="side-nav">
          <button
            v-for="item in navItems"
            :key="item"
            class="nav-item"
            @click="onNavClick(item)"
          >
            {{ item }}
          </button>
        </nav>
        <div class="side-group">
          <div class="side-title">最近搜索</div>
          <div class="side-list">
            <div v-for="item in recentList" :key="item" class="side-chip" @click="applyHot(item)">
              {{ item }}
            </div>
          </div>
        </div>
        <button class="contribute-btn" @click="goContribute">我要投稿</button>
      </aside>

      <main class="content-panel">
        <div v-if="chatMessages.length === 0" class="card hero-card">
          <div class="hero-title">今天想问什么？</div>
          <div class="hero-desc">输入问题，系统会匹配校园知识与 AI 建议</div>
        </div>

        <div v-if="chatMessages.length === 0" class="card guide-card">
          <div class="guide-row">
            <div class="guide-dot"></div>
            <div>
              <div class="guide-title">热门板块</div>
              <div class="guide-desc">食堂 / 宿舍 / 课程 / 生活服务</div>
            </div>
          </div>
        </div>

        <div class="chat-panel">
          <div class="chat-title">对话</div>
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
              <div class="chat-text">{{ message.text }}</div>
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
                <span class="source-links-title">📎 参考来源</span>
                <a
                  v-for="(u, i) in message.urls.slice(0,3)"
                  :key="i"
                  :href="u"
                  target="_blank"
                  class="source-link"
                >{{ urlHost(u) }}</a>
              </div>
            </div>
            <div v-if="loading" class="chat-bubble bot loading-bubble">
              正在查询...
            </div>
            <div v-if="!loading && chatMessages.length === 0" class="chat-empty">
              输入问题开始对话
            </div>
          </div>
        </div>

        <div class="composer">
          <textarea
            v-model="keyword"
            placeholder="输入你的问题……"
            rows="2"
            @keyup.enter.exact.prevent="search"
          ></textarea>
          <div class="composer-actions">
            <div class="composer-hint">按 Enter 发送，Shift + Enter 换行</div>
            <button class="send-btn" @click="search">发送</button>
          </div>
        </div>
      </main>

      <aside class="hot-panel">
        <div class="hot-title">热门 TOP10</div>
        <div class="hot-list">
          <div v-if="hotLoading" class="hot-state">加载中...</div>
          <button
            v-else-if="hotError"
            class="hot-state is-error"
            type="button"
            @click="loadHotList"
          >
            获取失败，点击重试
          </button>
          <div v-else-if="hotList.length === 0" class="hot-state">暂无热词</div>
          <template v-else>
            <button
              v-for="(item, index) in hotList"
              :key="item + '-' + index"
              class="hot-item"
              type="button"
              @click="applyHot(item)"
            >
              <span class="hot-rank">{{ index + 1 }}</span>
              <span class="hot-text">{{ item }}</span>
            </button>
          </template>
        </div>
      </aside>
    </div>
  </div>
</template>

<script>
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
      navItems: ['首页', '校园服务', '我的收藏', '设置', '茶话会'],
      recentList: [],
      hotList: [],
      hotLoading: false,
      hotError: ''
    }
  },
  mounted() {
    this.refreshMeta()
  },
  methods: {
    async search() {
      if (this.loading) return
      const question = this.keyword.trim()
      if (!question) return
      this.chatMessages.push({
        id: `${Date.now()}-user`,
        role: 'user',
        text: question
      })
      this.loading = true
      this.noResult = false
      this.results = []
      this.aiAnswer = ''
      let replyText = ''
      let source = ''
      let searchUrls = []
      try {
        const token = localStorage.getItem('token')
        const headers = { 'Content-Type': 'application/json' }
        if (token) headers['token'] = token
        const res = await fetch('/api/qa/ask', {
          method: 'POST',
          headers,
          body: JSON.stringify({ keyword: question })
        })
        const data = await res.json()
        if (data.code === 200 && data.data) {
          source = data.data.source || ''
          searchUrls = data.data.searchUrls || []
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
            question: question  // 绑定对应的问题
          })
        }
        this.loading = false
      }
      // 保存到本地最近搜索（去重，最多6条）
      const local = JSON.parse(localStorage.getItem('recentSearches') || '[]')
      const idx = local.indexOf(question)
      if (idx >= 0) local.splice(idx, 1)
      local.unshift(question)
      localStorage.setItem('recentSearches', JSON.stringify(local.slice(0, 6)))
      this.keyword = ''
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
          .map((item) => `📌 ${item.title}\n${item.content}`)
          .join('\n\n')
      }
      if (this.noResult) return '暂无相关答案，请尝试其他关键词'
      return '暂无可用答案'
    },
    getUserId() {
      const raw = localStorage.getItem('userId')
      const parsed = Number.parseInt(raw || '', 10)
      return Number.isFinite(parsed) ? parsed : null
    },
    async refreshMeta() {
      await Promise.all([this.loadHotList(), this.loadHistory()])
    },
    applyHot(item) {
      if (!item) return
      this.keyword = item
      this.search()
    },
    onNavClick(item) {
      if (item === '茶话会') {
        this.$router.push({ name: 'TeaTalkPage' })
      }
      // 其他导航项可按需扩展
    },
    goContribute() {
      this.$router.push({ name: 'ContributePage' })
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
        else { alert('反馈提交失败，请稍后重试') }
      } catch (e) { alert('反馈提交失败：' + e.message) }
    },
    urlHost(url) {
      try { return new URL(url).hostname } catch { return url }
    },
    sourceLabel(source) {
      const map = {
        knowledge:    '📚 数据库关键词检索',
        ai_knowledge: '🤖 AI检索数据库',
        ai_search:    '🌐 AI联网搜索(华南理工)',
        ai_web:       '💡 AI自主回答',
        fallback:     '⚠️ AI不可用·本地兜底',
        rejected:     '🚫 超出服务范围',
        ai:           '🤖 AI回答',
      }
      return map[source] || source
    },
    async loadHotList() {
      this.hotLoading = true
      this.hotError = ''
      try {
        const res = await fetch('/api/hotspot/top10')
        const data = await res.json()
        if (data.code === 200 && Array.isArray(data.data)) {
          this.hotList = data.data
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
    async loadHistory() {
      // 先从本地取（已登录和游客都能用）
      const local = JSON.parse(localStorage.getItem('recentSearches') || '[]')
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
  min-height: 100vh;
  background: radial-gradient(circle at 15% 10%, #cbe2ec 0%, #a9cddb 45%, #8fbcd1 100%);
  padding: 0;
  box-sizing: border-box;
  font-family: "Microsoft YaHei", "PingFang SC", "Segoe UI", sans-serif;
  color: #1c2b35;
  overflow-y: auto;
}

.qa-shell {
  max-width: none;
  min-height: 100vh;
  margin: 0;
  display: grid;
  grid-template-columns: 200px 1fr 200px;
  gap: 14px;
  background: #f8fbfd;
  border-radius: 0;
  padding: 12px;
  box-shadow: 0 20px 48px rgba(39, 66, 80, 0.18);
}

.side-panel {
  background: #3c6476;
  border-radius: 16px;
  padding: 12px 12px;
  color: #e8f5fb;
  display: flex;
  flex-direction: column;
  gap: 10px;
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
}

.card {
  background: #ffffff;
  border-radius: 16px;
  padding: 12px;
  box-shadow: 0 12px 28px rgba(60, 80, 90, 0.08);
}

.hero-card {
  min-height: 90px;
  display: grid;
  gap: 6px;
  background: linear-gradient(135deg, #cfdde4 0%, #e9f2f6 100%);
}

.hero-title {
  font-size: 18px;
  font-weight: 600;
}

.hero-desc {
  color: #4b6575;
  font-size: 13px;
}

.guide-card {
  background: #d4e0e6;
  padding-top: 10px;
  padding-bottom: 10px;
}

.guide-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.guide-dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: #6b8b99;
}

.guide-title {
  font-weight: 600;
}

.guide-desc {
  font-size: 12px;
  color: #526a77;
}

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
  display: grid;
  gap: 10px;
  padding: 12px 12px 14px;
  background: #ffffff;
  border-radius: 0;
  box-shadow: none;
  min-height: 280px;
}

.chat-title {
  font-size: 14px;
  font-weight: 600;
  color: #3c5461;
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
  margin-top: auto;
  background: #f7f3f2;
  border-radius: 16px;
  padding: 10px 12px;
  display: grid;
  gap: 6px;
  box-shadow: inset 0 0 0 1px rgba(106, 129, 140, 0.2);
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
</style>
