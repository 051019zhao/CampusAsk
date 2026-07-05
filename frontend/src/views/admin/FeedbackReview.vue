<template>
  <div class="fb-page">
    <div class="fb-header">
      <h1>📋 用户反馈管理</h1>
      <button class="btn-outline" @click="exportCsv">📥 导出反馈</button>
    </div>

    <!-- 统计卡片 -->
    <div class="card-row">
      <div class="card green"><div class="card-num">{{ stats.helpful||0 }}</div><div class="card-label">👍 有帮助</div></div>
      <div class="card red"><div class="card-num">{{ stats.unhelpful||0 }}</div><div class="card-label">👎 没帮助</div></div>
      <div class="card"><div class="card-num">{{ stats.total||0 }}</div><div class="card-label">📊 总反馈</div></div>
      <div class="card blue" v-if="stats.total">
        <div class="card-num">{{ Math.round((stats.helpful||0)/Math.max(stats.total||1,1)*100) }}%</div>
        <div class="card-label">📈 好评率</div>
      </div>
    </div>

    <div class="flex-row">
      <div class="section" style="flex:1;min-width:320px"><h2>好评率分布</h2><canvas id="pieChart" width="300" height="240"></canvas></div>
      <div class="section" style="flex:1;min-width:400px"><h2>📈 近7日反馈趋势</h2><canvas id="trendCanvas" width="420" height="240"></canvas></div>
    </div>

    <!-- 按问题分组 -->
    <div class="section">
      <h2>📊 反馈最多的问题</h2>
      <div v-if="!grouped.length" class="empty-hint">暂无</div>
      <div v-else class="group-list">
        <div v-for="(item,i) in grouped" :key="i" class="group-item" :class="{warn:item.unhelpful>=3}">
          <div class="g-rank">{{ i+1 }}</div>
          <div class="g-title" :title="item.question">{{ item.question }}</div>
          <div class="g-bars">
            <div class="g-bar-wrap"><div class="g-bar good" :style="{width:barPct(item.helpful,item.total)}"></div><span>{{ item.helpful }}</span></div>
            <div class="g-bar-wrap"><div class="g-bar bad" :style="{width:barPct(item.unhelpful,item.total)}"></div><span>{{ item.unhelpful }}</span></div>
          </div>
          <button v-if="item.unhelpful>=3" class="btn-sm warn-btn" @click="addToGap(item)">📋 加入缺口</button>
          <span v-if="item.unhelpful>=3" class="g-tag-warn">待优化</span>
        </div>
      </div>
    </div>

    <!-- 反馈记录列表 -->
    <div class="section">
      <div class="list-header">
        <h2 style="margin:0">📝 反馈记录</h2>
        <div class="filter-tabs">
          <button :class="{active:filter==='all'}" @click="switchFilter('all')">全部</button>
          <button :class="{active:filter==='helpful'}" @click="switchFilter('helpful')">👍 有帮助</button>
          <button :class="{active:filter==='unhelpful'}" @click="switchFilter('unhelpful')">👎 没帮助</button>
        </div>
      </div>

      <div v-if="!list.length" class="empty-hint">暂无反馈记录</div>
      <div v-else class="fb-list">
        <div v-for="item in list" :key="item.id" class="fb-item" :class="{neg:item.helpful!=1}" @click="toggleExpand(item)">
          <div class="fb-meta">
            <span class="fb-icon">{{ item.helpful==1?'👍':'👎' }}</span>
            <span class="fb-q">{{ item.question }}</span>
            <span class="fb-src">{{ sourceShort(item.source) }}</span>
            <span class="fb-time">{{ formatDate(item.created_at) }}</span>
          </div>
          <div v-if="item._expanded && item.answer" class="fb-answer">
            <strong>回答内容：</strong>{{ item.answer }}
          </div>
        </div>
      </div>

      <div v-if="listTotal>pageSize" class="page-row">
        <button :disabled="page<=1" @click="goPage(page-1)">上一页</button>
        <span>第 {{ page }}/{{ Math.ceil(listTotal/pageSize) }} 页（{{ listTotal }} 条）</span>
        <button :disabled="page>=Math.ceil(listTotal/pageSize)" @click="goPage(page+1)">下一页</button>
      </div>
    </div>

    <!-- AI 总结 -->
    <div class="section ai-section">
      <div class="list-header"><h2 style="margin:0">🤖 智能分析</h2><button class="btn-text" @click="loadSummary">🔄 刷新</button></div>
      <p v-if="aiSummary" class="ai-text" v-html="formatAi(aiSummary)"></p>
      <p v-else class="empty-hint">加载中...</p>
    </div>

    <div v-if="toast" class="toast">{{ toast }}</div>
  </div>
