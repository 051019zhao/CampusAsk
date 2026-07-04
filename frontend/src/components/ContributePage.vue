<template>
  <div class="contribute-page">
    <button class="back-float-btn" @click="$router.push({ name: 'QaPage' })">← 返回问答</button>
    <div class="contribute-card">
      <!-- 标签栏 -->
      <div class="tabs">
        <button :class="['tab-btn', { active: activeTab === 'submit' }]" @click="activeTab = 'submit'">
          投稿新问答
        </button>
        <button :class="['tab-btn', { active: activeTab === 'rejected' }]" @click="switchToRejected">
          未通过投稿
        </button>
      </div>

      <!-- 投稿新问答 -->
      <div v-if="activeTab === 'submit'" class="tab-content">
        <div class="page-header">
          <p>提交后将进入审核池，审核通过后展示到校园知识库。</p>
        </div>

        <div class="form-group">
          <label>问题</label>
          <textarea
            v-model="question"
            placeholder="请输入你想贡献的问题。"
            rows="4"
            @input="onInputChange"
          ></textarea>
        </div>

        <div class="form-group">
          <label>回答</label>
          <textarea
            v-model="answer"
            placeholder="请输入该问题的答案，确保内容准确。"
            rows="6"
            @input="onInputChange"
          ></textarea>
        </div>

        <div class="form-group">
          <label>分类</label>
          <select v-model="category" class="category-select">
            <option value="">请选择分类...</option>
            <option v-for="cat in categories" :key="cat" :value="cat">{{ cat }}</option>
          </select>
        </div>

        <div v-if="sensitiveHint" class="hint warning">
          {{ sensitiveHint }}
        </div>
        <div v-if="aiCheckResult" class="hint error ui-state ui-state-error">
          {{ aiCheckResult }}
        </div>
        <div v-if="sensitiveChecking" class="hint checking ui-state ui-state-loading">
          🔍 AI 正在审核中...
        </div>
        <div v-if="errorMessage" class="hint error ui-state ui-state-error">
          {{ errorMessage }}
        </div>
        <div v-if="statusMessage" class="hint success ui-state ui-state-empty">
          {{ statusMessage }}
        </div>

        <div class="action-row">
          <button
            class="check-btn"
            :disabled="aiChecking || !canCheck"
            @click="runAiCheck"
          >
            {{ aiChecking ? '⏳ 审核中...' : aiChecked ? '✅ AI 审核通过' : 'AI 审核' }}
          </button>
          <button
            class="submit-btn"
            :class="{ 'submit-btn--ready': aiChecked }"
            :disabled="submitting"
            @click="submitContribution"
          >
            {{ submitting ? '提交中...' : ' 提交 ' }}
          </button>
        </div>
      </div>

      <!-- 未通过投稿 -->
      <div v-if="activeTab === 'rejected'" class="tab-content">
        <div class="page-header">
          <p>这些投稿因某些原因未通过审核。请查看驳回原因后重新编辑提交。</p>
        </div>

        <div v-if="rejectedLoading" class="loading ui-state ui-state-loading">加载中...</div>
        <div v-else-if="rejectedContributions.length === 0" class="empty-state ui-state ui-state-empty">
          暂无未通过投稿
        </div>
        <div v-else class="rejected-list">
          <div v-for="item in rejectedContributions" :key="item.id" class="rejected-card">
            <div class="card-header">
              <h4>{{ item.question }}</h4>
              <span class="reject-badge">已驳回</span>
            </div>
            <div class="card-body">
              <div class="answer-section">
                <label>你的回答：</label>
                <p>{{ item.answer }}</p>
              </div>
              <div class="reason-section">
                <label>驳回原因：</label>
                <p class="reason-text">{{ item.reviewReason || '（无具体原因）' }}</p>
              </div>
              <div class="time-section">
                <small>审核时间：{{ formatDate(item.reviewTime) }}</small>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { submitContribution, getRejectedContributions, checkSensitiveText, getSensitiveWords } from '@/api/contribution'

