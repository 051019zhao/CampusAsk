<template>
  <div class="gap-page">
    <h2 class="page-title">📋 知识缺口审核</h2>
    <p class="page-desc">以下问题由 AI 联网搜索或自主回答，审核通过后将写入知识库</p>

    <!-- 统计卡片 -->
    <div class="stat-row">
      <div class="stat-card">
        <span class="stat-num">{{ pendingTotal }}</span>
        <span class="stat-label">待审核</span>
      </div>
      <div class="stat-card">
        <span class="stat-num">{{ ignoredTotal }}</span>
        <span class="stat-label">已忽略</span>
      </div>
      <div class="stat-card" v-for="s in sourceStats" :key="s.name">
        <span class="stat-num">{{ s.value }}</span>
        <span class="stat-label">{{ sourceLabel(s.name) }}</span>
      </div>
    </div>

    <!-- Tab 切换 -->
    <div class="tab-row">
      <button :class="{ active: tab === 'pending' }" @click="switchTab('pending')">待审核</button>
      <button :class="{ active: tab === 'ignored' }" @click="switchTab('ignored')">已忽略</button>
    </div>

    <!-- 列表 -->
    <div v-if="loading" class="ui-state ui-state-loading">加载中...</div>
    <div v-else-if="list.length === 0" class="ui-state ui-state-empty">暂无数据</div>
    <div v-else class="gap-list">
      <div v-for="item in list" :key="item.id" class="gap-card" :class="{ ignored: item.status === 'ignored' }">
        <div class="gap-meta">
          <span class="gap-source" :class="'badge-' + item.source">{{ sourceLabel(item.source) }}</span>
          <span class="gap-count" v-if="item.searchCount > 1">被问 {{ item.searchCount }} 次</span>
        </div>
        <div class="gap-question">❓ {{ item.question }}</div>
        <div class="gap-answer">{{ truncate(item.answer, 200) }}</div>
        <!-- V3: 联网搜索来源链接 -->
        <div v-if="item.urls" class="gap-urls">
          <span class="urls-label">📎 来源：</span>
          <a v-for="(u, i) in item.urls.split(',')" :key="i" :href="u" target="_blank" class="gap-url">{{ urlHost(u) }}</a>
        </div>

        <!-- V3: 编辑区（可修改标题和内容后入库） -->
        <button v-if="tab === 'pending' && editingId !== item.id" class="btn-edit" @click="startEdit(item)">✏️ 编辑再入库</button>
        <div v-if="tab === 'pending' && editingId === item.id" class="edit-block">
          <label class="edit-label">标题（问题）：</label>
          <input v-model="editTitle" class="edit-input" placeholder="修改标题" />
          <label class="edit-label">内容（回答）：</label>
          <textarea v-model="editContent" class="edit-textarea" rows="4" placeholder="修改回答内容"></textarea>
          <button class="btn-cancel-edit" @click="cancelEdit">取消编辑</button>
        </div>

        <div v-if="tab === 'pending'" class="gap-actions">
          <!-- 分类选择 -->
          <select v-model="categories[item.id]" class="gap-select">
            <option value="">选择分类...</option>
            <option value="学术学习">学术学习</option>
            <option value="校园生活">校园生活</option>
            <option value="规章制度">规章制度</option>
            <option value="就业指导">就业指导</option>
            <option value="技术支持">技术支持</option>
            <option value="交通出行">交通出行</option>
            <option value="综合咨询">综合咨询</option>
          </select>
          <input v-model="keywordsMap[item.id]" placeholder="关键词（逗号分隔）" class="gap-input" />
          <button class="btn-approve" @click="approve(item)">✅ 通过</button>
          <button class="btn-ignore" @click="ignore(item)">⏭ 忽略</button>
        </div>
        <div v-else class="gap-actions">
          <button class="btn-reopen" @click="reopen(item)">🔄 重新提交</button>
        </div>
      </div>
    </div>

    <!-- 分页 -->
    <div v-if="total > size" class="page-row">
      <button :disabled="page <= 1" @click="goPage(page - 1)">上一页</button>
      <span>第 {{ page }} / {{ Math.ceil(total / size) }} 页（共 {{ total }} 条）</span>
      <button :disabled="page >= Math.ceil(total / size)" @click="goPage(page + 1)">下一页</button>
    </div>
  </div>
</template>

