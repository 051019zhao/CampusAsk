<template>
  <div class="auth-container">
    <div class="auth-box">
      <h2>{{ isLogin ? '校园问答 - 登录' : '校园问答 - 注册' }}</h2>

      <form @submit.prevent="handleSubmit">
        <div class="form-item">
          <label>邮箱/学号</label>
          <input
              v-model="formData.username"
              type="text"
              placeholder="请输入邮箱或学号"
              required
          />
        </div>

        <div class="form-item">
          <label>密码</label>
          <input
              v-model="formData.password"
              type="password"
              placeholder="请输入密码"
              required
          />
        </div>

        <div v-if="!isLogin" class="form-item">
          <label>确认密码</label>
          <input
              v-model="formData.confirmPassword"
              type="password"
              placeholder="请再次输入密码"
              required
          />
        </div>

        <button type="submit" class="submit-btn">
          {{ isLogin ? '立即登录' : '注册账号' }}
        </button>
      </form>

      <p class="switch-tip">
        {{ isLogin ? '还没有账号？' : '已有账号？' }}
        <span @click="toggleStatus">{{ isLogin ? '立即注册' : '去登录' }}</span>
      </p>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { loginAPI, registerAPI } from '@/api/auth'

// 是否为登录状态（true 为登录，false 为注册）
const isLogin = ref(true)

// 表单数据绑定
const formData = reactive({
  username: '',
  password: '',
  confirmPassword: ''
})

// 切换登录/注册状态，顺便清空表单
const toggleStatus = () => {
  isLogin.value = !isLogin.value
  formData.username = ''
  formData.password = ''
  formData.confirmPassword = ''
}

// 提交表单逻辑
const handleSubmit = () => {
  if (isLogin.value) {
    // 执行登录逻辑
    handleLogin()
  } else {
    // 执行注册逻辑
    handleRegister()
  }
}

// 登录业务
const handleLogin = async () => {
  try {
    console.log('正在提交登录...')
    const res = await loginAPI({
      username: formData.username,
      password: formData.password
    })

    // 假设后端登录成功返回一个 token
    if (res.token) {
      localStorage.setItem('token', res.token) // 保存令牌
      alert('登录成功！')
      // router.push('/home') // 跳转到首页
    }
  } catch (error) {
    console.error('登录失败', error)
  }
}

// 注册业务
const handleRegister = async () => {
  if (formData.password !== formData.confirmPassword) {
    alert('两次输入的密码不一致！')
    return
  }

  try {
    console.log('正在提交注册...')
    await registerAPI({
      username: formData.username,
      password: formData.password
    })

    alert('注册成功，请登录！')
    toggleStatus() // 自动切回登录界面
  } catch (error) {
    console.error('注册失败', error)
  }
}
</script>