export default {
  name: 'ContributePage',
  data() {
    return {
      activeTab: 'submit',
      question: '',
      answer: '',
      category: '',  // V4: 投稿分类
      categories: ['课程与选课','考试与成绩','图书馆','奖学金与资助','毕业与实习','食堂餐饮','宿舍住宿','交通出行','校园卡证','校园服务','医疗健康','网络与IT','行政规章','社团活动','招生与就业','学校概况','其他'],
      submitting: false,
      errorMessage: '',
      statusMessage: '',
      // 本地数据库敏感词（实时匹配）
      sensitiveWords: [],
      sensitiveHint: '',
      // AI 审核状态
      aiChecked: false,
      aiChecking: false,
      aiCheckResult: '',
      // 未通过投稿
      rejectedContributions: [],
      rejectedLoading: false
    }
  },
  computed: {
    canCheck() {
      return this.question.trim().length > 0 && this.answer.trim().length > 0 && !this.aiChecked
    }
  },
  created() {
    this.loadSensitiveWords()
  },
  methods: {
    getUserId() {
      const raw = localStorage.getItem('userId')
      const parsed = Number.parseInt(raw || '', 10)
      return Number.isFinite(parsed) ? parsed : null
    },
    async loadSensitiveWords() {
      try {
        const res = await getSensitiveWords()
        if (res?.code === 200 && Array.isArray(res.data)) {
          this.sensitiveWords = res.data.map(w => String(w || '').toLowerCase()).filter(Boolean)
        }
      } catch (e) {
        // 使用默认词库兜底
        this.sensitiveWords = ['政治', '色情', '暴力', '赌博', '违规', '违禁', '敏感词', '涉政', '涉黄', '谣言']
      }
    },
    resetForm() {
      this.question = ''
      this.answer = ''
      this.sensitiveHint = ''
      this.aiChecked = false
      this.aiChecking = false
      this.aiCheckResult = ''
      this.errorMessage = ''
      // 注意：不重置 statusMessage，让成功提示保持显示
    },
    /** 输入时实时本地匹配数据库敏感词（免费、快速） */
    onInputChange() {
      this.errorMessage = ''
      this.statusMessage = ''
      // 内容变化后 AI 审核状态重置
      this.aiChecked = false
      this.aiCheckResult = ''

      const text = `${this.question}\n${this.answer}`.toLowerCase()
      if (text.trim().length < 2) {
        this.sensitiveHint = ''
        return
      }
      const found = this.sensitiveWords.filter(w => text.includes(w))
      if (found.length) {
        this.sensitiveHint = '⚠️ 检测到敏感词：' + found.join('、') + '，请修改后再提交。'
      } else {
        this.sensitiveHint = ''
      }
    },
    /** 点击 AI 审核按钮，调用 DeepSeek 深度检测 */
    async runAiCheck() {
      if (!this.question.trim() || !this.answer.trim()) return
      this.aiChecking = true
      this.aiCheckResult = ''
      this.errorMessage = ''
      try {
        const text = `${this.question.trim()}\n${this.answer.trim()}`
        const res = await checkSensitiveText(text)
        if (res?.code === 200 && res.data) {
          if (res.data.sensitive) {
            this.aiCheckResult = '❌ AI 审核未通过：' + (res.data.reason || '内容包含敏感信息，请修改')
            this.aiChecked = false
          } else {
            this.aiChecked = true
            this.aiCheckResult = ''
          }
        } else {
          this.aiCheckResult = '⚠️ AI 审核服务异常，请稍后重试'
          this.aiChecked = false
        }
      } catch (e) {
        this.aiCheckResult = '⚠️ AI 审核请求失败，请检查网络'
        this.aiChecked = false
      } finally {
        this.aiChecking = false
      }
    },
    async switchToRejected() {
      this.activeTab = 'rejected'
      if (this.rejectedContributions.length === 0) {
        await this.loadRejectedContributions()
      }
    },
    async loadRejectedContributions() {
      this.rejectedLoading = true
      try {
        const response = await getRejectedContributions()
        if (response?.code === 200 && Array.isArray(response.data)) {
          this.rejectedContributions = response.data
        } else {
          this.rejectedContributions = []
        }
      } catch (error) {
        this.rejectedContributions = []
      } finally {
        this.rejectedLoading = false
      }
    },
    formatDate(value) {
      if (!value) return '未审核'
      const date = new Date(value)
      return date.toLocaleString('zh-CN')
    },
    async submitContribution() {
      this.errorMessage = ''
      this.statusMessage = ''

      if (!this.question.trim()) {
        this.errorMessage = '请填写投稿问题。'
        return
      }
      if (!this.answer.trim()) {
        this.errorMessage = '请填写投稿回答。'
        return
      }
      if (this.sensitiveHint) {
        this.errorMessage = this.sensitiveHint
        return
      }
      if (!this.aiChecked) {
        this.errorMessage = '请先点击"AI 审核"按钮完成内容检测'
        return
      }

      this.submitting = true
      try {
        const userId = this.getUserId()
        const response = await submitContribution({
          question: this.question.trim(),
          answer: this.answer.trim(),
          userId: userId ? String(userId) : '',
          category: this.category || ''
        })

        if (response?.code === 200) {
          this.statusMessage = response.data || '投稿已提交，等待审核。'
          this.resetForm()
        } else {
          this.errorMessage = response?.message || '投稿失败，请稍后重试。'
        }
      } catch (error) {
        this.errorMessage = '投稿失败，请检查网络后重试。'
      } finally {
        this.submitting = false
      }
    }
  }
}
</script>

<style scoped>
.contribute-page {
  min-height: 100vh;
  padding: 24px;
  background: #f4f8fb;
  display: flex;
  justify-content: center;
  align-items: flex-start;
  position: relative;
}