<script>
export default {
  name: 'GapReview',
  data() {
    return {
      tab: 'pending',
      list: [],
      total: 0,
      page: 1,
      size: 10,
      loading: false,
      pendingTotal: 0,
      ignoredTotal: 0,
      sourceStats: [],
      categories: {},
      keywordsMap: {},
      editingId: null,   // V3: 正在编辑的条目 ID
      editTitle: '',     // V3: 编辑中的标题
      editContent: ''    // V3: 编辑中的内容
    }
  },
  mounted() { this.refresh() },
  methods: {
    async refresh() {
      await Promise.all([this.loadList(), this.loadStats()])
    },
    async loadList() {
      this.loading = true
      try {
        const res = await fetch(`/api/admin/gaps/${this.tab}?page=${this.page}&size=${this.size}`, {
          headers: { token: localStorage.getItem('token') || '' }
        })
        const data = await res.json()
        if (data.code === 200 && data.data) {
          this.list = data.data.list || []
          this.total = data.data.total || 0
        }
      } catch (e) { console.error(e) }
      finally { this.loading = false }
    },
    async loadStats() {
      try {
        const [pRes, iRes, sRes] = await Promise.all([
          fetch('/api/admin/gaps/pending?page=1&size=1', { headers: { token: localStorage.getItem('token') || '' } }),
          fetch('/api/admin/gaps/ignored?page=1&size=1', { headers: { token: localStorage.getItem('token') || '' } }),
          fetch('/api/admin/gaps/source-stats', { headers: { token: localStorage.getItem('token') || '' } })
        ])
        const pData = await pRes.json()
        const iData = await iRes.json()
        const sData = await sRes.json()
        if (pData.code === 200) this.pendingTotal = pData.data.total || 0
        if (iData.code === 200) this.ignoredTotal = iData.data.total || 0
        if (sData.code === 200) this.sourceStats = sData.data || []
      } catch (e) { /* ignore */ }
    },
    startEdit(item) {
      this.editingId = item.id
      this.editTitle = item.question
      this.editContent = item.answer
    },
    cancelEdit() {
      this.editingId = null
      this.editTitle = ''
      this.editContent = ''
    },
    async approve(item) {
      const category = this.categories[item.id] || '综合咨询'
      const keywords = this.keywordsMap[item.id] || ''
      if (!category) return alert('请选择分类')
      try {
        const body = { category, keywords }
        // V3: 如果正在编辑，传修改后的标题和内容
        if (this.editingId === item.id) {
          body.title = this.editTitle.trim()
          body.content = this.editContent.trim()
        }
        const res = await fetch(`/api/admin/gaps/${item.id}/approve`, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json', token: localStorage.getItem('token') || '' },
          body: JSON.stringify(body)
        })
        const data = await res.json()
        if (data.code === 200) { alert('已通过并加入知识库！'); this.cancelEdit(); this.refresh() }
        else alert(data.message || '操作失败')
      } catch (e) { alert('网络错误') }
    },
    async ignore(item) {
      if (!confirm('确定忽略此条记录？')) return
      try {
        const res = await fetch(`/api/admin/gaps/${item.id}/ignore`, {
          method: 'PUT',
          headers: { token: localStorage.getItem('token') || '' }
        })
        const data = await res.json()
        if (data.code === 200) this.refresh()
        else alert(data.message || '操作失败')
      } catch (e) { alert('网络错误') }
    },
    async reopen(item) {
      try {
        const res = await fetch(`/api/admin/gaps/${item.id}/reopen`, {
          method: 'PUT',
          headers: { token: localStorage.getItem('token') || '' }
        })
        const data = await res.json()
        if (data.code === 200) { this.tab = 'pending'; this.page = 1; this.refresh() }
        else alert(data.message || '操作失败')
      } catch (e) { alert('网络错误') }
    },
    switchTab(t) { this.tab = t; this.page = 1; this.loadList() },
    goPage(p) { this.page = p; this.loadList() },
    sourceLabel(s) {
      return {
        ai_search: '🌐 联网搜索',
        ai_web: '💡 AI自主回答',
        ai_knowledge: '🤖 AI检索数据库',
        fallback: '⚠️ 系统降级',
        knowledge: '📚 知识库差评',
        feedback: '👎 用户反馈差评'
      }[s] || s
    },
    truncate(text, n) { return text && text.length > n ? text.slice(0, n) + '...' : text },
    urlHost(url) { try { return new URL(url).hostname } catch { return url } }
  }
}
</script>

