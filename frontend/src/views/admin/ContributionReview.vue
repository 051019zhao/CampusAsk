<template>
  <div class="admin-container">
    <h1>📝 投稿审核</h1>
    <p class="hint">管理员可以在此查看待审核投稿，设置分类与关键词后通过，或输入原因驳回。</p>

    <table border="1" cellpadding="6" width="100%">
      <thead>
        <tr>
          <th>ID</th>
          <th>问题</th>
          <th>回答</th>
          <th>提交人</th>
          <th>提交时间</th>
          <th>分类</th>
          <th>关键词</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-if="list.length === 0">
          <td colspan="8">暂无待审核投稿</td>
        </tr>
        <tr v-else v-for="item in list" :key="item.id">
          <td>{{ item.id }}</td>
          <td class="wrap">{{ item.question }}</td>
          <td class="wrap">{{ item.answer }}</td>
          <td>{{ item.userId || '匿名' }}</td>
          <td>{{ formatDate(item.createdAt) }}</td>
          <td>
            <input
              v-model="item.category"
              placeholder="请输入分类"
              class="edit-input"
            />
          </td>
          <td>
            <input
              v-model="item.keywords"
              placeholder="请输入关键词，用逗号分隔"
              class="edit-input"
            />
          </td>
          <td>
            <button class="btn btn-success" @click="approve(item)">通过</button>
            <button class="btn btn-delete" @click="openReject(item)">驳回</button>
          </td>
        </tr>
      </tbody>
    </table>

    <div v-if="rejectModalVisible" class="modal-overlay">
      <div class="modal">
        <h3>驳回原因</h3>
        <textarea
          v-model="rejectReason"
          placeholder="请输入驳回原因"
          class="reject-textarea"
        ></textarea>
        <div class="modal-actions">
          <button class="btn btn-cancel" @click="closeReject">取消</button>
          <button class="btn btn-danger" @click="confirmReject">确认驳回</button>
        </div>
      </div>
    </div>

    <div v-if="message" :class="['message', messageType]">{{ message }}</div>
  </div>
</template>

<script>
export default {
  name: 'ContributionReview',
  data() {
    return {
      list: [],
      loading: false,
      rejectModalVisible: false,
      rejectReason: '',
      currentRejectItem: null,
      message: '',
      messageType: 'success',
    }
  },
  mounted() {
    this.loadPending()
  },
  methods: {
    async loadPending() {
      this.loading = true
      try {
        const res = await fetch('http://localhost:8080/api/contribute/pending')
        const data = await res.json()
        if (data.code === 200) {
          this.list = (data.data || []).map(item => ({ ...item, category: '', keywords: '' }))
        } else {
          this.list = []
          this.showMessage(data.message || '加载失败', 'error')
        }
      } catch (err) {
        this.list = []
        this.showMessage('加载失败: ' + err.message, 'error')
      } finally {
        this.loading = false
      }
    },
    formatDate(value) {
      if (!value) return ''
      const date = new Date(value)
      return date.toLocaleString()
    },
    async approve(item) {
      if (!item.category || !item.category.trim()) {
        this.showMessage('请先填写分类', 'error')
        return
      }
      try {
        const res = await fetch(`http://localhost:8080/api/contribute/${item.id}/review`, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            action: 'approve',
            category: item.category,
            keywords: item.keywords || '',
          }),
        })
        const data = await res.json()
        if (data.code === 200) {
          this.showMessage(data.message || '已通过并入库', 'success')
          this.loadPending()
        } else {
          this.showMessage(data.message || '审核失败', 'error')
        }
      } catch (err) {
        this.showMessage('审核失败: ' + err.message, 'error')
      }
    },
    openReject(item) {
      this.currentRejectItem = item
      this.rejectReason = ''
      this.rejectModalVisible = true
    },
    closeReject() {
      this.rejectModalVisible = false
      this.currentRejectItem = null
      this.rejectReason = ''
    },
    async confirmReject() {
      if (!this.currentRejectItem) return
      try {
        const res = await fetch(
          `http://localhost:8080/api/contribute/${this.currentRejectItem.id}/review`,
          {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
              action: 'reject',
              reason: this.rejectReason || '',
            }),
          },
        )
        const data = await res.json()
        if (data.code === 200) {
          this.showMessage(data.message || '已驳回', 'success')
          this.loadPending()
        } else {
          this.showMessage(data.message || '驳回失败', 'error')
        }
      } catch (err) {
        this.showMessage('驳回失败: ' + err.message, 'error')
      } finally {
        this.closeReject()
      }
    },
    showMessage(msg, type) {
      this.message = msg
      this.messageType = type
      setTimeout(() => {
        this.message = ''
      }, 3000)
    },
  },
}
</script>

<style scoped>
.admin-container {
  padding: 20px;
  max-width: 1200px;
  margin: 0 auto;
}
h1 {
  margin-bottom: 10px;
  color: #303133;
}
.hint {
  margin-bottom: 20px;
  color: #606266;
}
table {
  width: 100%;
  border-collapse: collapse;
  background: white;
}
td,
th {
  border: 1px solid #ebeef5;
  padding: 10px;
  word-break: break-all;
  white-space: normal;
}
th {
  background: #f5f7fa;
  font-weight: bold;
  text-align: left;
}
.wrap {
  white-space: pre-wrap;
  word-break: break-all;
  max-width: 220px;
}
.edit-input {
  width: 100%;
  padding: 6px 8px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  font-size: 14px;
}
.btn {
  padding: 6px 12px;
  margin-right: 6px;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
}
.btn-success {
  background: #67c23a;
  color: white;
}
.btn-delete {
  background: #f56c6c;
  color: white;
}
.btn-cancel {
  background: #dcdfe6;
  color: #606266;
}
.btn-danger {
  background: #f56c6c;
  color: white;
}
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}
.modal {
  background: white;
  padding: 20px;
  border-radius: 8px;
  width: 420px;
}
.reject-textarea {
  width: 100%;
  height: 120px;
  margin: 12px 0;
  padding: 10px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  font-size: 14px;
  resize: vertical;
}
.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
.message {
  margin-top: 16px;
  padding: 12px 16px;
  border-radius: 5px;
}
.message.success {
  background: #f0f9eb;
  color: #67c23a;
}
.message.error {
  background: #fde2e2;
  color: #f56c6c;
}
</style>
