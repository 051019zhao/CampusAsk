<template>
  <div class="qa-container">
    <h1>CampusAsk 校园智能问答</h1>
    <div class="search-box">
      <input
          v-model="keyword"
          placeholder="请输入问题，如：食堂几点关门？"
          @keyup.enter="search"
      />
      <button @click="search">搜索</button>
    </div>

    <div v-if="loading" class="loading">查询中...</div>

    <div v-if="results.length > 0" class="results">
      <div v-for="item in results" :key="item.id" class="result-card">
        <h3>{{ item.title }}</h3>
        <p>{{ item.content }}</p>
        <span class="category">{{ item.category }}</span>
      </div>
    </div>

    <div v-if="noResult" class="no-result">暂无相关答案，请尝试其他关键词</div>
  </div>
</template>

<script>
export default {
  name: 'QaPage',
  data() {
    return {
      keyword: '',
      results: [],
      loading: false,
      noResult: false
    }
  },
  methods: {
    async search() {
      if (!this.keyword.trim()) return
      this.loading = true
      this.noResult = false
      this.results = []
      try {
        const res = await fetch('http://localhost:8080/api/qa/ask', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ keyword: this.keyword })
        })
        const data = await res.json()
        if (data.code === 200 && Array.isArray(data.data)) {
          this.results = data.data
          if (this.results.length === 0) this.noResult = true
        } else {
          this.noResult = true
        }
      } catch (e) {
        this.noResult = true
      } finally {
        this.loading = false
      }
    }
  }
}
</script>

<style scoped>
.qa-container {
  max-width: 800px;
  margin: 40px auto;
  padding: 0 20px;
  font-family: sans-serif;
}
h1 {
  text-align: center;
  color: #2c3e50;
  margin-bottom: 30px;
}
.search-box {
  display: flex;
  gap: 10px;
  margin-bottom: 20px;
}
input {
  flex: 1;
  padding: 12px;
  font-size: 16px;
  border: 1px solid #ddd;
  border-radius: 6px;
}
button {
  padding: 12px 24px;
  background: #42b883;
  color: white;
  border: none;
  border-radius: 6px;
  font-size: 16px;
  cursor: pointer;
}
button:hover { background: #33a06f; }
.result-card {
  background: #f9f9f9;
  border: 1px solid #eee;
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 12px;
}
.result-card h3 { margin: 0 0 8px; color: #2c3e50; }
.result-card p { margin: 0 0 8px; color: #555; }
.category {
  font-size: 12px;
  background: #e8f5e9;
  color: #42b883;
  padding: 2px 8px;
  border-radius: 4px;
}
.loading, .no-result {
  text-align: center;
  color: #999;
  margin-top: 20px;
}
</style>