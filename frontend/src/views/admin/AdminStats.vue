<template>
  <div class="stats-dashboard">
    <h1>📊 数据统计面板</h1>

    <!-- 今日查询量卡片 -->
    <div class="card-row">
      <div class="card highlight">
        <div class="card-title">今日查询量</div>
        <div class="card-value">{{ overview.todayCount ?? 0 }}</div>
        <div class="card-sub">{{ overview.date }}</div>
      </div>
      <div class="card">
        <div class="card-title">知识库条目</div>
        <div class="card-value">{{ overview.knowledgeCount ?? '-' }}</div>
        <div class="card-sub">总条目数</div>
      </div>
      <div class="card">
        <div class="card-title">用户总数</div>
        <div class="card-value">{{ overview.userCount ?? '-' }}</div>
        <div class="card-sub">注册用户</div>
      </div>
    </div>

    <!-- 趋势图 -->
    <div class="chart-section">
      <h2>📈 近7日查询趋势</h2>
      <div class="chart-container">
        <canvas id="trendChart" height="80"></canvas>
      </div>
    </div>

    <!-- 来源占比 + 知识库分类 -->
    <div style="display:flex; gap:24px; flex-wrap:wrap">
      <div class="chart-section" style="flex:1; min-width:350px">
        <h2>检索来源占比</h2>
        <div class="chart-container half">
          <canvas id="sourceChart" height="80"></canvas>
        </div>
      </div>
      <div class="chart-section" style="flex:1; min-width:350px">
        <h2>知识库分类统计</h2>
        <div v-if="!knowledgeCategories.length" class="empty-hint">暂无数据</div>
        <div v-else class="bar-chart">
          <div v-for="(item, i) in knowledgeCategories" :key="i" class="bar-row">
            <span class="bar-label">{{ item.name || '未分类' }}</span>
            <div class="bar-track"><div class="bar-fill" :style="{width: barWidth(item.value)}"></div></div>
            <span class="bar-val">{{ item.value }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 话题分布 -->
    <div class="chart-section">
      <h2>今日提问话题分布</h2>
      <div v-if="!topics.length" class="empty-hint">今日暂无提问</div>
      <div v-else class="bar-chart">
        <div v-for="(item, i) in topics" :key="i" class="bar-row">
          <span class="bar-label">{{ item.name }}</span>
          <div class="bar-track"><div class="bar-fill" :style="{width: barWidth(item.value), background: topicColor(item.name)}"></div></div>
          <span class="bar-val">{{ item.value }}</span>
        </div>
      </div>
    </div>

    <!-- AI 报告 -->
    <div class="chart-section ai-report">
      <h2>AI 每日报告</h2>
      <p v-if="report" class="report-text">{{ report }}</p>
      <p v-else class="empty-hint">加载中...</p>
    </div>
  </div>
</template>

<script>
export default {
  name: "AdminStats",
  data() {
    return {
      overview: { todayCount: 0, userCount: 0, knowledgeCount: 0, date: "" },
      trend: [],
      source: [],
      knowledgeCategories: [],
      topics: [],
      report: "",
    }
  },
  mounted() {
    this.loadData()
  },
  methods: {
    getAuthHeaders() {
      const token = localStorage.getItem("token")
      return token ? { token } : {}
    },
    async loadData() {
      try {
        const [overviewRes, trendRes, sourceRes, catRes, topicRes, reportRes] = await Promise.all([
          fetch("/api/admin/stats/overview", { headers: this.getAuthHeaders() }),
          fetch("/api/admin/stats/trend", { headers: this.getAuthHeaders() }),
          fetch("/api/admin/stats/source", { headers: this.getAuthHeaders() }),
          fetch("/api/admin/stats/knowledge-categories", { headers: this.getAuthHeaders() }),
          fetch("/api/admin/stats/topics", { headers: this.getAuthHeaders() }),
          fetch("/api/admin/stats/report", { headers: this.getAuthHeaders() }),
        ])
        const o = await overviewRes.json(); const t = await trendRes.json(); const s = await sourceRes.json()
        const c = await catRes.json(); const tp = await topicRes.json(); const r = await reportRes.json()
        if (o.code === 200) this.overview = o.data
        if (t.code === 200) this.trend = t.data
        if (s.code === 200) this.source = s.data
        if (c.code === 200) this.knowledgeCategories = c.data
        if (tp.code === 200) this.topics = tp.data
        if (r.code === 200) this.report = r.data
        this.$nextTick(() => {
          this.drawTrend()
          this.drawSource()
        })
      } catch (err) {
        console.error("加载统计数据失败", err)
      }
    },
    drawTrend() {
      const canvas = document.getElementById("trendChart")
      if (!canvas || !this.trend.length) return
      const ctx = canvas.getContext("2d")
      const w = canvas.parentElement.clientWidth - 40
      canvas.width = w
      const h = 300
      canvas.height = h
      const pad = { top: 20, right: 20, bottom: 50, left: 60 }
      const cw = w - pad.left - pad.right
      const ch = h - pad.top - pad.bottom
      const maxVal = Math.max(...this.trend.map(d => d.count), 1)
      ctx.clearRect(0, 0, w, h)

      // 网格线
      ctx.strokeStyle = "#eee"
      ctx.lineWidth = 1
      for (let i = 0; i <= 4; i++) {
        const y = pad.top + (ch / 4) * i
        ctx.beginPath(); ctx.moveTo(pad.left, y); ctx.lineTo(w - pad.right, y); ctx.stroke()
        ctx.fillStyle = "#999"; ctx.font = "11px Arial"
        ctx.textAlign = "right"; ctx.fillText(Math.round(maxVal * (1 - i / 4)), pad.left - 8, y + 4)
      }

      // 折线
      const step = cw / (this.trend.length - 1)
      ctx.strokeStyle = "#2E75B6"; ctx.lineWidth = 2.5
      ctx.beginPath()
      this.trend.forEach((d, i) => {
        const x = pad.left + step * i
        const y = pad.top + ch - (d.count / maxVal) * ch
        if (i === 0) ctx.moveTo(x, y); else ctx.lineTo(x, y)
      })
      ctx.stroke()

      // 填充
      const lastIdx = this.trend.length - 1
      ctx.lineTo(pad.left + step * lastIdx, pad.top + ch)
      ctx.lineTo(pad.left, pad.top + ch)
      ctx.closePath()
      ctx.fillStyle = "rgba(46,117,182,0.08)"
      ctx.fill()

      // 圆点
      this.trend.forEach((d, i) => {
        const x = pad.left + step * i
        const y = pad.top + ch - (d.count / maxVal) * ch
        ctx.beginPath(); ctx.arc(x, y, 5, 0, Math.PI * 2)
        ctx.fillStyle = "#2E75B6"; ctx.fill()
        ctx.fillStyle = "#333"; ctx.font = "11px Arial"; ctx.textAlign = "center"
        ctx.fillText(d.date.slice(5), x, h - 8)
        ctx.fillText(d.count, x, y - 12)
      })
    },
    barWidth(val) {
      const max = Math.max(...[...this.knowledgeCategories, ...this.topics].map(d => d.value || 0), 1)
      return Math.round((val / max) * 100) + '%'
    },
    topicColor(name) {
      const map = { '餐饮':'#F56C6C', '宿舍':'#409EFF', '学习':'#67C23A', '交通':'#E6A23C', '校园服务':'#909399', '其他':'#C0C4CC' }
      return map[name] || '#909399'
    },
    drawSource() {
      const canvas = document.getElementById("sourceChart")
      if (!canvas || !this.source.length) return
      const ctx = canvas.getContext("2d")
      const w = 400; const h = 300
      canvas.width = w; canvas.height = h
      const cx = w / 2; const cy = h / 2; const r = 100
      const total = this.source.reduce((s, d) => s + d.value, 0)
      const colors = ["#2E75B6", "#67C23A", "#E6A23C", "#F56C6C", "#909399", "#409EFF"]
      let angle = -Math.PI / 2
      this.source.forEach((d, i) => {
        const slice = (d.value / total) * Math.PI * 2
        ctx.beginPath(); ctx.moveTo(cx, cy)
        ctx.arc(cx, cy, r, angle, angle + slice)
        ctx.closePath()
        ctx.fillStyle = colors[i % colors.length]; ctx.fill()
        ctx.strokeStyle = "#fff"; ctx.lineWidth = 2; ctx.stroke()

        // 标签
        const mid = angle + slice / 2
        const lx = cx + Math.cos(mid) * (r + 40)
        const ly = cy + Math.sin(mid) * (r + 40)
        ctx.fillStyle = "#333"; ctx.font = "12px Arial"; ctx.textAlign = "center"
        ctx.fillText(`${d.name} (${d.value})`, lx, ly)

        angle += slice
      })
    },
  },
}
</script>

<style scoped>
.stats-dashboard { max-width: 1100px; margin: 0 auto; padding: 24px; }
h1 { color: #303133; margin-bottom: 24px; font-size: 24px; }
h2 { color: #606266; margin-bottom: 12px; font-size: 16px; }

.card-row { display: flex; gap: 16px; margin-bottom: 32px; }
.card { flex: 1; background: white; border-radius: 10px; padding: 20px; box-shadow: 0 1px 6px rgba(0,0,0,0.06); text-align: center; }
.card.highlight { border-left: 4px solid #2E75B6; }
.card-title { font-size: 14px; color: #909399; margin-bottom: 8px; }
.card-value { font-size: 36px; font-weight: 700; color: #303133; }
.card-sub { font-size: 12px; color: #C0C4CC; margin-top: 4px; }

.chart-section { background: white; border-radius: 10px; padding: 24px; margin-bottom: 24px; box-shadow: 0 1px 6px rgba(0,0,0,0.06); }
.chart-container canvas { width: 100%; }
.chart-container.half { max-width: 450px; }
.bar-chart { padding: 8px 0; }
.bar-row { display: flex; align-items: center; gap: 12px; margin: 10px 0; }
.bar-label { width: 70px; text-align: right; font-size: 13px; color: #606266; flex-shrink: 0; }
.bar-track { flex: 1; height: 22px; background: #f0f2f5; border-radius: 4px; overflow: hidden; }
.bar-fill { height: 100%; background: #2E75B6; border-radius: 4px; transition: width 0.6s ease; min-width: 4px; }
.bar-val { width: 30px; font-size: 13px; font-weight: 600; color: #303133; }
.empty-hint { color: #C0C4CC; font-size: 14px; padding: 16px 0; }
.ai-report { border-left: 4px solid #67C23A; }
.report-text { font-size: 15px; color: #303133; line-height: 1.8; padding: 8px 0; }
</style>
