<template>
  <div class="admin-container">
    <h1>📚 知识点管理后台</h1>

    <!-- 搜索栏 -->
    <div style="margin: 15px 0">
      <input v-model="keyword" placeholder="输入关键词搜索知识点" />
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
        </tr>
      </thead>
      <tbody>
        <tr v-if="list.length === 0">
          <td colspan="4">暂无数据</td>
        </tr>
        <tr v-else v-for="item in list" :key="item.id">
          <td>{{ item.id }}</td>
          <td>{{ item.title }}</td>
          <td>{{ item.category }}</td>
          <td style="white-space: pre-wrap; word-break: break-all">
            {{ item.content }}
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script>
import axios from "axios";
export default {
  name: "KnowledgeImport",
  data() {
    return {
      keyword: "",
      list: [],
      loading: false,
    };
  },
  mounted() {
    this.loadAllData();
  },
  methods: {
    async loadAllData() {
      this.loading = true;
      try {
        let res = await fetch("http://localhost:8080/api/knowledge/listAll");
        let arr = await res.json();
        this.list = arr;
      } catch {
        this.list = [];
      } finally {
        this.loading = false;
      }
    },
    async searchData() {
      this.loading = true;
      try {
        // 拼接参数：keyword
        const url = `http://localhost:8080/api/knowledge/search?keyword=${this.keyword}`;
        const res = await fetch(url);
        this.list = await res.json();
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
      await fetch("http://localhost:8080/api/knowledge/importExcel", {
        method: "POST",
        body: form,
      });
      alert("导入完成！");
      this.loadAllData();
      // 清空文件，防止重复选同一个不触发change
      this.$refs.file.value = "";
    },
  },
};
</script>

<style scoped>
.admin-container {
  padding: 20px;
  max-width: 1000px;
  margin: 0 auto;
}
h1 {
  margin-bottom: 20px;
}
.search-bar {
  margin-bottom: 20px;
}
input {
  padding: 8px;
  width: 300px;
  margin-right: 8px;
}
button {
  padding: 8px 16px;
  background: #409eff;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  margin-right: 8px;
}
table {
  width: 100%;
  border-collapse: collapse;
}
td {
  border: 1px solid #ccc;
  padding: 6px;
  /* 自动换行，不隐藏文字 */
  word-break: break-all;
  white-space: normal;
}
th {
  background: #f5f7fa;
}
</style>
