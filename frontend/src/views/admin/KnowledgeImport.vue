<template>
  <div class="admin-container">
    <!-- 消息提示区域 -->
    <div class="message-container">
      <div
        v-for="(msg, index) in messages"
        :key="index"
        :class="['message', msg.type]"
        @click="removeMessage(index)"
      >
        {{ msg.text }}
        <span class="close-btn">×</span>
      </div>
    </div>

    <h1>📚 知识点管理后台</h1>

    <!-- 搜索栏 -->
    <div class="search-bar">
      <input
        v-model="keyword"
        placeholder="输入关键词搜索知识点"
        @keyup.enter="searchData"
      />
      <button @click="searchData">搜索</button>
      <button @click="loadAllData">刷新全部</button>
      <button @click="$refs.file.click()">导入Excel</button>
      <input
        ref="file"
        type="file"
        accept=".xlsx,.xls"
        hidden
        @change="uploadExcel"
      />
    </div>

    <table border="1" cellpadding="6" width="100%">
      <thead>
        <tr>
          <th>ID</th>
          <th>标题</th>
          <th>分类</th>
          <th>内容</th>
          <th>关键词</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-if="list.length === 0">
          <td colspan="6">暂无数据</td>
        </tr>
        <tr v-else v-for="item in list" :key="item.id">
          <td>{{ item.id }}</td>
          <td>
            <input
              v-if="editingId === item.id"
              v-model="editForm.title"
              class="edit-input"
            />
            <span v-else>{{ item.title }}</span>
          </td>
          <td>
            <input
              v-if="editingId === item.id"
              v-model="editForm.category"
              class="edit-input"
            />
            <span v-else>{{ item.category }}</span>
          </td>
          <td style="white-space: pre-wrap; word-break: break-all">
            <textarea
              v-if="editingId === item.id"
              v-model="editForm.content"
              class="edit-textarea"
            ></textarea>
            <span v-else>{{ item.content }}</span>
          </td>
          <td>
            <input
              v-if="editingId === item.id"
              v-model="editForm.keywords"
              class="edit-input"
            />
            <span v-else>{{ item.keywords }}</span>
          </td>
          <td>
            <template v-if="editingId === item.id">
              <button class="btn btn-success" @click="saveEdit(item.id)">
                保存
              </button>
              <button class="btn btn-cancel" @click="cancelEdit">取消</button>
            </template>
            <template v-else>
              <button class="btn btn-edit" @click="startEdit(item)">
                编辑
              </button>
              <button class="btn btn-delete" @click="showDeleteConfirm(item)">
                删除
              </button>
            </template>
          </td>
        </tr>
      </tbody>
    </table>
    <!-- 删除确认弹窗 -->
    <div v-if="deleteConfirmVisible" class="modal-overlay">
      <div class="modal">
        <h3>确认删除</h3>
        <p>确定要删除知识点「{{ deleteItem?.title }}」吗？此操作不可撤销。</p>
        <div class="modal-actions">
          <button class="btn btn-cancel" @click="deleteConfirmVisible = false">
            取消
          </button>
          <button class="btn btn-danger" @click="confirmDelete">
            确认删除
          </button>
        </div>
      </div>
    </div>

    <!-- 提示消息 -->
    <div v-if="message" :class="['message', messageType]">
      {{ message }}
    </div>
  </div>
</template>

