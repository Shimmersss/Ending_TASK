<template>
  <div class="login-form glass">
    <h2>用户登录</h2>
    <p class="tip">测试账号：test / 123456。后端不可用时也可以进入系统查看界面。</p>

    <form @submit.prevent="handleLogin">
      <div class="form-item">
        <label>用户名</label>
        <input v-model="form.userName" type="text" required placeholder="请输入用户名">
      </div>

      <div class="form-item">
        <label>密码</label>
        <input v-model="form.passWord" type="password" required placeholder="请输入密码">
      </div>

      <button class="btn btn-primary submit-btn" type="submit" :disabled="loading">
        {{ loading ? '登录中...' : '登录' }}
      </button>

      <div v-if="error" class="error">{{ error }}</div>
    </form>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { login } from '../api/auth'

const TEST_USER = {
  userName: 'test',
  passWord: '123456'
}

const form = ref({
  userName: TEST_USER.userName,
  passWord: TEST_USER.passWord
})
const loading = ref(false)
const error = ref('')

const handleLogin = async () => {
  try {
    loading.value = true
    error.value = ''

    if (form.value.userName === TEST_USER.userName && form.value.passWord === TEST_USER.passWord) {
      localStorage.setItem('user', JSON.stringify({ userName: TEST_USER.userName }))
      window.location.href = '/'
      return
    }

    const response = await login(form.value)
    localStorage.setItem('user', JSON.stringify(response.data || response))
    window.location.href = '/'
  } catch (err) {
    error.value = err.message || '登录失败，请检查账号或后端服务。'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-form {
  width: min(440px, 100%);
  margin: 0 auto;
  padding: 28px;
  border-radius: var(--radius-xl);
}

h2 {
  font-size: 24px;
}

.tip {
  margin: 8px 0 18px;
  color: var(--text-muted);
  font-size: 13px;
}

.form-item {
  margin-bottom: 16px;
  display: grid;
  gap: 8px;
}

label {
  color: var(--text-muted);
  font-weight: 700;
}

input {
  width: 100%;
  padding: 13px 14px;
  border: 1px solid rgba(255, 255, 255, 0.14);
  border-radius: var(--radius-md);
  background: rgba(5, 12, 20, 0.52);
  color: var(--text-main);
  outline: none;
  transition:
    border-color 160ms ease,
    box-shadow 160ms ease,
    transform 160ms ease;
}

input::placeholder {
  color: rgba(217, 232, 247, 0.45);
}

input:focus {
  border-color: rgba(66, 233, 208, 0.72);
  box-shadow: 0 0 0 4px rgba(66, 233, 208, 0.12);
  transform: translateY(-1px);
}

.submit-btn {
  width: 100%;
  margin-top: 8px;
}

.error {
  margin-top: 12px;
  padding: 10px 12px;
  border-radius: var(--radius-md);
  border: 1px solid rgba(255, 122, 122, 0.35);
  background: rgba(255, 122, 122, 0.1);
  color: #ffd6d6;
}
</style>
