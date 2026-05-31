<template>
  <div class="admin-layout">
    <!-- 左侧导航 -->
    <aside class="sidebar">
      <div class="sidebar-title">后台管理系统</div>
      <nav>
        <div class="nav-item active">知识导入</div>
      </nav>
    </aside>

    <!-- 主内容区 -->
    <main class="main">
      <!-- 顶部栏 -->
      <div class="topbar">
        <div></div>
        <div class="user-info">
          <span class="avatar">👤</span>
          <span>{{ username }}</span>
        </div>
      </div>

      <!-- 内容 -->
      <div class="content">
        <h2 class="page-title">📋 知识点管理后台</h2>

        <!-- 搜索栏 -->
        <div class="toolbar">
          <input
            v-model="keyword"
            placeholder="输入关键词搜索知识点"
            class="search-input"
          />
          <button class="btn-blue" @click="search">搜索</button>
          <button class="btn-blue" @click="loadAll">刷新全部</button>
          <button class="btn-green" @click="showAddForm = true">+ 添加</button>
          <label class="btn-green" style="cursor: pointer">
            导入 Excel
            <input
              type="file"
              accept=".xlsx,.xls"
              @change="handleImport"
              hidden
            />
          </label>
        </div>

        <!-- 表格 -->
        <table class="data-table">
          <thead>
            <tr>
              <th>ID</th>
              <th>标题</th>
              <th>分类</th>
              <th>内容</th>
              <th>浏览量</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="list.length === 0">
              <td colspan="4" class="empty">暂无数据</td>
            </tr>
            <tr v-for="item in list" :key="item.id">
              <td>{{ item.id }}</td>
              <td>{{ item.title }}</td>
              <td>{{ item.category }}</td>
              <td>
                {{ item.content?.slice(0, 60)
                }}{{ item.content?.length > 60 ? "..." : "" }}
              </td>
              <td>{{ item.viewCount || 0 }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </main>

    <!-- 添加弹窗 -->
    <div
      v-if="showAddForm"
      class="modal-mask"
      @click.self="showAddForm = false"
    >
      <div class="modal">
        <h3>添加知识点</h3>
        <div class="form-item">
          <label>标题</label><input v-model="form.title" />
        </div>
        <div class="form-item">
          <label>分类</label><input v-model="form.category" />
        </div>
        <div class="form-item">
          <label>关键词</label
          ><input v-model="form.keywords" placeholder="逗号分隔" />
        </div>
        <div class="form-item">
          <label>内容</label
          ><textarea v-model="form.content" rows="5"></textarea>
        </div>
        <div class="modal-btns">
          <button class="btn-blue" @click="submitAdd">提交</button>
          <button class="btn-cancel" @click="showAddForm = false">取消</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from "vue";
import request from "@/utils/request";

const list = ref([]);
const keyword = ref("");
const showAddForm = ref(false);
const username = ref(localStorage.getItem("username") || "管理员");
const form = ref({ title: "", category: "", keywords: "", content: "" });

const loadAll = async () => {
  const res = await request.get("/admin/knowledge/list");
  if (res.code === 200) list.value = res.data;
};

const search = async () => {
  if (!keyword.value.trim()) {
    loadAll();
    return;
  }
  const res = await request.get("/admin/knowledge/list");
  if (res.code === 200) {
    list.value = res.data.filter(
      (k) =>
        k.title?.includes(keyword.value) ||
        k.keywords?.includes(keyword.value) ||
        k.content?.includes(keyword.value),
    );
  }
};

const submitAdd = async () => {
  const res = await request.post("/admin/knowledge/add", form.value);
  if (res.code === 200) {
    alert("添加成功");
    showAddForm.value = false;
    form.value = { title: "", category: "", keywords: "", content: "" };
    loadAll();
  } else {
    alert(res.message);
  }
};

const handleImport = async (e) => {
  const file = e.target.files[0];
  if (!file) return;
  const fd = new FormData();
  fd.append("file", file);
  const res = await request.post("/admin/knowledge/import", fd);
  alert(res.message || (res.code === 200 ? "导入成功" : "导入失败"));
  if (res.code === 200) loadAll();
  e.target.value = "";
};

onMounted(loadAll);
</script>

<style scoped>
.admin-layout {
  display: flex;
  min-height: 100vh;
  font-family: sans-serif;
}

/* 左侧导航 */
.sidebar {
  width: 200px;
  background: #1e2a3a;
  color: #fff;
  flex-shrink: 0;
}
.sidebar-title {
  padding: 24px 20px;
  font-size: 16px;
  font-weight: 700;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}
.nav-item {
  padding: 14px 20px;
  font-size: 14px;
  cursor: pointer;
  color: rgba(255, 255, 255, 0.7);
}
.nav-item.active {
  color: #fff;
  background: rgba(255, 255, 255, 0.1);
}

/* 主区域 */
.main {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: #f5f6fa;
}
.topbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 24px;
  background: #fff;
  border-bottom: 1px solid #eee;
}
.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: #555;
}
.avatar {
  font-size: 20px;
}

.content {
  padding: 28px 32px;
}
.page-title {
  font-size: 20px;
  font-weight: 700;
  margin: 0 0 20px;
}

/* 搜索栏 */
.toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: 20px;
  align-items: center;
  flex-wrap: wrap;
}
.search-input {
  padding: 8px 14px;
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  font-size: 14px;
  width: 260px;
  outline: none;
}
.btn-blue,
.btn-green,
.btn-cancel {
  padding: 8px 18px;
  border: none;
  border-radius: 6px;
  font-size: 14px;
  cursor: pointer;
}
.btn-blue {
  background: #4096ff;
  color: #fff;
}
.btn-blue:hover {
  background: #2d7fe0;
}
.btn-green {
  background: #52c41a;
  color: #fff;
}
.btn-green:hover {
  background: #3da512;
}
.btn-cancel {
  background: #f0f0f0;
  color: #555;
}

/* 表格 */
.data-table {
  width: 100%;
  border-collapse: collapse;
  background: #fff;
  border-radius: 8px;
  overflow: hidden;
  font-size: 14px;
}
.data-table th,
.data-table td {
  padding: 12px 16px;
  text-align: left;
  border-bottom: 1px solid #f0f0f0;
}
.data-table th {
  background: #fafafa;
  font-weight: 600;
  color: #555;
}
.data-table tr:hover td {
  background: #fafffe;
}
.empty {
  text-align: center;
  color: #aaa;
  padding: 40px;
}

/* 弹窗 */
.modal-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.3);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
}
.modal {
  background: #fff;
  border-radius: 10px;
  padding: 32px;
  width: 480px;
}
.modal h3 {
  margin: 0 0 20px;
}
.form-item {
  margin-bottom: 14px;
}
.form-item label {
  display: block;
  font-size: 13px;
  color: #555;
  margin-bottom: 6px;
}
.form-item input,
.form-item textarea {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  font-size: 14px;
  box-sizing: border-box;
  outline: none;
}
.form-item textarea {
  resize: vertical;
}
.modal-btns {
  display: flex;
  gap: 10px;
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
