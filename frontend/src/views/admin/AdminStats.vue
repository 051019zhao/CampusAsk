<template>
  <div class="stats-dashboard">
    <h1>📊 数据统计面板</h1>

    <!-- 今日查询量卡片 -->
    <div class="card-row">
      <div class="card highlight">
        <div class="card-title">今日查询量</div>
        <div class="card-value">{{ daily.count || 0 }}</div>
        <div class="card-sub">{{ daily.date }}</div>
      </div>
      <div class="card">
        <div class="card-title">知识库条目</div>
        <div class="card-value">-</div>
        <div class="card-sub">总条目数</div>
      </div>
      <div class="card">
        <div class="card-title">用户总数</div>
        <div class="card-value">-</div>
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

    <!-- 来源占比 -->
    <div class="chart-section">
      <h2>🥧 检索来源占比</h2>
      <div class="chart-container half">
        <canvas id="sourceChart" height="80"></canvas>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: "AdminStats",
  data() {
    return {
      daily: { date: "", count: 0 },
      trend: [],
      source: [],
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
        const [dailyRes, trendRes, sourceRes] = await Promise.all([
          fetch("/api/admin/stats/daily", { headers: this.getAuthHeaders() }),
          fetch("/api/admin/stats/trend", { headers: this.getAuthHeaders() }),
          fetch("/api/admin/stats/source", { headers: this.getAuthHeaders() }),
        ])
        const d = await dailyRes.json()
        const t = await trendRes.json()
        const s = await sourceRes.json()
        if (d.code === 200) this.daily = d.data
        if (t.code === 200) this.trend = t.data
        if (s.code === 200) this.source = s.data
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
</style>
