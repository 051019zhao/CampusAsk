<template>
  <div class="feedback-admin">
    <h1>📋 用户反馈管理</h1>

    <!-- 统计卡片 -->
    <div class="card-row">
      <div class="card green">
        <div class="card-num">{{ stats.helpful || 0 }}</div>
        <div class="card-label">👍 有帮助</div>
      </div>
      <div class="card red">
        <div class="card-num">{{ stats.unhelpful || 0 }}</div>
        <div class="card-label">👎 没帮助</div>
      </div>
      <div class="card">
        <div class="card-num">{{ stats.total || 0 }}</div>
        <div class="card-label">📊 总反馈数</div>
      </div>
      <div class="card blue" v-if="stats.total">
        <div class="card-num">{{ Math.round((stats.helpful||0) / Math.max(stats.total||1, 1) * 100) }}%</div>
        <div class="card-label">📈 好评率</div>
      </div>
    </div>

    <div style="display:flex; gap:16px; flex-wrap:wrap; margin-bottom:16px">
      <!-- 好评率饼图 -->
      <div class="section" style="flex:1; min-width:320px">
        <h2>好评率分布</h2>
        <canvas id="pieChart" width="300" height="260"></canvas>
      </div>

      <!-- 近7日趋势 -->
      <div class="section" style="flex:1; min-width:400px">
        <h2>📈 近7日反馈趋势</h2>
        <canvas id="trendCanvas" width="420" height="260"></canvas>
      </div>
    </div>

    <!-- 按问题分组 + 差评高亮 -->
    <div class="section">
      <h2>📊 按问题分组（差评 ≥3 已自动入缺口）</h2>
      <table v-if="grouped.length">
        <thead>
          <tr><th>问题</th><th style="width:60px">👍</th><th style="width:60px">👎</th><th style="width:60px">总计</th><th style="width:100px">状态</th></tr>
        </thead>
        <tbody>
          <tr v-for="(item, i) in grouped" :key="i" :class="{ 'row-warn': item.unhelpful >= 3 }">
            <td class="question-cell" :title="item.question">{{ item.question }}</td>
            <td class="num green-txt">{{ item.helpful }}</td>
            <td class="num red-txt">{{ item.unhelpful }}</td>
            <td class="num">{{ item.total }}</td>
            <td class="num">
              <span v-if="item.unhelpful >= 3" style="color:#E6A23C;font-size:11px">⚠️ 待优化</span>
              <span v-else style="color:#C0C4CC;font-size:11px">正常</span>
            </td>
          </tr>
        </tbody>
      </table>
      <p v-else class="empty-hint">暂无反馈数据</p>
    </div>

    <!-- 筛选 + 反馈列表 -->
    <div class="section">
      <div class="list-header">
        <h2 style="margin:0">📝 反馈记录</h2>
        <div class="filter-tabs">
          <button :class="{active: filter==='all'}" @click="switchFilter('all')">全部</button>
          <button :class="{active: filter==='helpful'}" @click="switchFilter('helpful')">👍 有帮助</button>
          <button :class="{active: filter==='unhelpful'}" @click="switchFilter('unhelpful')">👎 没帮助</button>
        </div>
      </div>

      <table v-if="list.length">
        <thead>
          <tr><th style="width:50%">问题</th><th style="width:70px">反馈</th><th style="width:80px">来源</th><th style="width:130px">时间</th></tr>
        </thead>
        <tbody>
          <tr v-for="(item, i) in list" :key="i" @click="toggleExpand(item)" style="cursor:pointer">
            <td class="question-cell">
              <div>{{ item.question }}</div>
              <div v-if="item._expanded && item.answer" class="answer-preview">
                <strong>回答：</strong>{{ item.answer }}
              </div>
            </td>
            <td class="num">{{ item.helpful == 1 ? '👍' : '👎' }}</td>
            <td class="num"><span class="src-tag">{{ sourceShort(item.source) }}</span></td>
            <td class="time-cell">{{ formatDate(item.created_at) }}</td>
          </tr>
        </tbody>
      </table>
      <p v-else class="empty-hint">暂无反馈记录</p>

      <!-- 分页 -->
      <div v-if="listTotal > pageSize" class="page-row">
        <button :disabled="page <= 1" @click="goPage(page - 1)">上一页</button>
        <span>第 {{ page }} / {{ Math.ceil(listTotal / pageSize) }} 页（共 {{ listTotal }} 条）</span>
        <button :disabled="page >= Math.ceil(listTotal / pageSize)" @click="goPage(page + 1)">下一页</button>
      </div>
    </div>

    <!-- AI 总结 -->
    <div class="section ai-section">
      <h2>🤖 AI 反馈总结</h2>
      <p v-if="aiSummary" class="ai-text">{{ aiSummary }}</p>
      <p v-else class="empty-hint">加载中...</p>
      <button class="btn" @click="loadSummary">🔄 刷新总结</button>
    </div>
  </div>