<style scoped>
.gap-page { padding: 20px; max-width: 1000px; margin: 0 auto; }
.page-title { font-size: 20px; margin-bottom: 4px; }
.page-desc { color: #6b8290; font-size: 13px; margin-bottom: 16px; }
.stat-row { display: flex; gap: 12px; margin-bottom: 16px; flex-wrap: wrap; }
.stat-card { background: #fff; border-radius: 10px; padding: 12px 18px; display: flex; flex-direction: column; box-shadow: 0 4px 12px rgba(0,0,0,.06); }
.stat-num { font-size: 24px; font-weight: 700; color: #2b5569; }
.stat-label { font-size: 12px; color: #6b8290; }
.tab-row { display: flex; gap: 8px; margin-bottom: 16px; }
.tab-row button { border: 1px solid #c5d4dc; border-radius: 8px; padding: 6px 20px; background: #fff; cursor: pointer; font-size: 13px; }
.tab-row button.active { background: #2b5569; color: #fff; border-color: #2b5569; }
.gap-list { display: grid; gap: 10px; }
.gap-card { background: #fff; border-radius: 12px; padding: 14px; box-shadow: 0 4px 12px rgba(0,0,0,.05); }
.gap-card.ignored { opacity: 0.65; }
.gap-meta { display: flex; gap: 8px; align-items: center; margin-bottom: 8px; }
.gap-source { padding: 2px 10px; border-radius: 999px; font-size: 11px; }
.badge-ai_search { background: #e6f7ff; color: #0369a1; }
.badge-ai_web { background: #f3f4f6; color: #6b7280; }
.badge-ai_knowledge { background: #f0e6ff; color: #6d28d9; }
.badge-fallback { background: #fef3c7; color: #92400e; }
.badge-knowledge { background: #fff1f0; color: #cf1322; }
.badge-feedback { background: #fff7e6; color: #d46b08; }
.gap-count { font-size: 11px; color: #e67e22; }
.gap-question { font-weight: 600; margin-bottom: 6px; }
.gap-answer { font-size: 12px; color: #526a77; line-height: 1.5; margin-bottom: 10px; white-space: pre-wrap; }
.gap-urls { margin-bottom: 8px; display: flex; flex-wrap: wrap; gap: 4px; align-items: center; }
.urls-label { font-size: 11px; color: #6b8290; }
.gap-url { font-size: 11px; color: #2E75B6; text-decoration: none; background: #e8f0f5; padding: 2px 8px; border-radius: 4px; }
.gap-url:hover { text-decoration: underline; background: #d0dde5; }
.gap-actions { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
.gap-select { border: 1px solid #c5d4dc; border-radius: 6px; padding: 5px 10px; font-size: 12px; }
.gap-input { border: 1px solid #c5d4dc; border-radius: 6px; padding: 5px 10px; font-size: 12px; flex: 1; min-width: 120px; }
.btn-approve { border: none; border-radius: 6px; padding: 6px 14px; background: #27ae60; color: #fff; font-size: 12px; cursor: pointer; }
.btn-ignore { border: 1px solid #c5d4dc; border-radius: 6px; padding: 6px 14px; background: #fff; font-size: 12px; cursor: pointer; }
.btn-reopen { border: 1px solid #c5d4dc; border-radius: 6px; padding: 6px 14px; background: #f0f4f7; font-size: 12px; cursor: pointer; }
.btn-edit { border: 1px dashed #c5d4dc; border-radius: 6px; padding: 4px 10px; background: #fff; font-size: 11px; color: #6b8290; cursor: pointer; margin-bottom: 6px; }
.btn-edit:hover { border-color: #2b5569; color: #2b5569; }
.edit-block { display: grid; gap: 6px; padding: 10px; background: #f7f9fb; border-radius: 8px; margin-bottom: 8px; }
.edit-label { font-size: 11px; color: #6b8290; font-weight: 600; }
.edit-input { border: 1px solid #c5d4dc; border-radius: 6px; padding: 6px 10px; font-size: 12px; width: 100%; box-sizing: border-box; }
.edit-textarea { border: 1px solid #c5d4dc; border-radius: 6px; padding: 6px 10px; font-size: 12px; width: 100%; box-sizing: border-box; resize: vertical; font-family: inherit; }
.btn-cancel-edit { border: 1px solid #c5d4dc; border-radius: 6px; padding: 4px 10px; background: #fff; font-size: 11px; cursor: pointer; justify-self: start; }
.page-row { display: flex; gap: 12px; align-items: center; justify-content: center; margin-top: 16px; font-size: 13px; }
.page-row button { border: 1px solid #c5d4dc; border-radius: 6px; padding: 4px 12px; background: #fff; cursor: pointer; }
</style>
