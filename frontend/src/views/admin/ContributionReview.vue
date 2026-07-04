<template>
  <div class="contrib-page">
    <div class="page-header">
      <h1>📝 投稿审核</h1>
    </div>

    <!-- 统计栏 -->
    <div class="stats-bar">
      <div class="stat-item">⏳ 待审核 <strong>{{ pendingList.length }}</strong> 条</div>
      <div class="stat-item">📋 已驳回 <strong>{{ rejectedTotal }}</strong> 条</div>
    </div>

    <!-- Tab 切换 -->
    <div class="tab-row">
      <button :class="{active: tab==='pending'}" @click="switchTab('pending')">⏳ 待审核</button>
      <button :class="{active: tab==='rejected'}" @click="switchTab('rejected')">📋 已驳回</button>
    </div>

    <!-- Toast -->
    <div v-if="msg.text" :class="['toast', 'toast-'+msg.type]">{{ msg.text }}</div>

    <!-- 待审核列表 -->
    <template v-if="tab==='pending'">
      <div v-if="pendingList.length===0" class="empty-state">暂无待审核投稿 🎉</div>
      <div v-else class="card-list">
        <div v-for="item in pendingList" :key="item.id" class="contrib-card">
          <div class="card-header">
            <span class="card-id">#{{ item.id }}</span>
            <span class="card-user">👤 {{ item.userId || '匿名' }}</span>
            <span class="card-time">{{ formatDate(item.createdAt) }}</span>
          </div>

          <div class="card-body">
            <div class="field-row" @click="startEdit(item, 'question')">
              <span class="field-label">❓ 问题</span>
              <div v-if="editingId!==item.id || editingField!=='question'" class="field-val editable">{{ item.question }}</div>
              <textarea v-else v-model="editQuestion" class="edit-area" rows="2" @click.stop></textarea>
            </div>
            <div class="field-row" @click="startEdit(item, 'answer')">
              <span class="field-label">💬 回答</span>
              <div v-if="editingId!==item.id || editingField!=='answer'" class="field-val editable">{{ item.answer }}</div>
              <textarea v-else v-model="editAnswer" class="edit-area" rows="3" @click.stop></textarea>
            </div>
          </div>

          <div class="card-actions">
            <select v-model="item.category" class="act-select">
              <option value="">选分类...</option>
              <option v-for="cat in categories" :key="cat" :value="cat">{{ cat }}</option>
            </select>
            <input v-model="item.keywords" placeholder="关键词" class="act-input" />
            <button class="btn-approve" @click="approve(item)">✅ 通过</button>
            <button class="btn-reject" @click="openReject(item)">⏭ 驳回</button>
          </div>
        </div>
      </div>
    </template>

    <!-- 已驳回列表 -->
    <template v-if="tab==='rejected'">
      <div v-if="rejectedList.length===0" class="empty-state">暂无已驳回投稿</div>
      <div v-else class="card-list">
        <div v-for="item in rejectedList" :key="item.id" class="contrib-card rejected">
          <div class="card-header">
            <span class="card-id">#{{ item.id }}</span>
            <span class="card-user">👤 {{ item.userId || '匿名' }}</span>
            <span class="card-time">{{ formatDate(item.createdAt) }}</span>
            <span class="card-reason" v-if="item.reviewReason">驳回原因：{{ item.reviewReason }}</span>
          </div>
          <div class="card-body">
            <div class="field-row"><span class="field-label">❓</span><span class="field-val">{{ item.question }}</span></div>
            <div class="field-row"><span class="field-label">💬</span><span class="field-val">{{ item.answer }}</span></div>
          </div>
          <div class="card-actions">
            <button class="btn-reopen" @click="reopen(item)">🔄 重新提交审核</button>
          </div>
        </div>
      </div>
    </template>

    <!-- 驳回弹窗 -->
    <div v-if="rejectModalVisible" class="modal-overlay" @click.self="closeReject">
      <div class="modal">
        <h3>驳回原因</h3>
        <textarea v-model="rejectReason" placeholder="请输入驳回原因..." class="reject-textarea"></textarea>
        <div class="modal-actions">
          <button class="btn-cancel" @click="closeReject">取消</button>
          <button class="btn-danger" @click="confirmReject">确认驳回</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'ContributionReview',
  data() {
    return {
      tab: 'pending',
      pendingList: [], rejectedList: [], rejectedTotal: 0,
      loading: false,
      rejectModalVisible: false, rejectReason: '', currentRejectItem: null,
      editingId: null, editingField: null, editQuestion: '', editAnswer: '',
      msg: { text: '', type: 'success' },
      categories: ['课程与选课','考试与成绩','图书馆','奖学金与资助','毕业与实习','食堂餐饮','宿舍住宿','交通出行','校园卡证','校园服务','医疗健康','网络与IT','行政规章','社团活动','招生与就业','学校概况','其他'],
    }
  },
  mounted() { this.loadPending(); this.loadRejected() },
  methods: {
    getAuthHeaders() { const t=localStorage.getItem('token'); return t?{token:t}:{} },
    showMsg(text,type='success') { this.msg={text,type}; setTimeout(()=>this.msg.text='',2500) },

    async loadPending() {
      try {
        const r = await fetch('/api/contribute/pending', { headers:this.getAuthHeaders() })
        const d = await r.json()
        if (d.code===200) this.pendingList = (d.data||[]).map(i=>({...i, category:'', keywords:''}))
      } catch(e) { }
    },
    async loadRejected() {
      try {
        const r = await fetch('/api/contribute/rejected', { headers:this.getAuthHeaders() })
        const d = await r.json()
        if (d.code===200) { this.rejectedList = d.data||[]; this.rejectedTotal = this.rejectedList.length }
      } catch(e) { }
    },

    switchTab(t) { this.tab = t; if (t==='rejected') this.loadRejected() },

    startEdit(item, field) {
      this.editingId = item.id; this.editingField = field
      this.editQuestion = item.question; this.editAnswer = item.answer
    },

    async approve(item) {
      if (!item.category || !item.category.trim()) { this.showMsg('请先选择分类','error'); return }
      try {
        const body = { action:'approve', category:item.category, keywords:item.keywords||'' }
        if (this.editingId===item.id) {
          if (this.editQuestion!==item.question) body.title = this.editQuestion.trim()
          if (this.editAnswer!==item.answer) body.content = this.editAnswer.trim()
        }
        const r = await fetch(`/api/contribute/${item.id}/review`, {
          method:'PUT', headers:{'Content-Type':'application/json', ...this.getAuthHeaders()}, body:JSON.stringify(body)
        })
        const d = await r.json()
        if (d.code===200) { this.editingId=null; this.showMsg('已通过并入库'); this.loadPending() }
        else this.showMsg(d.message||'失败','error')
      } catch(e) { this.showMsg('网络错误','error') }
    },

    openReject(item) { this.currentRejectItem=item; this.rejectReason=''; this.rejectModalVisible=true },
    closeReject() { this.rejectModalVisible=false; this.currentRejectItem=null; this.rejectReason='' },
    async confirmReject() {
      if (!this.currentRejectItem) return
      try {
        const r = await fetch(`/api/contribute/${this.currentRejectItem.id}/review`, {
          method:'PUT', headers:{'Content-Type':'application/json', ...this.getAuthHeaders()},
          body:JSON.stringify({ action:'reject', reason:this.rejectReason||'' })
        })
        const d = await r.json()
        if (d.code===200) { this.showMsg('已驳回'); this.loadPending(); this.loadRejected() }
        else this.showMsg(d.message||'失败','error')
      } catch(e) { this.showMsg('网络错误','error') }
      finally { this.closeReject() }
    },

    async reopen(item) {
      try {
        const r = await fetch(`/api/contribute/${item.id}/review`, {
          method:'PUT', headers:{'Content-Type':'application/json', ...this.getAuthHeaders()},
          body:JSON.stringify({ action:'approve', category:item.category||'综合咨询', keywords:item.keywords||'' })
        })
        const d = await r.json()
        if (d.code===200) { this.showMsg('已重新提交并入库'); this.loadPending(); this.loadRejected() }
        else this.showMsg(d.message||'失败','error')
      } catch(e) { this.showMsg('网络错误','error') }
    },

    formatDate(v) { if(!v) return ''; return new Date(v).toLocaleString() },
  }
}
</script>

