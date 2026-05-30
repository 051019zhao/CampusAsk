<template>
  <div class="admin-container">
    <h1>📚 知识点管理后台</h1>

    <!-- 搜索栏 -->
    <div class="search-bar">
      <input
        v-model="keyword"
        type="text"
        placeholder="输入关键词搜索知识点"
        @keyup.enter="searchData"
      />
      <button @click="searchData">搜索</button>
      <button @click="loadAllData">刷新全部</button>
    </div>

    <!-- 数据表格 -->
    <table>
      <thead>
        <tr>
          <th>ID</th>
          <th>标题</th>
          <th>分类</th>
          <th>内容</th>
        </tr>
      </thead>
      <tbody>
        <tr v-if="loading">
          <td colspan="4" style="text-align: center">加载中...</td>
        </tr>
        <tr v-else-if="list.length === 0">
          <td colspan="4" style="text-align: center">暂无数据</td>
        </tr>
        <tr v-else v-for="item in list" :key="item.id">
          <td>{{ item.id }}</td>
          <td>{{ item.title }}</td>
          <td>{{ item.category }}</td>
          <td>{{ item.content }}</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script>
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
        const res = await this.$axios.get("/api/knowledge/listAll");
        this.list = res.data;
      } catch (err) {
        console.error("加载失败:", err);
        alert("加载知识点列表失败");
      } finally {
        this.loading = false;
      }
    },
    async searchData() {
      this.loading = true;
      try {
        const res = await this.$axios.get("/api/knowledge/search", {
          params: { keyword: this.keyword },
        });
        this.list = res.data;
      } catch (err) {
        console.error("搜索失败:", err);
      } finally {
        this.loading = false;
      }
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
th,
td {
  border: 1px solid #eee;
  padding: 12px;
  text-align: left;
}
th {
  background: #f5f7fa;
}
</style>