.back-float-btn {
  position: absolute;
  top: 20px;
  left: 24px;
  border: none;
  background: #fff;
  border-radius: 10px;
  padding: 8px 16px;
  font-size: 14px;
  color: #3c6476;
  cursor: pointer;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  transition: all 0.2s;
}

.back-float-btn:hover {
  background: #f0f4f8;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.12);
}

.contribute-card {
  width: min(920px, 100%);
  background: #ffffff;
  border-radius: 18px;
  box-shadow: 0 24px 56px rgba(0, 0, 0, 0.08);
  padding: 30px;
}

.tabs {
  display: flex;
  gap: 0;
  margin-bottom: 24px;
  border-bottom: 2px solid #e5e7eb;
}

.tab-btn {
  background: none;
  border: none;
  padding: 12px 20px;
  font-size: 15px;
  font-weight: 500;
  color: #6b7280;
  cursor: pointer;
  border-bottom: 3px solid transparent;
  margin-bottom: -2px;
  transition: all 0.2s;
}

.tab-btn:hover {
  color: #1b2730;
}

.tab-btn.active {
  color: #1d4ed8;
  border-bottom-color: #1d4ed8;
}

.tab-content {
  animation: fadeIn 0.2s ease-in-out;
}

@keyframes fadeIn {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

.page-header {
  margin-bottom: 24px;
}

.page-header h2 {
  margin: 0;
  font-size: 26px;
  color: #1b2730;
}

.page-header p {
  margin: 8px 0 0;
  color: #5a6f7f;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 20px;
}

.form-group label {
  font-weight: 600;
  color: #334155;
}

textarea {
  width: 100%;
  border-radius: 14px;
  border: 1px solid #d7e2ea;
  padding: 14px;
  resize: vertical;
  font-size: 14px;
  line-height: 1.75;
  min-height: 120px;
  color: #1f2937;
}

.hint {
  margin-bottom: 18px;
  padding: 12px 14px;
  border-radius: 12px;
  font-size: 14px;
}

.warning {
  background: #fff4e5;
  color: #b45309;
  border: 1px solid #f1c96d;
}

.checking {
  background: #eef2ff;
  color: #4338ca;
  border: 1px solid #a5b4fc;
}

.error {
  background: #fbeaea;
  color: #b91c1c;
  border: 1px solid #f5c2c2;
}

.success {
  background: #eff7ee;
  color: #166534;
  border: 1px solid #8ccf94;
}

.action-row {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.check-btn {
  background: #fff;
  color: #4338ca;
  border: 2px solid #4338ca;
  border-radius: 14px;
  padding: 12px 24px;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}

.check-btn:hover:not(:disabled) {
  background: #eef2ff;
}

.check-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.submit-btn {
  background: #9ca3af;
  color: white;
  border: none;
  padding: 12px 26px;
  font-size: 15px;
  border-radius: 12px;
  cursor: pointer;
  transition: background 0.2s;
}

.submit-btn:hover {
  background: #6b7280;
}

.submit-btn--ready {
  background: #1d4ed8;
}

.submit-btn--ready:hover {
  background: #1e40af;
}

.submit-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.loading {
  text-align: center;
  padding: 40px;
  color: #6b7280;
}

.empty-state {
  text-align: center;
  padding: 60px 20px;
  color: #9ca3af;
  font-size: 16px;
}

.rejected-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.rejected-card {
  border: 1px solid #fee2e2;
  border-radius: 12px;
  background: #fef2f2;
  overflow: hidden;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 16px;
  background: #fde8e8;
  border-bottom: 1px solid #fee2e2;
}

.card-header h4 {
  margin: 0;
  color: #7f1d1d;
  font-size: 15px;
  flex: 1;
}

.reject-badge {
  background: #f56c6c;
  color: white;
  padding: 4px 12px;
  border-radius: 16px;
  font-size: 12px;
  font-weight: 500;
  white-space: nowrap;
  margin-left: 12px;
}

.card-body {
  padding: 16px;
}

.answer-section,
.reason-section,
.time-section {
  margin-bottom: 12px;
}

.answer-section:last-child,
.reason-section:last-child,
.time-section:last-child {
  margin-bottom: 0;
}

.answer-section label,
.reason-section label,
.time-section label {
  display: block;
  font-weight: 600;
  color: #374151;
  margin-bottom: 6px;
  font-size: 14px;
}

.answer-section p,
.reason-section p {
  margin: 0;
  color: #1f2937;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

.reason-section {
  padding: 12px;
  background: #fff7ed;
  border-left: 3px solid #f56c6c;
  border-radius: 6px;
}

.reason-text {
  color: #b91c1c;
  font-weight: 500;
}

.time-section {
  color: #9ca3af;
  font-size: 13px;
}

@media (max-width: 900px) {
  .contribute-page {
    padding: 12px;
  }

  .back-float-btn {
    position: static;
    margin-bottom: 12px;
  }

  .contribute-card {
    padding: 16px;
    border-radius: 14px;
  }

  .action-row {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