<style scoped>
.contrib-page { max-width: 1000px; margin:0 auto; padding:20px; }
.page-header { margin-bottom:12px; }
.page-header h1 { margin:0; font-size:22px; }
.stats-bar { display:flex; gap:20px; margin-bottom:12px; font-size:13px; color:#909399; }
.stats-bar strong { color:#303133; font-size:20px; }
.tab-row { display:flex; gap:8px; margin-bottom:16px; }
.tab-row button { border:1px solid #dcdfe6; border-radius:8px; padding:6px 20px; background:#fff; cursor:pointer; font-size:13px; }
.tab-row button.active { background:#2E75B6; color:#fff; border-color:#2E75B6; }
.empty-state { text-align:center; padding:60px; color:#C0C4CC; font-size:16px; }
.toast { position:fixed; top:16px; right:16px; z-index:3000; padding:10px 20px; border-radius:8px; color:#fff; font-size:13px; }
.toast-success { background:#67c23a; } .toast-error { background:#f56c6c; }

.card-list { display:grid; gap:12px; }
.contrib-card { background:#fff; border-radius:12px; padding:16px; box-shadow:0 2px 12px rgba(0,0,0,.06); }
.contrib-card.rejected { opacity:.7; border-left:4px solid #f56c6c; }
.card-header { display:flex; gap:12px; align-items:center; margin-bottom:10px; font-size:12px; color:#909399; flex-wrap:wrap; }
.card-id { font-weight:700; color:#2E75B6; }
.card-reason { color:#f56c6c; margin-left:auto; }
.card-body { display:grid; gap:8px; margin-bottom:12px; }
.field-row { display:flex; gap:8px; align-items:flex-start; }
.field-label { font-size:12px; color:#909399; flex-shrink:0; width:40px; padding-top:2px; }
.field-val { flex:1; font-size:13px; color:#303133; line-height:1.5; white-space:pre-wrap; }
.field-val.editable { cursor:pointer; padding:2px 4px; border-radius:4px; }
.field-val.editable:hover { background:#f0f4f7; }
.edit-area { flex:1; border:2px solid #2E75B6; border-radius:4px; padding:4px 6px; font-size:13px; font-family:inherit; resize:vertical; box-sizing:border-box; }
.card-actions { display:flex; gap:8px; align-items:center; flex-wrap:wrap; padding-top:10px; border-top:1px solid #f0f2f5; }
.act-select { border:1px solid #dcdfe6; border-radius:6px; padding:5px 10px; font-size:12px; }
.act-input { border:1px solid #dcdfe6; border-radius:6px; padding:5px 10px; font-size:12px; flex:1; min-width:100px; }
.btn-approve { border:none; border-radius:6px; padding:6px 14px; background:#27ae60; color:#fff; font-size:12px; cursor:pointer; }
.btn-reject { border:1px solid #dcdfe6; border-radius:6px; padding:6px 14px; background:#fff; font-size:12px; cursor:pointer; }
.btn-reopen { border:1px solid #dcdfe6; border-radius:6px; padding:6px 14px; background:#e6f7ff; color:#1890ff; font-size:12px; cursor:pointer; }
.btn-cancel { border:1px solid #dcdfe6; border-radius:6px; padding:6px 14px; background:#fff; font-size:12px; cursor:pointer; }
.btn-danger { border:none; border-radius:6px; padding:6px 14px; background:#f56c6c; color:#fff; font-size:12px; cursor:pointer; }

.modal-overlay { position:fixed; top:0; left:0; right:0; bottom:0; background:rgba(0,0,0,.5); display:flex; align-items:center; justify-content:center; z-index:1000; }
.modal { background:#fff; border-radius:12px; padding:24px; width:420px; }
.modal h3 { margin:0 0 12px; }
.reject-textarea { width:100%; height:120px; margin:8px 0; padding:10px; border:1px solid #dcdfe6; border-radius:8px; font-size:13px; resize:vertical; box-sizing:border-box; }
.modal-actions { display:flex; justify-content:flex-end; gap:8px; margin-top:12px; }
</style>
