<template>
  <div class="contribute-page">
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
            @input="checkSensitive"
          ></textarea>
        </div>

        <div class="form-group">
          <label>回答</label>
          <textarea
            v-model="answer"
            placeholder="请输入该问题的答案，确保内容准确。"
            rows="6"
            @input="checkSensitive"
          ></textarea>
        </div>

        <div v-if="sensitiveHint" class="hint warning">
          {{ sensitiveHint }}
        </div>
        <div v-if="errorMessage" class="hint error">
          {{ errorMessage }}
        </div>
        <div v-if="statusMessage" class="hint success">
          {{ statusMessage }}
        </div>

        <div class="action-row">
          <button class="submit-btn" :disabled="submitting" @click="submitContribution">
            {{ submitting ? '提交中...' : '提交审核' }}
          </button>
        </div>
      </div>

      <!-- 未通过投稿 -->
      <div v-if="activeTab === 'rejected'" class="tab-content">
        <div class="page-header">
          <p>这些投稿因某些原因未通过审核。请查看驳回原因后重新编辑提交。</p>
        </div>

        <div v-if="rejectedLoading" class="loading">加载中...</div>
        <div v-else-if="rejectedContributions.length === 0" class="empty-state">
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
import { submitContribution, getSensitiveWords, getMyContributions } from '@/api/contribution'

export default {
  name: 'ContributePage',
  data() {
    return {
      activeTab: 'submit',
      question: '',
      answer: '',
      submitting: false,
      errorMessage: '',
      statusMessage: '',
      foundWords: [],
      sensitiveWords: [
        '政治',
        '色情',
        '暴力',
        '赌博',
        '违规',
        '违禁',
        '敏感词',
        '涉政',
        '涉黄',
        '谣言'
      ],
      rejectedContributions: [],
      rejectedLoading: false
    }
  },
  created() {
    this.loadSensitiveWords()
  },
  computed: {
    sensitiveHint() {
      if (!this.foundWords.length) {
        return ''
      }
      return `检测到敏感词：${this.foundWords.join('，')}，请修改后再提交。`
    }
  },
  methods: {
    getUserId() {
      const raw = localStorage.getItem('userId')
      const parsed = Number.parseInt(raw || '', 10)
      return Number.isFinite(parsed) ? parsed : null
    },
    async loadSensitiveWords() {
      try {
        const response = await getSensitiveWords()
        if (response?.code === 200 && Array.isArray(response.data)) {
          this.sensitiveWords = response.data.map((word) => String(word || '').toLowerCase())
        }
      } catch (error) {
        // 保持默认敏感词列表，后端仍会做最终校验
      }
    },
    checkSensitive() {
      const text = `${this.question}\n${this.answer}`.toLowerCase()
      this.foundWords = this.sensitiveWords.filter((word) => word && text.includes(word))
    },
    resetForm() {
      this.question = ''
      this.answer = ''
      this.foundWords = []
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
        const response = await getMyContributions()
        if (response?.code === 200 && Array.isArray(response.data)) {
          this.rejectedContributions = response.data.filter(item => item.status === 'rejected')
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
      this.checkSensitive()

      if (this.foundWords.length) {
        this.errorMessage = this.sensitiveHint
        return
      }
      if (!this.question.trim()) {
        this.errorMessage = '请填写投稿问题。'
        return
      }
      if (!this.answer.trim()) {
        this.errorMessage = '请填写投稿回答。'
        return
      }

      this.submitting = true
      try {
        const userId = this.getUserId()
        const response = await submitContribution({
          question: this.question.trim(),
          answer: this.answer.trim(),
          userId: userId ? String(userId) : ''
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
}

.submit-btn {
  background: #1d4ed8;
  color: white;
  border: none;
  padding: 12px 26px;
  font-size: 15px;
  border-radius: 12px;
  cursor: pointer;
}

.submit-btn:hover {
  background: #1e40af;
}

.submit-btn:disabled {
  opacity: 0.65;
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
</style>