</template>

<script>
export default {
  name: "FeedbackReview",
  data() {
    return {
      stats: {},
      list: [],
      listTotal: 0,
      grouped: [],
      sourceData: [],
      aiSummary: "",
      trendData: [],
      filter: 'all',
      page: 1,
      pageSize: 10,
    }
  },
  mounted() { this.loadAll() },
  methods: {
    getAuthHeaders() {
      const token = localStorage.getItem("token")
      return token ? { token } : {}
    },
    async loadAll() {
      try {
        const [s, g, src, tr] = await Promise.all([
          fetch("/api/admin/feedback/stats", { headers: this.getAuthHeaders() }),
          fetch("/api/admin/feedback/grouped", { headers: this.getAuthHeaders() }),
          fetch("/api/admin/feedback/by-source", { headers: this.getAuthHeaders() }),
          fetch("/api/admin/feedback/trend", { headers: this.getAuthHeaders() }),
        ])
        const sd = await s.json(), gd = await g.json(), srcd = await src.json(), td = await tr.json()
        if (sd.code === 200) this.stats = sd.data
        if (gd.code === 200) this.grouped = gd.data
        if (srcd.code === 200) this.sourceData = srcd.data
        if (td.code === 200) this.trendData = td.data
        await this.loadList()
        this.loadSummary()
        this.$nextTick(() => { this.drawPie(); this.drawTrend() })
      } catch (e) { console.error(e) }
    },
    async loadList() {
      try {
        const res = await fetch(`/api/admin/feedback/list?page=${this.page}&size=${this.pageSize}&filter=${this.filter}`, {
          headers: this.getAuthHeaders()
        })
        const d = await res.json()
        if (d.code === 200) {
          this.list = (d.data.list || []).map(item => ({ ...item, _expanded: false }))
          this.listTotal = d.data.total || 0
        }
      } catch (e) { console.error(e) }
    },
    switchFilter(f) { this.filter = f; this.page = 1; this.loadList() },
    goPage(p) { this.page = p; this.loadList() },
    toggleExpand(item) { item._expanded = !item._expanded },
    async loadSummary() {
      try {
        const r = await fetch("/api/admin/feedback/summary", { headers: this.getAuthHeaders() })
        const d = await r.json()
        if (d.code === 200) this.aiSummary = d.data
      } catch (e) { }
    },
    sourceShort(s) {
      const m = { knowledge: '📚本地', ai_knowledge: '🤖AI库', ai_search: '🌐联网', ai_web: '💡AI答', fallback: '⚠️降级', rejected: '🚫拒绝' }
      return m[s] || s || '-'
    },
    drawPie() {
      const c = document.getElementById('pieChart')
      if (!c || !this.stats.total) return
      const ctx = c.getContext('2d'), w = c.width, h = c.height, cx = w / 2, cy = h / 2, r = 80
      const hh = this.stats.helpful || 0, uh = this.stats.unhelpful || 0
      const total = hh + uh || 1
      ctx.beginPath(); ctx.moveTo(cx, cy)
      ctx.arc(cx, cy, r, -Math.PI / 2, -Math.PI / 2 + (hh / total) * Math.PI * 2)
      ctx.closePath(); ctx.fillStyle = '#67C23A'; ctx.fill()
      ctx.beginPath(); ctx.moveTo(cx, cy)
      ctx.arc(cx, cy, r, -Math.PI / 2 + (hh / total) * Math.PI * 2, -Math.PI / 2 + Math.PI * 2)
      ctx.closePath(); ctx.fillStyle = '#F56C6C'; ctx.fill()
      ctx.fillStyle = '#303133'; ctx.font = 'bold 22px Arial'; ctx.textAlign = 'center'
      ctx.fillText(Math.round(hh / total * 100) + '%', cx, cy + 6)
      ctx.font = '13px Arial'
      ctx.fillStyle = '#67C23A'; ctx.fillRect(cx - 70, h - 40, 12, 12)
      ctx.fillStyle = '#606266'; ctx.textAlign = 'left'; ctx.fillText('有帮助 ' + hh, cx - 54, h - 30)
      ctx.fillStyle = '#F56C6C'; ctx.fillRect(cx + 20, h - 40, 12, 12)
      ctx.fillText('没帮助 ' + uh, cx + 36, h - 30)
    },
    drawTrend() {
      const c = document.getElementById('trendCanvas')
      if (!c || !this.trendData.length) return
      const ctx = c.getContext('2d'), w = c.width, h = c.height
      const pad = { top: 20, right: 20, bottom: 45, left: 50 }
      const cw = w - pad.left - pad.right, ch = h - pad.top - pad.bottom
      const maxVal = Math.max(...this.trendData.map(d => d.total || 0), 1)
      ctx.clearRect(0, 0, w, h)

      // grid
      for (let i = 0; i <= 4; i++) {
        const y = pad.top + ch / 4 * i
        ctx.strokeStyle = '#eee'; ctx.beginPath(); ctx.moveTo(pad.left, y); ctx.lineTo(w - pad.right, y); ctx.stroke()
        ctx.fillStyle = '#999'; ctx.font = '11px Arial'; ctx.textAlign = 'right'
        ctx.fillText(Math.round(maxVal * (1 - i / 4)), pad.left - 8, y + 4)
      }

      const gap = cw / this.trendData.length
      const bw = Math.min(28, gap - 6)
      this.trendData.forEach((d, i) => {
        const x = pad.left + gap * i + (gap - bw) / 2
        const hh = (d.helpful || 0) / maxVal * ch
        const uh = (d.unhelpful || 0) / maxVal * ch
        ctx.fillStyle = '#67C23A'; ctx.fillRect(x, pad.top + ch - hh, bw, hh)
        if (uh > 0) { ctx.fillStyle = '#F56C6C'; ctx.fillRect(x, pad.top + ch - hh - uh, bw, uh) }
        ctx.fillStyle = '#666'; ctx.font = '10px Arial'; ctx.textAlign = 'center'
        ctx.fillText(d.date ? d.date.slice(5) : '', pad.left + gap * i + gap / 2, h - 10)
      })

      // legend
      ctx.fillStyle = '#67C23A'; ctx.fillRect(pad.left, h - 24, 10, 10)
      ctx.fillStyle = '#666'; ctx.font = '11px Arial'; ctx.textAlign = 'left'; ctx.fillText('👍 有帮助', pad.left + 14, h - 14)
      ctx.fillStyle = '#F56C6C'; ctx.fillRect(pad.left + 80, h - 24, 10, 10)
      ctx.fillText('👎 没帮助', pad.left + 94, h - 14)
    },
    formatDate(v) {
      if (!v) return ''
      return new Date(v).toLocaleString()
    }
  }
}
</script>