</template>

<script>
export default {
  name:"FeedbackReview",
  data(){return{
    stats:{},list:[],listTotal:0,grouped:[],sourceData:[],aiSummary:"",trendData:[],
    filter:'all',page:1,pageSize:10,toast:''
  }},
  mounted(){this.loadAll()},
  methods:{
    getAuthHeaders(){const t=localStorage.getItem('token');return t?{token:t}:{}},
    async loadAll(){
      try{
        const[s,g,src,tr]=await Promise.all([
          fetch("/api/admin/feedback/stats",{headers:this.getAuthHeaders()}),
          fetch("/api/admin/feedback/grouped",{headers:this.getAuthHeaders()}),
          fetch("/api/admin/feedback/by-source",{headers:this.getAuthHeaders()}),
          fetch("/api/admin/feedback/trend",{headers:this.getAuthHeaders()})
        ]);
        const sd=await s.json(),gd=await g.json(),srcd=await src.json(),td=await tr.json();
        if(sd.code===200)this.stats=sd.data;
        if(gd.code===200)this.grouped=gd.data;
        if(srcd.code===200)this.sourceData=srcd.data;
        if(td.code===200)this.trendData=td.data;
        await this.loadList();this.loadSummary();
        this.$nextTick(()=>{this.drawPie();this.drawTrend()})
      }catch(e){}
    },
    async loadList(){
      try{
        const r=await fetch(`/api/admin/feedback/list?page=${this.page}&size=${this.pageSize}&filter=${this.filter}`,{headers:this.getAuthHeaders()});
        const d=await r.json();
        if(d.code===200){this.list=(d.data.list||[]).map(i=>({...i,_expanded:false}));this.listTotal=d.data.total||0}
      }catch(e){}
    },
    switchFilter(f){this.filter=f;this.page=1;this.loadList()},
    goPage(p){this.page=p;this.loadList()},
    toggleExpand(item){item._expanded=!item._expanded},
    async loadSummary(){
      try{const r=await fetch("/api/admin/feedback/summary",{headers:this.getAuthHeaders()});const d=await r.json();if(d.code===200)this.aiSummary=d.data}catch(e){}
    },
    barPct(v,total){const m=Math.max(...this.grouped.map(g=>g.helpful+g.unhelpful),1);return Math.round(v/m*100)+'%'},
    sourceShort(s){const m={knowledge:'📚本地',ai_knowledge:'🤖AI库',ai_search:'🌐联网',ai_web:'💡AI答',fallback:'⚠️降级',rejected:'🚫拒绝'};return m[s]||s||'-'},
    async addToGap(item){
      try{
        const r=await fetch('/api/qa/feedback',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({question:item.question,answer:'',source:'feedback',helpful:false})});
        if(r.ok)this.toast='已加入知识缺口';setTimeout(()=>this.toast='',2000)
      }catch(e){this.toast='操作失败'}
    },
    exportCsv(){
      let csv='问题,反馈,来源,时间\n';
      this.list.forEach(i=>{csv+=`"${(i.question||'').replace(/"/g,'""')}","${i.helpful==1?'有帮助':'没帮助'}","${i.source||''}","${i.created_at||''}"\n`});
      const b=new Blob(['﻿'+csv],{type:'text/csv'});
      const a=document.createElement('a');a.href=URL.createObjectURL(b);a.download='feedback_export.csv';a.click()
    },
    formatAi(t){return t?t.replace(/\n/g,'<br>'):''},
    drawPie(){
      const c=document.getElementById('pieChart');if(!c||!this.stats.total)return;
      const ctx=c.getContext('2d'),w=c.width,h=c.height,cx=w/2,cy=h/2,r=70;
      const hh=this.stats.helpful||0,uh=this.stats.unhelpful||0,total=hh+uh||1;
      ctx.beginPath();ctx.moveTo(cx,cy);ctx.arc(cx,cy,r,-Math.PI/2,-Math.PI/2+(hh/total)*Math.PI*2);ctx.closePath();ctx.fillStyle='#67C23A';ctx.fill();
      ctx.beginPath();ctx.moveTo(cx,cy);ctx.arc(cx,cy,r,-Math.PI/2+(hh/total)*Math.PI*2,-Math.PI/2+Math.PI*2);ctx.closePath();ctx.fillStyle='#F56C6C';ctx.fill();
      ctx.fillStyle='#303133';ctx.font='bold 20px Arial';ctx.textAlign='center';ctx.fillText(Math.round(hh/total*100)+'%',cx,cy+6);
      ctx.font='12px Arial';ctx.fillStyle='#67C23A';ctx.fillRect(cx-60,h-36,10,10);ctx.fillStyle='#606266';ctx.textAlign='left';ctx.fillText('有帮助 '+hh,cx-46,h-26);
      ctx.fillStyle='#F56C6C';ctx.fillRect(cx+20,h-36,10,10);ctx.fillText('没帮助 '+uh,cx+34,h-26);
    },
    drawTrend(){
      const c=document.getElementById('trendCanvas');if(!c||!this.trendData.length)return;
      const ctx=c.getContext('2d'),w=c.width,h=c.height,pad={top:20,right:20,bottom:40,left:50};
      const cw=w-pad.left-pad.right,ch=h-pad.top-pad.bottom;
      const maxVal=Math.max(...this.trendData.map(d=>d.total||0),1);
      ctx.clearRect(0,0,w,h);
      for(let i=0;i<=4;i++){const y=pad.top+ch/4*i;ctx.strokeStyle='#eee';ctx.beginPath();ctx.moveTo(pad.left,y);ctx.lineTo(w-pad.right,y);ctx.stroke();ctx.fillStyle='#999';ctx.font='11px Arial';ctx.textAlign='right';ctx.fillText(Math.round(maxVal*(1-i/4)),pad.left-8,y+4)}
      const gap=cw/this.trendData.length,bw=Math.min(28,gap-6);
      this.trendData.forEach((d,i)=>{const x=pad.left+gap*i+(gap-bw)/2,hh=(d.helpful||0)/maxVal*ch,uh=(d.unhelpful||0)/maxVal*ch;ctx.fillStyle='#67C23A';ctx.fillRect(x,pad.top+ch-hh,bw,hh);if(uh>0){ctx.fillStyle='#F56C6C';ctx.fillRect(x,pad.top+ch-hh-uh,bw,uh)}ctx.fillStyle='#666';ctx.font='10px Arial';ctx.textAlign='center';ctx.fillText(d.date?d.date.slice(5):'',pad.left+gap*i+gap/2,h-8)});
      ctx.fillStyle='#67C23A';ctx.fillRect(pad.left,h-22,10,10);ctx.fillStyle='#666';ctx.font='11px Arial';ctx.textAlign='left';ctx.fillText('👍',pad.left+14,h-12);
      ctx.fillStyle='#F56C6C';ctx.fillRect(pad.left+60,h-22,10,10);ctx.fillText('👎',pad.left+74,h-12);
    },
    formatDate(v){if(!v)return'';return new Date(v).toLocaleString()}
  }
}
</script>

