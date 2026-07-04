<template>
  <div class="auth-container">
    <div class="auth-box">
      <h2 v-if="isReset">{{ '重置密码' }}</h2>
      <h2 v-else>{{ isLogin ? 'CampusAsk 登录' : 'CampusAsk 注册' }}</h2>

      <!-- ===== 忘记密码模式 ===== -->
      <form v-if="isReset" @submit.prevent="handleReset">
        <div class="reset-notice">
          <p>请联系管理员 <strong>3265585758@qq.com</strong> 获取密码重置密钥。</p>
        </div>
        <div class="form-item">
          <label>学号/工号</label>
          <input v-model="resetForm.username" type="text" placeholder="请输入学号或工号" required />
        </div>
        <div class="form-item">
          <label>重置密钥</label>
          <input v-model="resetForm.resetKey" type="text" placeholder="请输入管理员提供的密钥" required />
        </div>
        <div class="form-item">
          <label>新密码</label>
          <div class="input-wrap">
            <input v-model="resetForm.newPassword" :type="showResetPwd ? 'text' : 'password'" placeholder="请输入新密码（至少6位）" required />
            <span class="pwd-toggle" @click="showResetPwd=!showResetPwd">{{ showResetPwd ? '🙈' : '👁' }}</span>
          </div>
        </div>
        <div v-if="errorMsg" class="error-msg">{{ errorMsg }}</div>
        <button type="submit" class="submit-btn" :disabled="submitting">{{ submitting ? '处理中...' : '重置密码' }}</button>
        <p class="switch-tip"><span @click="isReset=false">← 返回登录</span></p>
      </form>

      <!-- ===== 登录/注册模式 ===== -->
      <form v-else @submit.prevent="handleSubmit">
        <div class="form-item">
          <label>学号/工号</label>
          <input v-model="formData.username" type="text" placeholder="请输入学号或工号" required />
        </div>
        <div class="form-item">
          <label>密码</label>
          <div class="input-wrap">
            <input v-model="formData.password" :type="showPwd ? 'text' : 'password'" placeholder="请输入密码" required />
            <span class="pwd-toggle" @click="showPwd=!showPwd">{{ showPwd ? '🙈' : '👁' }}</span>
          </div>
        </div>
        <div v-if="!isLogin" class="form-item">
          <label>确认密码</label>
          <div class="input-wrap">
            <input v-model="formData.confirmPassword" :type="showConfirmPwd ? 'text' : 'password'" placeholder="请再次输入密码" required />
            <span class="pwd-toggle" @click="showConfirmPwd=!showConfirmPwd">{{ showConfirmPwd ? '🙈' : '👁' }}</span>
          </div>
        </div>
        <!-- V4: 管理员注册密钥 -->
        <div v-if="!isLogin && formData.role == 1" class="form-item">
          <label>管理员注册密钥</label>
          <input v-model="formData.registerKey" type="text" placeholder="请输入管理员注册密钥" required />
        </div>
        <div class="form-item">
          <label>登录身份</label>
          <div class="role-radio-group">
            <label class="radio-label">
              <input type="radio" v-model="formData.role" :value="0" name="role" />
              <span class="custom-radio"></span>学生
            </label>
            <label class="radio-label">
              <input type="radio" v-model="formData.role" :value="1" name="role" />
              <span class="custom-radio"></span>管理员
            </label>
          </div>
        </div>
        <div v-if="errorMsg" class="error-msg">{{ errorMsg }}</div>
        <button type="submit" class="submit-btn" :disabled="submitting">
          {{ submitting ? '处理中...' : (isLogin ? '立即登录' : '注册账号') }}
        </button>
      </form>

      <p class="switch-tip" v-if="!isReset">
        {{ isLogin ? '还没有账号？' : '已有账号？' }}
        <span @click="toggleStatus">{{ isLogin ? '立即注册' : '去登录' }}</span>
      </p>
      <p class="forgot-tip" v-if="isLogin && !isReset">
        <span @click="isReset=true">忘记密码？</span>
      </p>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { loginAPI, registerAPI } from '@/api/auth'
import request from '@/utils/request'
import { useRouter } from 'vue-router'

const isLogin = ref(true)
const isReset = ref(false)
const submitting = ref(false)
const errorMsg = ref('')
const showPwd = ref(false)
const showConfirmPwd = ref(false)
const showResetPwd = ref(false)
const router = useRouter()

const formData = reactive({
  username: '', password: '', confirmPassword: '', role: 0, registerKey: ''
})

