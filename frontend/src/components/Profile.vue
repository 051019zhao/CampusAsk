<template>
  <div class="auth-container">
    <div class="auth-box">
      <h2>个人信息管理</h2>
      <p class="subtitle">CampusAsk 校园生活百事通 · 候选流</p>

      <form @submit.prevent="handleSubmit">
        <div class="form-item">
          <label>学号/工号 (不可修改)</label>
          <input
              v-model="formData.username"
              type="text"
              disabled
              class="disabled-input"
          />
        </div>

        <div class="form-item">
          <label>当前昵称</label>
          <input
              v-model="formData.nickname"
              type="text"
              placeholder="请输入您的新昵称"
              required
          />
        </div>

        <div class="form-item">
          <label>个性名言</label>
          <textarea
              v-model="formData.motto"
              placeholder="写下一句你的人生格言吧...（选填，最多100字）"
              class="motto-textarea"
              rows="3"
          ></textarea>
        </div>

        <div class="password-divider">
          <span>安全设置（如需修改密码请填写）</span>
        </div>

        <div class="form-item">
          <label>原密码</label>
          <input
              v-model="formData.oldPassword"
              type="password"
              placeholder="修改密码时必填，不改则留空"
          />
        </div>

        <div class="form-item">
          <label>新密码</label>
          <input
              v-model="formData.newPassword"
              type="password"
              placeholder="新密码长度不能少于 6 位"
          />
        </div>

        <div class="form-item">
          <label>确认新密码</label>
          <input
              v-model="formData.confirmPassword"
              type="password"
              placeholder="请再次输入新密码"
          />
        </div>

        <button type="submit" class="submit-btn" :disabled="loading">
          {{ loading ? '正在保存...' : '保存修改' }}
        </button>
      </form>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { getUserProfile, updateUserProfile } from '@/api/auth' // 同时平级引入两个接口
import { useRouter } from 'vue-router'
import { toastError, toastSuccess } from '@/utils/toast'

const router = useRouter()

const loading = ref(false)