<script>
export default {
  name: "KnowledgeImport",
  data() {
    return {
      messages: [], // 新增：消息队列
      keyword: "",
      list: [],
      loading: false,
      editingId: null,
      editForm: {
        title: "",
        content: "",
        category: "",
        keywords: "",
      },
      deleteConfirmVisible: false,
      deleteItem: null,
      // 删除：message: "",
      // 删除：messageType: "success",
    };
  },
  mounted() {
    this.loadAllData();
  },
  methods: {
    // 新增：消息提示方法
    showMessage(msg, type = "success") {
      // 添加新消息到队列
      this.messages.push({ text: msg, type: type });

      // 5秒后自动移除
      setTimeout(() => {
        if (this.messages.length > 0) {
          this.messages.shift();
        }
      }, 5000);
    },
    // 新增：手动关闭消息
    removeMessage(index) {
      this.messages.splice(index, 1);
    },
    getAuthHeaders() {
      const token = localStorage.getItem("token");
      return token ? { token } : {};
    },

    async loadAllData() {
      this.loading = true;
      try {
        const res = await fetch("/api/knowledge/listAll", {
          headers: this.getAuthHeaders(),
        });
        const data = await res.json();
        console.log("响应数据:", data); // 添加日志
        if (data.code === 200) {
          this.list = data.data;
          this.message = "";
        } else {
          this.list = [];
          this.showMessage(data.message || "获取知识点失败", "error");
        }
      } catch (err) {
        console.error("加载失败:", err);
        this.list = [];
      } finally {
        this.loading = false;
      }
    },
    async searchData() {
      if (!this.keyword.trim()) {
        this.loadAllData();
        return;
      }
      this.loading = true;
      try {
        const url = `/api/knowledge/search?keyword=${encodeURIComponent(
          this.keyword,
        )}`;
        const res = await fetch(url, {
          headers: this.getAuthHeaders(),
        });
        const data = await res.json();
        if (data.code === 200) {
          this.list = data.data;
        } else {
          this.list = [];
        }
      } catch (err) {
        console.error("搜索失败:", err);
        this.list = [];
      } finally {
        this.loading = false;
      }
    },
    // Excel导入
    async uploadExcel(e) {
      const file = e.target.files[0];
      if (!file) return;
      const form = new FormData();
      form.append("file", file);
      try {
        const res = await fetch(
          "/api/knowledge/importExcel",
          {
            method: "POST",
            headers: this.getAuthHeaders(),
            body: form,
          },
        );
        const data = await res.json();
        console.log("导入响应:", data);
        if (data.code === 200) {
          this.showMessage(data.message, "success");
          // 如果有错误行，显示详细错误信息
          if (
            data.data &&
            data.data.errorRows &&
            data.data.errorRows.length > 0
          ) {
            alert("错误行详情:\n" + data.data.errorRows.join("\n"));
          }
        } else {
          this.showMessage(data.message, "error");
        }
        this.loadAllData();
      } catch (err) {
        this.showMessage("导入失败: " + err.message, "error");
      }
      // 清空文件，防止重复选同一个不触发change
      this.$refs.file.value = "";
    },
    // 开始编辑
    startEdit(item) {
      this.editingId = item.id;
      this.editForm = {
        title: item.title,
        content: item.content,
        category: item.category,
        keywords: item.keywords,
      };
    },
    // 保存编辑
    async saveEdit(id) {
      try {
        const res = await fetch(`/api/knowledge/${id}`, {
          method: "PUT",
          headers: {
            "Content-Type": "application/json",
            ...this.getAuthHeaders(),
          },
          body: JSON.stringify(this.editForm),
        });
        const data = await res.json();
        if (data.code === 200) {
          this.showMessage(data.message, "success");
          this.cancelEdit();
          this.loadAllData();
        } else {
          this.showMessage(data.message, "error");
        }
      } catch (err) {
        this.showMessage("保存失败: " + err.message, "error");
      }
    },
    // 取消编辑
    cancelEdit() {
      this.editingId = null;
      this.editForm = {
        title: "",
        content: "",
        category: "",
        keywords: "",
      };
    },
    // 显示删除确认
    showDeleteConfirm(item) {
      this.deleteItem = item;
      this.deleteConfirmVisible = true;
    },
    // 确认删除
    async confirmDelete() {
      if (!this.deleteItem) return;
      try {
        const res = await fetch(
          `/api/knowledge/${this.deleteItem.id}`,
          {
            method: "DELETE",
            headers: this.getAuthHeaders(),
          },
        );
        const data = await res.json();
        if (data.code === 200) {
          this.showMessage(data.message, "success");
        } else {
          this.showMessage(data.message, "error");
        }
        this.loadAllData();
      } catch (err) {
        this.showMessage("删除失败: " + err.message, "error");
      } finally {
        this.deleteConfirmVisible = false;
        this.deleteItem = null;
      }
    },
    // 显示消息提示
    showMessage(msg, type) {
      this.message = msg;
      this.messageType = type;
      setTimeout(() => {
        this.message = "";
      }, 3000);
    },
  },
};
</script>

<style scoped>
.admin-container {
  padding: 20px;
  max-width: 1200px;
  margin: 0 auto;
}
h1 {
  margin-bottom: 20px;
  color: #303133;
}
.search-bar {
  margin-bottom: 20px;
  display: flex;
  align-items: center;
  gap: 10px;
}
.search-bar input {
  padding: 8px 12px;
  width: 300px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  font-size: 14px;
}
.btn {
  padding: 8px 16px;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
  margin-right: 5px;
}
.btn-edit {
  background: #67c23a;
  color: white;
}
.btn-edit:hover {
  background: #85ce61;
}
.btn-delete {
  background: #f56c6c;
  color: white;
}
.btn-delete:hover {
  background: #f78989;
}
.btn-success {
  background: #67c23a;
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
.edit-input {
  width: 100%;
  padding: 4px;
  border: 1px solid #409eff;
  border-radius: 3px;
  font-size: 14px;
}
.edit-textarea {
  width: 100%;
  height: 60px;
  padding: 4px;
  border: 1px solid #409eff;
  border-radius: 3px;
  font-size: 14px;
  resize: none;
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
  min-width: 350px;
}
.modal h3 {
  margin: 0 0 15px 0;
  color: #303133;
}
.modal p {
  color: #606266;
  margin-bottom: 20px;
}
.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
/* 消息提示样式 */
.message-container {
  position: fixed;
  top: 20px;
  right: 20px;
  z-index: 2000;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.message {
  position: fixed;
  top: 20px;
  right: 20px;
  padding: 12px 20px;
  border-radius: 4px;
  color: white;
  font-size: 14px;
  z-index: 2000;
}
.message.success {
  background: #67c23a;
}
.message.error {
  background: #f56c6c;
}
</style>
