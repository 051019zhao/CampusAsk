<template>
  <div class="contribute-page">
    <div class="contribute-card">
      <div class="page-header">
        <h2>投稿新问答</h2>
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
  </div>
</template>

<script>
import { submitContribution, getSensitiveWords } from '@/api/contribution'

export default {
  name: 'ContributePage',
  data() {
    return {
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
      ]
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

.submit-btn:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}
</style>
