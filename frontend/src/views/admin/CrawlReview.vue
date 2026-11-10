<template>
  <section class="crawl-page">
    <header><div><h2>官网知识采集</h2><p>定期收集官网更新，审核后加入知识库。网页正文作为资料处理。</p></div>
      <button :disabled="busy || !status.enabled || status.running" @click="run">{{ status.running ? '任务执行中' : '立即抓取' }}</button></header>
    <p v-if="!status.enabled" class="notice">采集尚未启用。请在服务配置中设置官网白名单、种子页面和执行时间。</p>
    <p v-if="message" role="status" class="notice">{{ message }}</p>
    <div class="tools"><label>审核状态 <select v-model="filter" :disabled="busy" @change="page=1; load()"><option value="PENDING">待审核</option><option value="APPROVED">已入库</option><option value="REJECTED">已拒绝</option></select></label><button :disabled="busy" @click="load">刷新</button><span>共 {{ total }} 条</span></div>
    <p v-if="busy">正在处理…</p><p v-else-if="!items.length">当前没有符合条件的采集内容。</p>
    <article v-for="item in items" :key="item.id" class="candidate">
      <h3>{{ item.title }}</h3><a :href="safeUrl(item.url)" target="_blank" rel="noopener noreferrer">查看原网页</a><small>最近抓取：{{ item.fetchedAt }}</small>
      <details><summary>查看抓取原文</summary><pre>{{ item.content }}</pre></details>
      <template v-if="item.status === 'PENDING'">
        <label>入库标题<input v-model="item.editTitle" maxlength="500" /></label>
        <label>分类<input v-model="item.category" maxlength="50" /></label>
        <label>整理正文（最多 4000 字符，发布时自动附来源）<textarea v-model="item.editContent" rows="6" maxlength="4000"></textarea></label>
        <small v-if="item.content.length > 4000">原文较长，请核对并整理当前节选后再入库。</small>
        <div class="actions"><button :disabled="busy" @click="approve(item)">审核通过并入库</button><button :disabled="busy" @click="reject(item)">拒绝本版本</button></div>
      </template>
      <p v-else>状态：{{ item.status === 'APPROVED' ? '已审核入库' : '已拒绝' }}</p>
    </article>
    <div class="tools"><button :disabled="page<=1 || busy" @click="page--; load()">上一页</button><span>第 {{ page }} 页</span><button :disabled="page*20>=total || busy" @click="page++; load()">下一页</button></div>
    <h3>最近任务</h3><div class="run" v-for="job in status.runs" :key="job.id"><strong>{{ job.status }}</strong> {{ job.started_at }} · 访问 {{ job.visited }} 页 · 新增/更新 {{ job.changed_count }} 条 · 失败 {{ job.failed }} 页 <span>{{ job.detail }}</span></div>
  </section>
</template>

<script>
import request from '@/utils/request'
export default {
  name: 'CrawlReview',
  data: () => ({ status: { enabled: false, running: false, runs: [] }, items: [], total: 0, page: 1, filter: 'PENDING', busy: false, message: '', timer: null }),
  mounted() { this.load(); this.timer = setInterval(() => { if (this.status.running && !this.busy) this.refreshStatus() }, 5000) },
  beforeUnmount() { clearInterval(this.timer) },
  methods: {
    async refreshStatus() {
      try { this.status = (await request.get('/api/admin/crawler/status')).data }
      catch (error) { this.message = error.message; clearInterval(this.timer) }
    },
    safeUrl(url) { try { return new URL(url).protocol === 'https:' ? url : '#' } catch { return '#' } },
    async load() {
      this.busy = true
      try {
        const [status, list] = await Promise.all([request.get('/api/admin/crawler/status'), request.get('/api/admin/crawler/candidates', { params: { status: this.filter, page: this.page } })])
        this.status = status.data; this.total = list.data.total
        this.items = list.data.items.map(item => ({ ...item, editTitle: item.title, editContent: item.content.slice(0, 4000), category: '校园公告' }))
      } catch (error) { this.message = error.response?.data?.message || error.message }
      finally { this.busy = false }
    },
    async run() {
      this.busy = true
      try { await request.post('/api/admin/crawler/run'); this.message = '任务已启动，可在下方查看运行结果'; await this.load() }
      catch (error) { this.message = error.response?.data?.message || error.message }
      finally { this.busy = false }
    },
    async approve(item) {
      this.busy = true
      try { await request.post(`/api/admin/crawler/candidates/${item.id}/approve`, { fingerprint: item.fingerprint, title: item.editTitle, content: item.editContent, category: item.category }); this.message = '已入库，知识索引将在后续问答时增量更新'; await this.load() }
      catch (error) { this.message = error.response?.data?.message || error.message }
      finally { this.busy = false }
    },
    async reject(item) {
      this.busy = true
      try { await request.post(`/api/admin/crawler/candidates/${item.id}/reject`, { fingerprint: item.fingerprint }); this.message = '已拒绝当前版本，网页有新内容时会重新待审'; await this.load() }
      catch (error) { this.message = error.response?.data?.message || error.message }
      finally { this.busy = false }
    }
  }
}
</script>

<style scoped>
.crawl-page { max-width: 1000px; margin: auto; color: #263c4a; }
header, .tools, .actions { display: flex; gap: 12px; align-items: center; justify-content: space-between; flex-wrap: wrap; }
header p, small { color: #647786; font-size: 13px; } small { display: block; margin: 8px 0; }
.tools { justify-content: flex-start; margin: 18px 0; }
.notice { background: #eef5fb; padding: 12px; border-radius: 8px; }
.candidate { border: 1px solid #d8e3ea; padding: 20px; border-radius: 12px; margin: 16px 0; background: white; }
.candidate label { display: block; margin: 12px 0; font-size: 13px; }
input, textarea { display: block; box-sizing: border-box; width: 100%; margin-top: 6px; padding: 10px; border: 1px solid #bdced9; border-radius: 6px; font: inherit; }
pre { white-space: pre-wrap; max-height: 300px; overflow: auto; overflow-wrap: anywhere; }
button, select { padding: 8px 12px; border: 1px solid #a8bfce; border-radius: 6px; background: white; color: #244b63; cursor: pointer; }
button:disabled { opacity: .5; cursor: default; } .actions { justify-content: flex-start; }
.run { border-bottom: 1px solid #d8e3ea; padding: 10px 0; font-size: 13px; }.run span { display: block; color: #647786; }
</style>
