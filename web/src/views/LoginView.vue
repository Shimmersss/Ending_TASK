<template>
  <div class="login-view">
    <section class="login-shell fade-rise glass">
      <aside class="brand-area">
        <p class="tag">AI WORKSTATION</p>
        <h1>欢迎回来</h1>
        <p class="desc">登录后可使用图像处理与大模型对话模块。</p>
      </aside>
      <div class="form-area">
        <LoginForm />
      </div>
    </section>
  </div>
</template>

<script setup>
import LoginForm from '../components/LoginForm.vue'
</script>

<style scoped>
.login-view {
  min-height: calc(100vh - 48px);
  display: grid;
  place-items: center;
}

.login-shell {
  width: min(960px, 100%);
  display: grid;
  grid-template-columns: 1.15fr 1fr;
  gap: 16px;
  padding: 22px;
}

.brand-area {
  border-radius: 16px;
  padding: 30px;
  background: linear-gradient(145deg, #0f766e, #194f8f);
  color: #eef8ff;
  display: flex;
  flex-direction: column;
  justify-content: center;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.16);
}

.tag {
  font-size: 12px;
  letter-spacing: 0.2em;
  margin-bottom: 10px;
  opacity: 0.86;
}

.brand-area h1 {
  font-size: 42px;
  line-height: 1.1;
}

.desc {
  margin-top: 12px;
  opacity: 0.94;
  max-width: 30ch;
}

.form-area {
  padding: 6px;
}

@media (max-width: 900px) {
  .login-shell {
    grid-template-columns: 1fr;
    padding: 14px;
  }

  .brand-area h1 {
    font-size: 34px;
  }
}
</style>