const resetForm = reactive({
  username: '', resetKey: '', newPassword: ''
})

const toggleStatus = () => {
  isLogin.value = !isLogin.value; errorMsg.value = ''
  formData.username = ''; formData.password = ''; formData.confirmPassword = ''; formData.role = 0; formData.registerKey = ''
}

const handleSubmit = () => { errorMsg.value = ''; isLogin.value ? handleLogin() : handleRegister() }

const handleLogin = async () => {
  submitting.value = true; errorMsg.value = ''
  try {
    const res = await loginAPI({ username: formData.username, password: formData.password, role: formData.role })
    if (res.data && res.data.token) {
      localStorage.setItem('token', res.data.token)
      localStorage.setItem('username', formData.username)
      localStorage.setItem('role', formData.role)
      router.push('/')
    }
  } catch (error) {
    errorMsg.value = error.response?.data?.message || '账号或密码错误'
  } finally { submitting.value = false }
}

const handleRegister = async () => {
  if (formData.password !== formData.confirmPassword) { errorMsg.value = '两次密码不一致！'; return }
  submitting.value = true; errorMsg.value = ''
  try {
    await registerAPI({
      username: formData.username, password: formData.password, role: formData.role,
      registerKey: formData.registerKey || ''
    })
    isLogin.value = true; errorMsg.value = ''
    formData.username = ''; formData.password = ''; formData.confirmPassword = ''; formData.role = 0; formData.registerKey = ''
  } catch (error) {
    errorMsg.value = error.response?.data?.message || '注册失败，请检查信息'
  } finally { submitting.value = false }
}

const handleReset = async () => {
  if (resetForm.newPassword.length < 6) { errorMsg.value = '新密码至少6位'; return }
  submitting.value = true; errorMsg.value = ''
  try {
    const res = await request({ url: '/auth/reset-password', method: 'post', data: resetForm })
    if (res.code === 200) { isReset.value = false; resetForm.username = ''; resetForm.resetKey = ''; resetForm.newPassword = '' }
    else errorMsg.value = res.message || '重置失败'
  } catch (e) { errorMsg.value = e.response?.data?.message || '网络错误' }
  finally { submitting.value = false }
}
</script>

<style scoped>
/* 基础容器 */
.auth-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 80vh;
  font-family: "Microsoft YaHei","PingFang SC",sans-serif;
  padding: 0 20px;
  background: linear-gradient(135deg, #e8f0f5 0%, #d5e3ec 50%, #c8dbe6 100%);
}

/* 核心登录框 */
.auth-box {
  width: 100%;
  max-width: 400px;
  background: #fff;
  border: none;
  border-radius: 20px;
  padding: 40px 32px;
  box-shadow: 0 8px 36px rgba(43,85,105,.12);
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
  padding: 12px 14px;
  font-size: 14px;
  border: 1px solid #e0e5ea;
  border-radius: 10px;
  outline: none;
  transition: all .25s;
  background: #f8fafb;
}
.form-item input:focus {
  border-color: #2b5569;
  box-shadow: 0 0 0 3px rgba(43,85,105,.08);
  background: #fff;
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
  padding: 13px;
  background: linear-gradient(135deg, #2b5569, #3c6476);
  color: #fff;
  border: none;
  border-radius: 12px;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  margin-top: 12px;
  transition: all .25s;
  box-shadow: 0 4px 14px rgba(43,85,105,.2);
}
.submit-btn:hover { transform: translateY(-1px); box-shadow: 0 6px 20px rgba(43,85,105,.3); }
.submit-btn:active { transform: scale(.98); }

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

.forgot-tip { text-align: center; margin-top: 8px; font-size: 13px; }
.forgot-tip span { color: #909399; cursor: pointer; }
.forgot-tip span:hover { color: #42b883; text-decoration: underline; }

.reset-notice { background: #e6f7ff; border: 1px solid #91d5ff; border-radius: 8px; padding: 12px; margin-bottom: 16px; font-size: 13px; color: #1890ff; }
.reset-notice strong { color: #f56c6c; }

.input-wrap { position: relative; display: flex; align-items: center; }
.input-wrap input { flex: 1; padding-right: 36px; }
.pwd-toggle { position: absolute; right: 10px; cursor: pointer; font-size: 16px; user-select: none; }

.error-msg { background: #fef2f2; border: 1px solid #f7caca; color: #b42318; border-radius: 6px; padding: 8px 12px; font-size: 13px; margin-bottom: 12px; }

.submit-btn:disabled { opacity: 0.6; cursor: not-allowed; }
</style>