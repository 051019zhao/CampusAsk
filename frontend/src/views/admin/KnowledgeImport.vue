<template>
  <div class="knowledge-page">
    <div class="page-header">
      <h1>📚 知识库管理</h1>
      <div class="header-actions">
        <button class="btn btn-primary" @click="showAddModal = true">＋ 新增知识点</button>
        <button class="btn btn-outline" @click="exportCsv">📥 导出CSV</button>
        <button class="btn btn-outline" @click="$refs.file.click()">📤 批量导入</button>
        <input ref="file" type="file" accept=".xlsx,.xls,.csv" hidden @change="uploadExcel" />
      </div>
    </div>

    <!-- 统计概览 -->
    <div class="stats-bar">
      <div class="stat-item">📚 知识库总数 <strong>{{ allTotal }}</strong> 条</div>
      <div class="stat-item">📂 分类 <strong>{{ categories.length }}</strong> 个</div>
      <div class="stat-item" v-if="keyword || selectedCategory">
        🔍 筛选结果 <strong>{{ total }}</strong> 条
      </div>
      <div class="stat-item" v-else>
        📄 当前页显示 <strong>{{ list.length }}</strong> 条
      </div>
    </div>

    <!-- 搜索 + 分类筛选 -->
    <div class="search-bar">
      <input v-model="keyword" placeholder="搜索标题或关键词..." @keyup.enter="searchData" />
      <select v-model="selectedCategory" @change="searchData">
        <option value="">全部分类</option>
        <option v-for="cat in categories" :key="cat" :value="cat">{{ cat }}</option>
      </select>
      <button class="btn btn-primary" @click="searchData">搜索</button>
      <button class="btn btn-text" @click="resetFilter">清除筛选</button>
    </div>

    <!-- 消息提示 -->
    <div v-if="msg.text" :class="['toast', 'toast-' + msg.type]">{{ msg.text }}</div>

    <!-- 表格 -->
    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            <th style="width:40px">ID</th>
            <th style="width:18%">标题</th>
            <th style="width:10%">分类</th>
            <th style="width:35%">内容</th>
            <th style="width:12%">关键词</th>
            <th style="width:50px">👁</th>
            <th style="width:120px">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="list.length === 0">
            <td colspan="7" style="text-align:center;color:#C0C4CC;padding:40px">暂无数据</td>
          </tr>
          <tr v-for="item in list" :key="item.id" :class="{ editing: editingId === item.id }">
            <td>{{ item.id }}</td>
            <td>
              <input v-if="editingId === item.id" v-model="editForm.title" class="cell-input" />
              <span v-else>{{ item.title }}</span>
            </td>
            <td>
              <select v-if="editingId === item.id" v-model="editForm.category" class="cell-input">
                <option v-for="cat in categories" :key="cat" :value="cat">{{ cat }}</option>
              </select>
              <span v-else class="cat-tag">{{ item.category || '-' }}</span>
            </td>
            <td>
              <textarea v-if="editingId === item.id" v-model="editForm.content" class="cell-textarea" rows="2"></textarea>
              <span v-else class="content-cell">{{ item.content }}</span>
            </td>
            <td>
              <input v-if="editingId === item.id" v-model="editForm.keywords" class="cell-input" />
              <span v-else class="kw-cell">{{ item.keywords || '-' }}</span>
            </td>
            <td class="num">{{ item.viewCount || 0 }}</td>
            <td class="action-cell">
              <template v-if="editingId === item.id">
                <button class="btn-sm btn-sm-success" @click="saveEdit(item.id)">保存</button>
                <button class="btn-sm btn-sm-cancel" @click="cancelEdit">取消</button>
              </template>
              <template v-else>
                <button class="btn-sm btn-sm-edit" @click="startEdit(item)">编辑</button>
                <button class="btn-sm btn-sm-del" @click="showDeleteConfirm(item)">删除</button>
              </template>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 分页 -->
    <div class="pagination" v-if="total > pageSize">
      <button :disabled="page <= 1" @click="goPage(page - 1)">上一页</button>
      <span class="page-info">第 {{ page }} / {{ Math.ceil(total / pageSize) }} 页（共 {{ total }} 条）</span>
      <button :disabled="page >= Math.ceil(total / pageSize)" @click="goPage(page + 1)">下一页</button>
    </div>

    <!-- 新增知识弹窗 -->
    <div v-if="showAddModal" class="modal-overlay" @click.self="showAddModal = false">
      <div class="modal">
        <h3>＋ 新增知识点</h3>
        <div class="form-group">
          <label>标题 <span class="req">*</span></label>
          <input v-model="addForm.title" placeholder="如：食堂开放时间" class="form-input" />
        </div>
        <div class="form-group">
          <label>分类 <span class="req">*</span></label>
          <select v-model="addForm.category" class="form-input">
            <option value="">选择分类...</option>
            <option v-for="cat in categories" :key="cat" :value="cat">{{ cat }}</option>
            <option value="__new__">＋ 新增分类</option>
          </select>
          <input v-if="addForm.category === '__new__'" v-model="addForm.newCategory" placeholder="输入新分类名" class="form-input" style="margin-top:6px" />
        </div>
        <div class="form-group">
          <label>内容 <span class="req">*</span></label>
          <textarea v-model="addForm.content" placeholder="回答内容..." class="form-textarea" rows="4"></textarea>
        </div>
        <div class="form-group">
          <label>关键词</label>
          <input v-model="addForm.keywords" placeholder="逗号分隔，如：食堂,开放,时间" class="form-input" />
        </div>
        <div class="modal-actions">
          <button class="btn btn-cancel" @click="showAddModal = false">取消</button>
          <button class="btn btn-primary" @click="addKnowledge" :disabled="adding">添加</button>
        </div>
      </div>
    </div>

    <!-- 删除确认 -->
    <div v-if="deleteConfirmVisible" class="modal-overlay" @click.self="deleteConfirmVisible = false">
      <div class="modal modal-sm">
        <h3>确认删除</h3>
        <p>确定要删除「{{ deleteItem?.title }}」吗？</p>
        <div class="modal-actions">
          <button class="btn btn-cancel" @click="deleteConfirmVisible = false">取消</button>
          <button class="btn btn-danger" @click="confirmDelete">确认删除</button>
        </div>
      </div>
    </div>

    <!-- 导入错误弹窗 -->
    <div v-if="errorModalVisible" class="modal-overlay" @click.self="errorModalVisible = false">
      <div class="modal">
        <h3>导入错误详情</h3>
        <p class="error-count">共 {{ errorRows.length }} 行导入失败</p>
        <div class="error-list"><div v-for="(e, i) in errorRows" :key="i" class="error-item">{{ e }}</div></div>
        <div class="modal-actions"><button class="btn btn-primary" @click="errorModalVisible = false">知道了</button></div>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: "KnowledgeImport",
  data() {
    return {
      keyword: "", selectedCategory: "", categories: [],
      list: [], total: 0, allTotal: 0, page: 1, pageSize: 15, loading: false,
      editingId: null, editForm: { title: "", content: "", category: "", keywords: "" },
      deleteConfirmVisible: false, deleteItem: null,
      errorModalVisible: false, errorRows: [],
      showAddModal: false, adding: false,
      addForm: { title: "", category: "", newCategory: "", content: "", keywords: "" },
      msg: { text: "", type: "success" },
    }
  },
  mounted() { this.loadCategories(); this.searchData(); this.loadTotalCount() },
  methods: {
    async loadTotalCount() {
      try {
        const r = await fetch("/api/knowledge/list?page=1&size=1", { headers: this.getAuthHeaders() })
        const d = await r.json()
        if (d.code === 200) this.allTotal = d.data?.total || 0
      } catch (e) { }
    },
    getAuthHeaders() { const t = localStorage.getItem("token"); return t ? { token: t } : {} },
    showMsg(text, type = "success") { this.msg = { text, type }; setTimeout(() => this.msg.text = "", 2500) },

    async loadCategories() {
      // V4: 预定义完整分类列表（不从DB动态取，保证完整性）
      this.categories = [
        '课程与选课', '考试与成绩', '图书馆', '奖学金与资助', '毕业与实习',
        '食堂餐饮', '宿舍住宿', '交通出行', '校园卡证', '校园服务',
        '医疗健康', '网络与IT', '行政规章', '社团活动', '招生与就业',
        '学校概况', '其他'
      ]
    },

    async searchData() {
      this.loading = true
      try {
        const p = new URLSearchParams()
        if (this.keyword.trim()) p.append("keyword", this.keyword.trim())
        if (this.selectedCategory) p.append("category", this.selectedCategory)
        p.append("page", this.page); p.append("size", this.pageSize)
        const r = await fetch(`/api/knowledge/list?${p}`, { headers: this.getAuthHeaders() })
        const d = await r.json()
        if (d.code === 200) { this.list = d.data?.list || []; this.total = d.data?.total || 0 }
      } catch (e) { }
      finally { this.loading = false }
    },

    resetFilter() { this.keyword = ""; this.selectedCategory = ""; this.page = 1; this.searchData() },
    goPage(p) { this.page = p; this.searchData() },

    // 新增
    async addKnowledge() {
      if (!this.addForm.title.trim() || !this.addForm.content.trim()) { this.showMsg("标题和内容不能为空", "error"); return }
      let cat = this.addForm.category === "__new__" ? this.addForm.newCategory.trim() : this.addForm.category
      if (!cat) { this.showMsg("请选择或输入分类", "error"); return }
      this.adding = true
      try {
        const r = await fetch("/api/knowledge/", {
          method: "POST", headers: { "Content-Type": "application/json", ...this.getAuthHeaders() },
          body: JSON.stringify({ title: this.addForm.title.trim(), content: this.addForm.content.trim(), category: cat, keywords: this.addForm.keywords.trim() })
        })
        const d = await r.json()
        if (d.code === 200) { this.showMsg("添加成功"); this.showAddModal = false; this.addForm = { title: "", category: "", newCategory: "", content: "", keywords: "" }; this.loadCategories(); this.searchData(); this.loadTotalCount() }
        else this.showMsg(d.message || "添加失败", "error")
      } catch (e) { this.showMsg("网络错误", "error") }
      finally { this.adding = false }
    },

    // 编辑
    startEdit(item) { this.editingId = item.id; this.editForm = { title: item.title, content: item.content, category: item.category, keywords: item.keywords } },
    cancelEdit() { this.editingId = null },
    async saveEdit(id) {
      try {
        const r = await fetch(`/api/knowledge/${id}`, {
          method: "PUT", headers: { "Content-Type": "application/json", ...this.getAuthHeaders() },
          body: JSON.stringify(this.editForm)
        })
        const d = await r.json()
        if (d.code === 200) { this.showMsg("保存成功"); this.cancelEdit(); this.searchData() }
        else this.showMsg(d.message || "保存失败", "error")
      } catch (e) { this.showMsg("网络错误", "error") }
    },

    // 删除
    showDeleteConfirm(item) { this.deleteItem = item; this.deleteConfirmVisible = true },
    async confirmDelete() {
      if (!this.deleteItem) return
      try {
        const r = await fetch(`/api/knowledge/${this.deleteItem.id}`, { method: "DELETE", headers: this.getAuthHeaders() })
        const d = await r.json()
        if (d.code === 200) { this.showMsg("删除成功"); this.searchData(); this.loadTotalCount() }
        else this.showMsg(d.message || "删除失败", "error")
      } catch (e) { this.showMsg("网络错误", "error") }
      finally { this.deleteConfirmVisible = false; this.deleteItem = null }
    },

    // 导入
    async uploadExcel(e) {
      const f = e.target.files[0]; if (!f) return
      const fd = new FormData(); fd.append("file", f)
      try {
        const r = await fetch("/api/knowledge/importExcel", { method: "POST", headers: this.getAuthHeaders(), body: fd })
        const d = await r.json()
        if (d.code === 200) {
          this.showMsg(d.message)
          if (d.data?.errorRows?.length) { this.errorRows = d.data.errorRows; this.errorModalVisible = true }
        } else this.showMsg(d.message || "导入失败", "error")
        this.searchData(); this.loadCategories(); this.loadTotalCount()
      } catch (e) { this.showMsg("导入失败: " + e.message, "error") }
      this.$refs.file.value = ""
    },

    // 导出
    exportCsv() { window.open("/api/knowledge/export", "_blank") },
  }
}
</script>