// 表单数据绑定（：在这里补上 motto 和 avatarUrl 字段）
const formData = reactive({
  username: '',      // 初始留空，由数据库实时读取后填充
  nickname: '',      // 初始留空
  motto: '',         // 新增个性名言绑定
  avatarUrl: '',     // 新增头像 URL 暂存绑定（用于配合主页上传后的无缝持久化）
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

// 🚀 核心逻辑改造：页面挂载时，直接从后端数据库读取真实数据
onMounted(async () => {
  try {
    loading.value = true
    const res = await getUserProfile() // 向后端发送 GET 请求

    if (res.code === 200 && res.data) {
      // 将数据库里的学号、昵称真实渲染到对应的输入框中
      formData.username = res.data.username
      formData.nickname = res.data.nickname || '新用户'

      // 同步回显数据库中已有的个性名言和头像链接
      formData.motto = res.data.motto || ''
      formData.avatarUrl = res.data.avatarUrl || ''

      // 同步刷新一下本地缓存中的昵称，确保导航栏欢迎语同步
      if (res.data.nickname) {
        localStorage.setItem('nickname', res.data.nickname)
      }
    } else {
      toastError(res.message || '获取个人信息失败，请重新登录')
    }
  } catch (error) {
    console.error('从数据库读取用户信息失败:', error)
    toastError('网络异常，用户信息加载失败')
  } finally {
    loading.value = false
  }
})

// 提交表单逻辑
const handleSubmit = async () => {
  // 严格执行 Week 3 规约的长度与友好备选流校验
  if (formData.nickname.trim().length === 0 || formData.nickname.length > 20) {
    toastError('昵称长度必须在 1 到 20 个字符之间')
    return
  }

  // 对新追加的名言进行前端长度拦截，对齐后端写的 100 字限制
  if (formData.motto && formData.motto.length > 100) {
    toastError('个性名言长度不能超过 100 个字符')
    return
  }

  // 如果填写了新密码，触发修改密码流校验
  if (formData.oldPassword || formData.newPassword || formData.confirmPassword) {
    if (!formData.oldPassword || !formData.newPassword || !formData.confirmPassword) {
      toastError('修改密码时，原密码、新密码和确认密码均不能为空')
      return
    }
    if (formData.newPassword.length < 6) {
      toastError('新密码长度不能少于 6 位')
      return
    }
    if (formData.newPassword !== formData.confirmPassword) {
      toastError('两次输入的新密码不一致')
      return
    }
  }

  loading.value = true
  try {
    // 组装传输数据（✨ 补丁 5：把名言和头像 URL 打包带过去送给 /auth/profile 接口）
    const updateData = {
      nickname: formData.nickname,
      motto: formData.motto,          // 带着新名言
      avatarUrl: formData.avatarUrl,  // 带着当前最新的头像链接
      oldPassword: formData.oldPassword || null,
      newPassword: formData.newPassword || null
    }

    const res = await updateUserProfile(updateData)

    if (res.code === 200 || res.success) {
      toastSuccess('个人信息更新成功')
      localStorage.setItem('nickname', formData.nickname)
      if (formData.username) {
        localStorage.setItem('userId', formData.username)
        localStorage.setItem('username', formData.username)
      }
      if (formData.avatarUrl) {
        localStorage.setItem('avatarUrl', formData.avatarUrl)
      }

      // 广播资料更新事件，通知其他页面即时刷新用户信息
      window.dispatchEvent(new CustomEvent('profile-updated', {
        detail: {
          nickname: formData.nickname,
          username: formData.username,
          avatarUrl: formData.avatarUrl
        }
      }))

      // 置空密码框
      formData.oldPassword = ''
      formData.newPassword = ''
      formData.confirmPassword = ''

      router.push('/user-center')
    } else {
      toastError(res.message || '更新失败，请检查输入')
    }
  } catch (error) {
    console.error('修改个人信息失败', error)
    toastError('网络或系统异常，请稍后再试')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
/* 🎯 原有 CSS 全部完美保留，下面仅追补原生文本域的绿调视觉反馈样式 */

.auth-container {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  min-height: 80vh;
  font-family: sans-serif;
  padding: 20px;
}

.auth-box {
  width: 100%;
  max-width: 400px;
  background: #ffffff;
  border: 1px solid #eee;
  border-radius: 12px;
  padding: 35px 30px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.04);
}

h2 {
  text-align: center;
  color: #2c3e50;
  margin-top: 0;
  margin-bottom: 5px;
  font-size: 24px;
  font-weight: 600;
}

.subtitle {
  text-align: center;
  font-size: 14px;
  color: #999;
  margin-top: 0;
  margin-bottom: 30px;
}

.form-item {
  margin-bottom: 20px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.form-item label {
  font-size: 14px;
  color: #555;
  font-weight: bold;
  padding-left: 2px;
}

.form-item input[type="text"],
.form-item input[type="password"] {
  padding: 12px;
  font-size: 15px;
  border: 1px solid #ddd;
  border-radius: 6px;
  outline: none;
  transition: all 0.25s ease;
}

.form-item input:focus {
  border-color: #42b883;
  box-shadow: 0 0 0 3px rgba(66, 184, 131, 0.1);
}

/* ✨ 新增：手写多行文本输入域样式（完美融合原生绿调） */
.motto-textarea {
  padding: 12px;
  font-size: 15px;
  border: 1px solid #ddd;
  border-radius: 6px;
  outline: none;
  resize: none; /* 禁止拖动破坏表单布局 */
  font-family: inherit;
  transition: all 0.25s ease;
}

.motto-textarea:focus {
  border-color: #42b883;
  box-shadow: 0 0 0 3px rgba(66, 184, 131, 0.1);
}

.disabled-input {
  background-color: #f9f9f9;
  color: #aaa;
  cursor: not-allowed;
}

.password-divider {
  display: flex;
  align-items: center;
  margin: 25px 0 20px 0;
  font-size: 13px;
  color: #ccc;
}

.password-divider::before,
.password-divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background-color: #eee;
}

.password-divider span {
  padding: 0 10px;
}

.submit-btn {
  width: 100%;
  padding: 12px;
  background: #42b883;
  color: white;
  border: none;
  border-radius: 6px;
  font-size: 16px;
  font-weight: bold;
  cursor: pointer;
  margin-top: 10px;
  transition: background 0.2s ease;
}

.submit-btn:hover {
  background: #33a06f;
}

.submit-btn:disabled {
  background: #acdcc4;
  cursor: not-allowed;
}

.submit-btn:active {
  transform: scale(0.99);
}
</style>