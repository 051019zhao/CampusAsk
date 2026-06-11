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
        <div class="card-label">总反馈数</div>
      </div>
    </div>

    <!-- 图表区 -->
    <div style="display:flex; gap:20px; margin-bottom:20px; flex-wrap:wrap">
      <div class="section" style="flex:1; min-width:350px">
        <h2>好评率分布</h2>
        <canvas id="pieChart" width="300" height="260"></canvas>
      </div>
      <div class="section" style="flex:1; min-width:400px">
        <h2>各来源好评/差评对比</h2>
        <canvas id="barChart" width="400" height="260"></canvas>
      </div>
    </div>

    <!-- AI 总结 -->
    <div class="section" style="border-left:4px solid #2E75B6; padding:16px; margin-bottom:20px;">
      <h2 style="margin-top:0">🤖 AI 反馈总结</h2>
      <p v-if="aiSummary" style="color:#606266;line-height:1.8">{{ aiSummary }}</p>
      <p v-else style="color:#C0C4CC">加载中...</p>
      <button class="btn" @click="loadSummary">刷新总结</button>
    </div>

    <!-- 按问题分组 -->
    <div class="section">
      <h2>📊 按问题分组</h2>
      <table v-if="grouped.length">
        <thead>
          <tr><th>问题</th><th>👍 有帮助</th><th>👎 没帮助</th><th>总计</th></tr>
        </thead>
        <tbody>
          <tr v-for="(item, i) in grouped" :key="i">
            <td class="question-cell">{{ item.question }}</td>
            <td class="num green-txt">{{ item.helpful }}</td>
            <td class="num red-txt">{{ item.unhelpful }}</td>
            <td class="num">{{ item.total }}</td>
          </tr>
        </tbody>
      </table>
      <p v-else style="color:#C0C4CC">暂无反馈数据</p>
    </div>

    <!-- 最近反馈列表 -->
    <div class="section">
      <h2>📝 最近反馈记录</h2>
      <table v-if="list.length">
        <thead>
          <tr><th>问题</th><th>反馈</th><th>来源</th><th>时间</th></tr>
        </thead>
        <tbody>
          <tr v-for="(item, i) in list.slice(0,20)" :key="i">
            <td class="question-cell">{{ item.question }}</td>
            <td>{{ item.helpful == 1 ? '👍 有帮助' : '👎 没帮助' }}</td>
            <td>{{ item.source }}</td>
            <td class="time-cell">{{ formatDate(item.created_at) }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script>
export default {
  name: "FeedbackReview",
  data() {
    return { stats: {}, list: [], grouped: [], sourceData: [], aiSummary: "" }
  },
  mounted() { this.loadAll() },
  methods: {
    getAuthHeaders() {
      const token = localStorage.getItem("token")
      return token ? { token } : {}
    },
    async loadAll() {
      try {
        const [s, l, g, src] = await Promise.all([
          fetch("/api/admin/feedback/stats", { headers: this.getAuthHeaders() }),
          fetch("/api/admin/feedback/list", { headers: this.getAuthHeaders() }),
          fetch("/api/admin/feedback/grouped", { headers: this.getAuthHeaders() }),
          fetch("/api/admin/feedback/by-source", { headers: this.getAuthHeaders() }),
        ])
        const sd=await s.json(), ld=await l.json(), gd=await g.json(), srcd=await src.json()
        if(sd.code===200) this.stats=sd.data
        if(ld.code===200) this.list=ld.data
        if(gd.code===200) this.grouped=gd.data
        if(srcd.code===200) this.sourceData=srcd.data
        this.loadSummary()
        this.$nextTick(() => { this.drawPie(); this.drawBar() })
      } catch(e) {}
    },
    async loadSummary() {
      try {
        const r = await fetch("/api/admin/feedback/summary", { headers: this.getAuthHeaders() })
        const d = await r.json()
        if(d.code===200) this.aiSummary = d.data
      } catch(e) {}
    },
    sourceLabel(s) {
      const map={knowledge:'本地检索',ai_knowledge:'AI检索数据库',ai_search:'AI联网搜索',ai_web:'AI自主回答',fallback:'降级兜底',rejected:'超出服务范围'}
      return map[s]||s||'未知'
    },
    drawPie() {
      const c=document.getElementById('pieChart')
      if(!c||!this.stats.total) return
      const ctx=c.getContext('2d'), w=c.width, h=c.height, cx=w/2, cy=h/2, r=80
      const hh=this.stats.helpful||0, uh=this.stats.unhelpful||0
      const total=hh+uh||1
      // 好评（绿）
      ctx.beginPath(); ctx.moveTo(cx,cy)
      ctx.arc(cx,cy,r,-Math.PI/2,-Math.PI/2+(hh/total)*Math.PI*2)
      ctx.closePath(); ctx.fillStyle='#67C23A'; ctx.fill()
      // 差评（红）
      ctx.beginPath(); ctx.moveTo(cx,cy)
      ctx.arc(cx,cy,r,-Math.PI/2+(hh/total)*Math.PI*2,-Math.PI/2+Math.PI*2)
      ctx.closePath(); ctx.fillStyle='#F56C6C'; ctx.fill()
      // 中间文字
      ctx.fillStyle='#303133'; ctx.font='bold 22px Arial'; ctx.textAlign='center'
      ctx.fillText(Math.round(hh/total*100)+'%', cx, cy+6)
      // 图例
      ctx.font='13px Arial'
      ctx.fillStyle='#67C23A'; ctx.fillRect(cx-80, h-40, 12, 12)
      ctx.fillStyle='#606266'; ctx.textAlign='left'; ctx.fillText('有帮助 '+hh, cx-64, h-30)
      ctx.fillStyle='#F56C6C'; ctx.fillRect(cx+30, h-40, 12, 12)
      ctx.fillText('没帮助 '+uh, cx+46, h-30)
    },
    drawBar() {
      const c=document.getElementById('barChart')
      if(!c||!this.sourceData.length) return
      const ctx=c.getContext('2d'), w=c.width, h=c.height
      const pad={top:20,right:20,bottom:50,left:80}
      const cw=w-pad.left-pad.right, ch=h-pad.top-pad.bottom
      const maxVal=Math.max(...this.sourceData.map(d=>d.total||0),1)
      const bw=Math.min(40, cw/this.sourceData.length-10)
      const gap=cw/this.sourceData.length
      ctx.clearRect(0,0,w,h)
      // 网格
      for(let i=0;i<=4;i++){
        const y=pad.top+ch/4*i
        ctx.strokeStyle='#eee'; ctx.beginPath(); ctx.moveTo(pad.left,y); ctx.lineTo(w-pad.right,y); ctx.stroke()
        ctx.fillStyle='#999'; ctx.font='11px Arial'; ctx.textAlign='right'
        ctx.fillText(Math.round(maxVal*(1-i/4)), pad.left-8, y+4)
      }
      this.sourceData.forEach((d,i)=>{
        const x=pad.left+gap*i+gap/2-bw
        const hhRatio=(d.helpful||0)/maxVal*ch
        const uhRatio=(d.unhelpful||0)/maxVal*ch
        // 好评柱
        ctx.fillStyle='#67C23A'
        ctx.fillRect(x, pad.top+ch-hhRatio, bw, hhRatio)
        // 差评柱（叠在上面）
        if(uhRatio>0){
          ctx.fillStyle='#F56C6C'
          ctx.fillRect(x, pad.top+ch-hhRatio-uhRatio, bw, uhRatio)
        }
        // 标签
        ctx.fillStyle='#333'; ctx.font='10px Arial'; ctx.textAlign='center'
        ctx.fillText(this.sourceLabel(d.source), pad.left+gap*i+gap/2, h-8)
      })
    },
    formatDate(v) {
      if(!v) return ''
      return new Date(v).toLocaleString()
    }
  }
}
</script>

<style scoped>
.feedback-admin { max-width:1100px; margin:0 auto; padding:24px; }
h1 { margin-bottom:24px; color:#303133; }
.card-row { display:flex; gap:16px; margin-bottom:24px; }
.card { flex:1; background:#fff; border-radius:10px; padding:20px; text-align:center; box-shadow:0 1px 6px rgba(0,0,0,0.06); }
.card.green { border-left:4px solid #67C23A; }
.card.red { border-left:4px solid #F56C6C; }
.card-num { font-size:36px; font-weight:700; color:#303133; }
.card-label { font-size:14px; color:#909399; margin-top:4px; }
.section { background:#fff; border-radius:10px; padding:20px; margin-bottom:20px; box-shadow:0 1px 6px rgba(0,0,0,0.06); }
.section h2 { margin-top:0; }
table { width:100%; border-collapse:collapse; }
td,th { border:1px solid #ebeef5; padding:10px 12px; font-size:13px; }
th { background:#f5f7fa; text-align:left; }
.question-cell { max-width:300px; word-break:break-all; }
.num { text-align:center; width:80px; }
.green-txt { color:#67C23A; font-weight:600; }
.red-txt { color:#F56C6C; font-weight:600; }
.time-cell { white-space:nowrap; }
.btn { padding:8px 16px; border:none; border-radius:6px; background:#2E75B6; color:#fff; font-size:13px; cursor:pointer; margin-top:8px; }
</style>
