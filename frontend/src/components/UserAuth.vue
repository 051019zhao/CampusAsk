<template>
  <div class="auth-wrapper">
    <div class="auth-card">
      <h2 class="auth-title">CampusAsk {{ isLogin ? '登录' : '注册' }}</h2>

      <form @submit.prevent="handleSubmit">
        <div class="form-item">
          <label>学号/工号</label>
          <input v-model="formData.username" type="text" placeholder="请输入学号或工号" required />
        </div>

        <div class="form-item">
          <label>密码</label>
          <input v-model="formData.password" type="password" placeholder="请输入密码" required />
        </div>

        <div v-if="!isLogin" class="form-item">
          <label>确认密码</label>
          <input v-model="formData.confirmPassword" type="password" placeholder="请再次输入密码" required />
        </div>

        <div v-if="isLogin" class="form-item">
          <label>登录身份</label>
          <div class="role-group">
            <label class="role-option">
              <input type="radio" v-model="formData.role" value="student" />
              <span>学生</span>
            </label>
            <label class="role-option">
              <input type="radio" v-model="formData.role" value="admin" />
              <span>管理员</span>
            </label>
          </div>
        </div>

        <button type="submit" class="submit-btn">
          {{ isLogin ? '立即登录' : '立即注册' }}
        </button>
      </form>

      <p class="switch-tip">
        {{ isLogin ? '没有账号？' : '已有账号？' }}
        <span @click="toggleStatus">{{ isLogin ? '立即注册' : '去登录' }}</span>
      </p>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { loginAPI, registerAPI } from '@/api/auth'

const router = useRouter()
const isLogin = ref(true)

const formData = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  role: 'student'
})

const toggleStatus = () => {
  isLogin.value = !isLogin.value
  formData.username = ''
  formData.password = ''
  formData.confirmPassword = ''
  formData.role = 'student'
}

const handleSubmit = () => {
  isLogin.value ? handleLogin() : handleRegister()
}

const handleLogin = async () => {
  try {
    const res = await loginAPI({
      username: formData.username,
      password: formData.password,
      role: formData.role
    })
    if (res.code === 200) {
      localStorage.setItem('token', res.data)
      localStorage.setItem('role', formData.role)
      router.push('/')
    } else {
      alert(res.message || '登录失败')
    }
  } catch (error) {
    console.error('登录失败', error)
  }
}

const handleRegister = async () => {
  if (formData.password !== formData.confirmPassword) {
    alert('两次输入的密码不一致！')
    return
  }
  try {
    const res = await registerAPI({
      username: formData.username,
      password: formData.password
    })
    if (res.code === 200) {
      alert('注册成功，请登录！')
      toggleStatus()
    } else {
      alert(res.message || '注册失败')
    }
  } catch (error) {
    console.error('注册失败', error)
  }
}
</script>

<style scoped>
.auth-wrapper {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f0f2f5;
}

.auth-card {
  background: #fff;
  border-radius: 12px;
  padding: 48px 40px;
  width: 400px;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.08);
}

.auth-title {
  text-align: center;
  font-size: 24px;
  font-weight: 700;
  color: #1a1a1a;
  margin: 0 0 32px;
}

.form-item {
  margin-bottom: 20px;
}

.form-item label {
  display: block;
  font-size: 14px;
  color: #555;
  margin-bottom: 8px;
}

.form-item input[type="text"],
.form-item input[type="password"] {
  width: 100%;
  padding: 10px 14px;
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  font-size: 14px;
  box-sizing: border-box;
  outline: none;
  transition: border-color 0.2s;
}

.form-item input[type="text"]:focus,
.form-item input[type="password"]:focus {
  border-color: #4dc3b8;
}

.role-group {
  display: flex;
  gap: 32px;
  padding-top: 4px;
}

.role-option {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  color: #333;
  cursor: pointer;
}

.role-option input[type="radio"] {
  accent-color: #4dc3b8;
  width: 16px;
  height: 16px;
}

.submit-btn {
  width: 100%;
  padding: 12px;
  background: #4dc3b8;
  color: #fff;
  border: none;
  border-radius: 6px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  margin-top: 8px;
  transition: background 0.2s;
}

.submit-btn:hover {
  background: #3aada2;
}

.switch-tip {
  text-align: center;
  margin-top: 20px;
  font-size: 14px;
  color: #888;
}

.switch-tip span {
  color: #4dc3b8;
  cursor: pointer;
  font-weight: 500;
}

.switch-tip span:hover {
  text-decoration: underline;
}
</style>
