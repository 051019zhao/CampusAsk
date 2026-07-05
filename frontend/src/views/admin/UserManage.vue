<template>
  <div class="um-page">
    <h1>👥 用户管理</h1>
    <div class="um-bar">
      <span>共 <strong>{{ filteredList.length }}</strong> 位用户</span>
      <div class="um-search"><input v-model="searchKey" placeholder="搜索账号..." /></div>
    </div>
    <div v-if="!filteredList.length" class="empty">暂无用户</div>
    <div class="um-list">
      <div v-for="u in filteredList" :key="u.id" class="um-card">
        <div class="um-avatar"><img v-if="u.avatarUrl" :src="u.avatarUrl" /><span v-else>{{ (u.nickname||u.username||'?').charAt(0) }}</span></div>
        <div class="um-info">
          <div class="um-name">{{ u.nickname||'未设置' }} <span class="um-role" :class="u.role==1?'admin':'user'">{{ u.role==1?'管理员':'学生' }}</span></div>
          <div class="um-id">@{{ u.username }}</div>
        </div>
        <button class="btn-sm" @click="openPwdModal(u)">🔑 改密</button>
        <button class="btn-sm btn-del" @click="delUser(u)">🗑 删除</button>
      </div>
    </div>

    <!-- 改密弹窗 -->
    <div v-if="pwdModal" class="modal-overlay" @click.self="pwdModal=null">
      <div class="modal">
        <h3>重置密码 — {{ pwdModal.username }}</h3>
        <input v-model="newPwd" placeholder="输入新密码" class="modal-input" />
        <div class="modal-actions">
          <button class="btn-cancel" @click="pwdModal=null">取消</button>
          <button class="btn-ok" @click="doResetPwd">确认重置</button>
        </div>
      </div>
    </div>
    <div v-if="toast" class="toast">{{ toast }}</div>
  </div>
</template>

<script>
export default {
  name:"UserManage",
  data(){return{list:[],toast:'',searchKey:'',pwdModal:null,newPwd:''}},
  computed:{
    filteredList(){const k=this.searchKey.trim().toLowerCase();return k?this.list.filter(u=>u.username.toLowerCase().includes(k)):this.list}
  },
  mounted(){this.load()},
  methods:{
    getAuthHeaders(){const t=localStorage.getItem('token');return t?{token:t}:{}},
    async load(){try{const r=await fetch('/api/admin/stats/users',{headers:this.getAuthHeaders()});const d=await r.json();if(d.code===200)this.list=d.data||[]}catch(e){}},
    openPwdModal(u){this.pwdModal=u;this.newPwd=''},
    async doResetPwd(){
      const pwd=this.newPwd.trim();if(!pwd||pwd.length<6) return alert('密码至少6位')
      try{const r=await fetch(`/api/admin/stats/users/${this.pwdModal.username}/reset-pwd`,{method:'PUT',headers:{'Content-Type':'application/json',...this.getAuthHeaders()},body:JSON.stringify({password:pwd})});const d=await r.json();this.toast=d.code===200?('密码已重置为 '+pwd):'操作失败';this.pwdModal=null;setTimeout(()=>this.toast='',2000)}catch(e){this.toast='操作失败'}
    },
    async delUser(u){
      if(!confirm(`确定删除用户 ${u.username} 吗？此操作不可撤销！`)) return
      try{const r=await fetch(`/api/admin/stats/users/${u.username}`,{method:'DELETE',headers:this.getAuthHeaders()});const d=await r.json();if(d.code===200){this.toast='已删除';this.load();setTimeout(()=>this.toast='',2000)}else this.toast='操作失败'}
      catch(e){this.toast='操作失败'}
    }
  }
}
</script>

<style scoped>
.um-page{max-width:800px;margin:0 auto;padding:20px;}
h1{margin:0 0 12px;font-size:22px;}
.um-bar{display:flex;align-items:center;justify-content:space-between;margin-bottom:16px;font-size:13px;color:#909399;}
.um-bar strong{color:#303133;}
.um-search input{border:1px solid #dcdfe6;border-radius:8px;padding:5px 12px;font-size:12px;outline:none;width:160px;}
.empty{text-align:center;color:#C0C4CC;padding:60px 0;}
.um-list{display:grid;gap:10px;}
.um-card{display:flex;align-items:center;gap:14px;background:#fff;border-radius:12px;padding:14px 18px;box-shadow:0 1px 6px rgba(0,0,0,.05);}
.um-avatar{width:44px;height:44px;border-radius:50%;background:#d4e0e6;display:flex;align-items:center;justify-content:center;overflow:hidden;flex-shrink:0;}
.um-avatar img{width:100%;height:100%;object-fit:cover;}
.um-avatar span{font-size:18px;font-weight:700;color:#3c6476;}
.um-info{flex:1;}
.um-name{font-size:14px;font-weight:600;color:#303133;}
.um-role{font-size:10px;padding:1px 6px;border-radius:4px;margin-left:6px;}
.um-role.admin{background:#fef3e2;color:#e6a23c;}.um-role.user{background:#e6f7ff;color:#1890ff;}
.um-id{font-size:12px;color:#909399;}
.btn-sm{border:1px solid #dcdfe6;border-radius:6px;padding:4px 10px;background:#fff;font-size:11px;cursor:pointer;color:#606266;}
.btn-sm:hover{background:#f5f7fa;}
.btn-del{color:#f56c6c;border-color:#fbc4c4;}.btn-del:hover{background:#fef2f2;}
.toast{position:fixed;top:16px;right:16px;z-index:3000;padding:10px 20px;border-radius:8px;background:#67c23a;color:#fff;font-size:13px;}
.modal-overlay{position:fixed;inset:0;background:rgba(0,0,0,.5);display:flex;align-items:center;justify-content:center;z-index:2000;}
.modal{background:#fff;border-radius:12px;padding:24px;min-width:320px;}
.modal h3{margin:0 0 16px;}
.modal-input{width:100%;padding:8px 12px;border:1px solid #dcdfe6;border-radius:8px;font-size:14px;box-sizing:border-box;margin-bottom:16px;}
.modal-actions{display:flex;justify-content:flex-end;gap:8px;}
.btn-cancel{border:1px solid #dcdfe6;border-radius:6px;padding:6px 14px;background:#fff;font-size:13px;cursor:pointer;}
.btn-ok{border:none;border-radius:6px;padding:6px 14px;background:#2E75B6;color:#fff;font-size:13px;cursor:pointer;}
</style>
