<template>
  <div class="login-view">
    <section class="login-shell glass-panel fade-rise">
      <aside class="login-copy">
        <p class="eyebrow">DJI AIRWORKS / CONTROL</p>
        <h1>无人机巡检中台</h1>
        <p class="lead">
          登录后进入图像检测、点云分析和 Dify 决策面板，整套流程保持同一套视觉模板。
        </p>

        <div class="login-tags">
          <span>巡检核查</span>
          <span>目标识别</span>
          <span>安全策略</span>
        </div>

        <div class="frame login-frame">
          <img src="/generated/dji-dashboard-wide.png" alt="无人机巡检登录页视觉图">
        </div>
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
  position: relative;
  z-index: 1;
  min-height: calc(100vh - 48px);
  display: grid;
  place-items: center;
}

.login-shell {
  width: min(1040px, 100%);
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(320px, 0.9fr);
  gap: 18px;
  padding: 18px;
  border-radius: var(--radius-xl);
}

.login-copy {
  display: grid;
  gap: 12px;
  padding: 24px;
  border-radius: var(--radius-xl);
  border: 1px solid var(--line);
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.07), rgba(255, 255, 255, 0.03)),
    linear-gradient(145deg, rgba(9, 20, 34, 0.96), rgba(13, 30, 49, 0.92));
}

.login-copy h1 {
  font-size: 30px;
  line-height: 1.15;
}

.login-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.login-tags span {
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.14);
  padding: 6px 11px;
  background: rgba(255, 255, 255, 0.06);
  color: var(--text-main);
  font-size: 12px;
  font-weight: 700;
}

.login-frame {
  aspect-ratio: 16 / 9;
  margin-top: 10px;
}

.form-area {
  display: grid;
  align-items: center;
  padding: 4px;
}

@media (max-width: 920px) {
  .login-shell {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .login-shell,
  .login-copy {
    padding: 14px;
  }

  .login-copy h1 {
    font-size: 24px;
  }
}
</style>
