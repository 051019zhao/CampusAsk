<template>
  <div class="auth-container">
    <div class="auth-box">
      <!-- 标题 -->
      <h2>{{ isLogin ? 'CampusAsk 登录' : 'CampusAsk 注册' }}</h2>

      <form @submit.prevent="handleSubmit">
        <!-- 账号 -->
        <div class="form-item">
          <label>学号/工号</label>
          <input
              v-model="formData.username"
              type="text"
              placeholder="请输入学号或工号"
              required
          />
        </div>

        <!-- 密码 -->
        <div class="form-item">
          <label>密码</label>
          <input
              v-model="formData.password"
              type="password"
              placeholder="请输入密码"
              required
          />
        </div>

        <!-- 确认密码（仅注册可见） -->
        <div v-if="!isLogin" class="form-item">
          <label>确认密码</label>
          <input
              v-model="formData.confirmPassword"
              type="password"
              placeholder="请再次输入密码"
              required
          />
        </div>

        <!-- ========================================== -->
        <!-- 新增：身份选择区域（数字 0 和 1 完美对接后端） -->
        <!-- ========================================== -->
        <div class="form-item">
          <label>登录身份</label>
          <div class="role-radio-group">
            <label class="radio-label">
              <input
                  type="radio"
                  v-model="formData.role"
                  :value="0"
                  name="role"
              />
              <span class="custom-radio"></span>
              学生
            </label>
            <label class="radio-label">
              <input
                  type="radio"
                  v-model="formData.role"
                  :value="1"
                  name="role"
              />
              <span class="custom-radio"></span>
              管理员
            </label>
          </div>
        </div>
        <!-- ========================================== -->

        <!-- 提交按钮 -->
        <button type="submit" class="submit-btn">
          {{ isLogin ? '立即登录' : '注册账号' }}
        </button>
      </form>

      <!-- 底部切换提示 -->
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
import { useRouter } from 'vue-router'

// 是否为登录状态
const isLogin = ref(true)

const router = useRouter()
// 表单数据绑定（初始化时 role 默认为 0，即学生）
const formData = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  role: 0 // 0: 学生, 1: 管理员
})

// 切换登录/注册状态，清空表单并重置身份为学生
const toggleStatus = () => {
  isLogin.value = !isLogin.value
  formData.username = ''
  formData.password = ''
  formData.confirmPassword = ''
  formData.role = 0
}

// 提交表单逻辑
const handleSubmit = () => {
  if (isLogin.value) {
    handleLogin()
  } else {
    handleRegister()
  }
}

// 登录业务
const handleLogin = async () => {
  try {
    console.log('正在提交登录，传输数据：', formData)
    // 此时提交的参数包含：username, password, role(0或1)
    const res = await loginAPI({
      username: formData.username,
      password: formData.password,
      role: formData.role
    })

    if (res.data && res.data.token) {
      localStorage.setItem('token', res.token)
      // 顺便可以存一下身份，方便后续页面判断展示
      localStorage.setItem('role', formData.role)
      alert('登录成功！')
      router.push('/')
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
    console.log('正在提交注册，传输数据：', formData)
    // 此时提交的参数包含：username, password, role(0或1)
    await registerAPI({
      username: formData.username,
      password: formData.password,
      role: formData.role
    })

    alert('注册成功，请登录！')
    toggleStatus()
  } catch (error) {
    console.error('注册失败', error)
  }
}
</script>

<style scoped>
/* 基础容器 */
.auth-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 80vh;
  font-family: sans-serif;
  padding: 0 20px;
}

/* 核心登录框 */
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
  margin-bottom: 30px;
  font-size: 24px;
  font-weight: 600;
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

/* ========================================== */
/* 新增：单选框美化样式 */
/* ========================================== */
.role-radio-group {
  display: flex;
  gap: 30px;
  padding: 5px 2px;
}

.radio-label {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  font-size: 15px !important; /* 覆盖上面的label大小 */
  color: #333 !important;
  font-weight: normal !important;
}

/* 隐藏原生收音机圆圈 */
.radio-label input[type="radio"] {
  display: none;
}

/* 自定义漂亮的圆圈 */
.custom-radio {
  width: 18px;
  height: 18px;
  border: 2px solid #ddd;
  border-radius: 50%;
  display: inline-block;
  position: relative;
  transition: all 0.2s ease;
}

/* 当被选中时，外圈变绿 */
.radio-label input[type="radio"]:checked + .custom-radio {
  border-color: #42b883;
  background: #42b883;
}

/* 当被选中时，内嵌一个小百点 */
.radio-label input[type="radio"]:checked + .custom-radio::after {
  content: '';
  width: 6px;
  height: 6px;
  background: white;
  border-radius: 50%;
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
}
/* ========================================== */

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

.submit-btn:active {
  transform: scale(0.99);
}

.switch-tip {
  text-align: center;
  margin-top: 25px;
  margin-bottom: 0;
  font-size: 14px;
  color: #999;
}

.switch-tip span {
  color: #42b883;
  cursor: pointer;
  font-weight: bold;
  margin-left: 5px;
  transition: color 0.2s;
}

.switch-tip span:hover {
  color: #33a06f;
  text-decoration: underline;
}
</style>