<style scoped>
.feedback-admin { max-width: 1100px; margin: 0 auto; padding: 24px; }
h1 { margin-bottom: 24px; color: #303133; }
.card-row { display: flex; gap: 16px; margin-bottom: 24px; }
.card { flex: 1; background: #fff; border-radius: 10px; padding: 20px; text-align: center; box-shadow: 0 1px 6px rgba(0,0,0,.06); }
.card.green { border-left: 4px solid #67C23A; }
.card.red { border-left: 4px solid #F56C6C; }
.card.blue { border-left: 4px solid #2E75B6; }
.card-num { font-size: 36px; font-weight: 700; color: #303133; }
.card-label { font-size: 14px; color: #909399; margin-top: 4px; }
.section { background: #fff; border-radius: 10px; padding: 20px; margin-bottom: 16px; box-shadow: 0 1px 6px rgba(0,0,0,.06); }
.section h2 { margin-top: 0; }
table { width: 100%; border-collapse: collapse; }
td, th { border: 1px solid #ebeef5; padding: 10px 12px; font-size: 13px; }
th { background: #f5f7fa; text-align: left; }
.question-cell { max-width: 350px; word-break: break-all; }
.num { text-align: center; width: 60px; }
.green-txt { color: #67C23A; font-weight: 600; }
.red-txt { color: #F56C6C; font-weight: 600; }
.time-cell { white-space: nowrap; font-size: 12px; color: #909399; }
.empty-hint { color: #C0C4CC; font-size: 14px; padding: 16px 0; text-align: center; }
.btn { padding: 8px 16px; border: none; border-radius: 6px; background: #2E75B6; color: #fff; font-size: 13px; cursor: pointer; margin-top: 8px; }

.row-warn { background: #fef9e7; }
.row-warn:hover { background: #fdf3d0; }

.answer-preview { margin-top: 8px; padding: 8px 10px; background: #f5f7fa; border-radius: 6px; font-size: 12px; color: #606266; line-height: 1.5; max-height: 120px; overflow-y: auto; white-space: pre-wrap; }

.list-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; flex-wrap: wrap; gap: 8px; }
.filter-tabs { display: flex; gap: 4px; }
.filter-tabs button { border: 1px solid #dcdfe6; border-radius: 6px; padding: 5px 14px; background: #fff; font-size: 12px; cursor: pointer; color: #606266; }
.filter-tabs button.active { background: #2E75B6; color: #fff; border-color: #2E75B6; }

.src-tag { font-size: 10px; padding: 1px 6px; border-radius: 4px; background: #f0f2f5; color: #606266; }

.page-row { display: flex; gap: 12px; align-items: center; justify-content: center; margin-top: 16px; font-size: 13px; }
.page-row button { border: 1px solid #dcdfe6; border-radius: 6px; padding: 4px 12px; background: #fff; cursor: pointer; }
.page-row button:disabled { opacity: .4; cursor: default; }

.ai-section { border-left: 4px solid #67C23A; }
.ai-text { color: #606266; line-height: 1.8; font-size: 14px; }
</style>