<style scoped>
.knowledge-page { max-width: 1200px; margin: 0 auto; padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-header h1 { margin: 0; font-size: 22px; }
.header-actions { display: flex; gap: 8px; }
.stats-bar { display: flex; gap: 20px; margin-bottom: 12px; font-size: 13px; color: #909399; }
.stats-bar strong { color: #303133; }
.search-bar { display: flex; gap: 8px; margin-bottom: 16px; align-items: center; }
.search-bar input { flex: 1; padding: 8px 12px; border: 1px solid #dcdfe6; border-radius: 6px; font-size: 13px; max-width: 280px; }
.search-bar select { padding: 8px 12px; border: 1px solid #dcdfe6; border-radius: 6px; font-size: 13px; }

.btn { padding: 8px 16px; border: none; border-radius: 6px; font-size: 13px; cursor: pointer; }
.btn-primary { background: #2E75B6; color: #fff; }
.btn-primary:hover { background: #24669e; }
.btn-outline { border: 1px solid #dcdfe6; background: #fff; color: #606266; }
.btn-outline:hover { background: #f5f7fa; }
.btn-text { border: none; background: transparent; color: #909399; }
.btn-text:hover { color: #606266; }
.btn-cancel { border: 1px solid #dcdfe6; background: #fff; color: #606266; }
.btn-danger { background: #f56c6c; color: #fff; }

.btn-sm { padding: 3px 10px; border: none; border-radius: 4px; font-size: 11px; cursor: pointer; }
.btn-sm-edit { background: #e6f7ff; color: #1890ff; }
.btn-sm-del { background: #fff1f0; color: #f5222d; margin-left: 4px; }
.btn-sm-success { background: #67c23a; color: #fff; }
.btn-sm-cancel { background: #f5f5f5; color: #999; margin-left: 4px; }

.table-wrap { overflow-x: auto; background: #fff; border-radius: 8px; box-shadow: 0 1px 6px rgba(0,0,0,.06); }
table { width: 100%; border-collapse: collapse; }
td, th { border-bottom: 1px solid #ebeef5; padding: 10px 12px; font-size: 13px; text-align: left; }
th { background: #fafafa; font-weight: 600; color: #606266; white-space: nowrap; }
tr:hover { background: #fafbfc; }
tr.editing { background: #e6f7ff; }
.content-cell { display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; max-height: 40px; }
.kw-cell, .cat-tag { font-size: 11px; color: #909399; }
.cat-tag { background: #f0f2f5; padding: 1px 8px; border-radius: 4px; }
.num { text-align: center; }
.action-cell { white-space: nowrap; }
.cell-input { width: 100%; padding: 4px 6px; border: 1px solid #409eff; border-radius: 4px; font-size: 12px; box-sizing: border-box; }
.cell-textarea { width: 100%; padding: 4px 6px; border: 1px solid #409eff; border-radius: 4px; font-size: 12px; resize: vertical; font-family: inherit; box-sizing: border-box; }

.pagination { display: flex; gap: 12px; align-items: center; justify-content: center; margin-top: 16px; }
.pagination button { padding: 6px 14px; border: 1px solid #dcdfe6; border-radius: 6px; background: #fff; cursor: pointer; font-size: 12px; }
.pagination button:disabled { opacity: .4; cursor: default; }
.page-info { font-size: 13px; color: #606266; }

.modal-overlay { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,.5); display: flex; align-items: center; justify-content: center; z-index: 1000; }
.modal { background: #fff; border-radius: 12px; padding: 24px; min-width: 420px; max-width: 560px; }
.modal-sm { min-width: 320px; }
.modal h3 { margin: 0 0 16px; }
.modal p { margin: 0 0 16px; color: #606266; }
.modal-actions { display: flex; justify-content: flex-end; gap: 8px; }
.form-group { margin-bottom: 12px; }
.form-group label { display: block; font-size: 12px; font-weight: 600; color: #606266; margin-bottom: 4px; }
.req { color: #f56c6c; }
.form-input { width: 100%; padding: 8px 10px; border: 1px solid #dcdfe6; border-radius: 6px; font-size: 13px; box-sizing: border-box; }
.form-textarea { width: 100%; padding: 8px 10px; border: 1px solid #dcdfe6; border-radius: 6px; font-size: 13px; resize: vertical; font-family: inherit; box-sizing: border-box; }
.toast { position: fixed; top: 16px; right: 16px; z-index: 3000; padding: 10px 20px; border-radius: 8px; color: #fff; font-size: 13px; box-shadow: 0 4px 12px rgba(0,0,0,.2); }
.toast-success { background: #67c23a; }
.toast-error { background: #f56c6c; }

.error-count { color: #f56c6c; font-weight: 600; }
.error-list { max-height: 260px; overflow-y: auto; border: 1px solid #f0f2f5; border-radius: 6px; padding: 8px; }
.error-item { padding: 6px 10px; background: #fef0f0; color: #f56c6c; border-radius: 4px; margin-bottom: 4px; font-size: 12px; }
</style>
