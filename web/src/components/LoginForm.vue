<template>
  <div class="login-form glass">
    <h2>用户登录</h2>
    <p class="tip">测试账号: test / 123456（无数据库时可用）</p>
    <form @submit.prevent="handleLogin">
      <div class="form-item">
        <label>用户名</label>
        <input v-model="form.userName" type="text" required placeholder="请输入用户名">
      </div>
      <div class="form-item">
        <label>密码</label>
        <input v-model="form.passWord" type="password" required placeholder="请输入密码">
      </div>
      <button class="btn btn-primary" type="submit" :disabled="loading">
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

    // 无数据库时，允许使用本地测试账号直接登录
    if (form.value.userName === TEST_USER.userName && form.value.passWord === TEST_USER.passWord) {
      localStorage.setItem('user', JSON.stringify({ userName: TEST_USER.userName }))
      window.location.href = '/'
      return
    }

    const response = await login(form.value)
    localStorage.setItem('user', JSON.stringify(response.data || response))
    window.location.href = '/'
  } catch (err) {
    error.value = err.message || '登录失败（可使用 test / 123456）'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-form {
  max-width: 420px;
  margin: 0 auto;
  padding: 28px 24px;
}

.tip {
  margin: 4px 0 14px;
  color: var(--text-muted);
  font-size: 13px;
}

.form-item {
  margin-bottom: 15px;
}

label {
  display: block;
  margin-bottom: 8px;
  font-weight: 600;
  color: var(--text-muted);
}

input {
  width: 100%;
  padding: 11px 12px;
  border: 1px solid var(--line);
  border-radius: 10px;
  outline: none;
  transition: border-color 160ms ease, box-shadow 160ms ease;
}

input:focus {
  border-color: var(--brand);
  box-shadow: 0 0 0 3px rgba(15, 118, 110, 0.15);
}

button {
  width: 100%;
  margin-top: 6px;
}

.error {
  color: #b42318;
  margin-top: 12px;
  padding: 8px 10px;
  border-radius: 10px;
  background: #fee4e2;
}
</style>