<style scoped>
.fb-page{max-width:1100px;margin:0 auto;padding:20px;}
.fb-header{display:flex;justify-content:space-between;align-items:center;margin-bottom:16px;}
.fb-header h1{margin:0;font-size:22px;}
.btn-outline{border:1px solid #dcdfe6;border-radius:8px;padding:6px 16px;background:#fff;font-size:12px;cursor:pointer;color:#606266;}
.btn-text{border:none;background:transparent;color:#909399;font-size:12px;cursor:pointer;}
.card-row{display:flex;gap:12px;margin-bottom:16px;}
.card{flex:1;background:#fff;border-radius:10px;padding:18px;text-align:center;box-shadow:0 1px 6px rgba(0,0,0,.05);}
.card.green{border-left:4px solid #67C23A;}.card.red{border-left:4px solid #F56C6C;}.card.blue{border-left:4px solid #2E75B6;}
.card-num{font-size:32px;font-weight:700;color:#303133;}.card-label{font-size:13px;color:#909399;margin-top:2px;}
.flex-row{display:flex;gap:16px;flex-wrap:wrap;margin-bottom:16px;}
.section{background:#fff;border-radius:10px;padding:20px;margin-bottom:16px;box-shadow:0 1px 6px rgba(0,0,0,.05);}
.section h2{margin:0 0 12px;font-size:16px;}
.empty-hint{color:#C0C4CC;font-size:14px;text-align:center;padding:30px 0;}

.group-list{display:flex;flex-direction:column;}
.group-item{display:flex;align-items:center;gap:10px;padding:10px 0;border-bottom:1px solid #f5f7fa;}
.group-item.warn{background:#fef9e7;margin:0 -8px;padding:10px 8px;border-radius:6px;}
.g-rank{width:24px;font-size:14px;font-weight:700;color:#909399;text-align:center;}
.g-title{flex:1;font-size:13px;color:#303133;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;}
.g-bars{display:flex;gap:4px;width:120px;flex-shrink:0;}
.g-bar-wrap{flex:1;display:flex;align-items:center;gap:2px;height:18px;background:#f5f7fa;border-radius:4px;overflow:hidden;}
.g-bar{height:100%;border-radius:4px;}.g-bar.good{background:#67C23A;}.g-bar.bad{background:#F56C6C;}
.g-bar-wrap span{font-size:10px;color:#909399;padding-right:2px;}
.g-tag-warn{font-size:10px;color:#E6A23C;background:#fef3e2;padding:1px 6px;border-radius:4px;}
.btn-sm{border:none;border-radius:4px;padding:2px 8px;font-size:10px;cursor:pointer;}
.warn-btn{background:#fef3e2;color:#E6A23C;}

.list-header{display:flex;align-items:center;justify-content:space-between;margin-bottom:12px;}
.filter-tabs{display:flex;gap:4px;}
.filter-tabs button{border:1px solid #dcdfe6;border-radius:6px;padding:5px 14px;background:#fff;font-size:12px;cursor:pointer;color:#606266;}
.filter-tabs button.active{background:#2E75B6;color:#fff;border-color:#2E75B6;}

.fb-list{display:flex;flex-direction:column;}
.fb-item{padding:10px 0;border-bottom:1px solid #f5f7fa;cursor:pointer;}
.fb-item:hover{background:#fafbfc;}
.fb-item.neg{border-left:3px solid #F56C6C;padding-left:8px;}
.fb-meta{display:flex;align-items:center;gap:8px;}
.fb-icon{font-size:14px;width:28px;text-align:center;}
.fb-q{flex:1;font-size:13px;color:#303133;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;}
.fb-src{font-size:10px;color:#909399;background:#f0f2f5;padding:1px 6px;border-radius:4px;}
.fb-time{font-size:11px;color:#C0C4CC;}
.fb-answer{margin-top:8px;padding:10px;background:#f5f7fa;border-radius:6px;font-size:12px;color:#606266;line-height:1.6;white-space:pre-wrap;max-height:150px;overflow-y:auto;}

.page-row{display:flex;gap:12px;align-items:center;justify-content:center;margin-top:16px;font-size:13px;}
.page-row button{border:1px solid #dcdfe6;border-radius:6px;padding:4px 12px;background:#fff;cursor:pointer;}
.page-row button:disabled{opacity:.4;}

.ai-section{border-left:4px solid #67C23A;}
.ai-text{color:#606266;line-height:2;font-size:14px;}
.toast{position:fixed;top:16px;right:16px;z-index:3000;padding:10px 20px;border-radius:8px;background:#67c23a;color:#fff;font-size:13px;}
</style>
