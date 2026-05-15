<template>
  <div class="home-view fade-rise">
    <div class="background-canvas" aria-hidden="true">
      <img class="bg-layer bg-layer-main" src="/generated/dji-home-background.png" alt="">
      <div class="bg-scrim"></div>
    </div>

    <header class="topbar glass-panel">
      <div class="topbar-copy">
        <p class="eyebrow">DJI / YOLO / MMDet3D</p>
        <h1>无人机巡检检测控制台</h1>
        <p class="lead">图像检测、点云推理和 Dify 决策放在同一套工作流里。</p>
      </div>

      <div class="topbar-meta">
        <div v-if="user" class="user-chip">
          <span>当前用户</span>
          <strong>{{ user.userName }}</strong>
        </div>
        <button v-if="user" class="btn btn-danger" type="button" @click="logout">退出登录</button>
      </div>
    </header>

    <section class="workspace-grid">
      <section class="panel glass-panel">
        <div class="panel-title">
          <div>
            <h3>检测输入</h3>
            <p>上传图像或点云，选择模型后直接推理。</p>
          </div>
          <span class="chip">输入</span>
        </div>
        <ImageUpload />
      </section>

      <section class="panel glass-panel">
        <div class="panel-title">
          <div>
            <h3>模型对话</h3>
            <p>结果摘要、验证状态和原始输出放在一起，方便继续追问。</p>
          </div>
          <span class="chip muted">Dify</span>
        </div>
        <ModelChat />
      </section>
    </section>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import ImageUpload from '../components/ImageUpload.vue'
import ModelChat from '../components/ModelChat.vue'

const user = ref(null)

onMounted(() => {
  const userStr = localStorage.getItem('user')
  if (userStr) {
    user.value = JSON.parse(userStr)
  }
})

const logout = () => {
  localStorage.removeItem('user')
  window.location.href = '/login'
}
</script>

<style scoped>
.home-view {
  position: relative;
  z-index: 1;
  display: grid;
  gap: 18px;
  isolation: isolate;
}

.background-canvas {
  position: fixed;
  inset: 0;
  pointer-events: none;
  overflow: hidden;
  z-index: 0;
  background:
    radial-gradient(circle at 18% 18%, rgba(66, 233, 208, 0.14), transparent 26%),
    radial-gradient(circle at 82% 16%, rgba(109, 178, 255, 0.12), transparent 24%),
    linear-gradient(180deg, rgba(4, 9, 16, 0.18), rgba(4, 9, 16, 0.52));
}

.bg-layer-main {
  position: absolute;
  left: 50%;
  top: 50%;
  width: min(132vw, 1820px);
  max-width: none;
  aspect-ratio: 16 / 9;
  transform: translate(-50%, -44%) scale(1.04);
  object-fit: cover;
  object-position: center center;
  opacity: 0.72;
  filter: saturate(1.08) contrast(1.04);
}

.bg-scrim {
  position: absolute;
  inset: 0;
  background:
    linear-gradient(180deg, rgba(4, 9, 16, 0.28), rgba(7, 16, 27, 0.42)),
    radial-gradient(circle at 20% 20%, rgba(66, 233, 208, 0.08), transparent 28%),
    radial-gradient(circle at 85% 18%, rgba(109, 178, 255, 0.07), transparent 24%),
    radial-gradient(circle at 55% 82%, rgba(66, 233, 208, 0.05), transparent 30%);
}

.topbar,
.panel {
  border-radius: var(--radius-xl);
}

.topbar {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 18px;
  align-items: start;
  padding: 20px;
}

.topbar-copy {
  display: grid;
  gap: 10px;
}

.topbar-copy h1 {
  font-size: 28px;
  line-height: 1.15;
}

.topbar-meta {
  display: grid;
  justify-items: end;
  gap: 12px;
}

.user-chip {
  display: grid;
  gap: 2px;
  justify-items: end;
  padding: 12px 14px;
  border: 1px solid var(--line);
  border-radius: var(--radius-lg);
  background: rgba(255, 255, 255, 0.06);
}

.user-chip span {
  color: var(--text-muted);
  font-size: 12px;
}

.user-chip strong {
  font-size: 16px;
}

.workspace-grid {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 18px;
  align-items: stretch;
}

.panel {
  display: flex;
  flex-direction: column;
  padding: 20px;
  min-height: 100%;
}

@media (max-width: 1080px) {
  .topbar,
  .workspace-grid {
    grid-template-columns: 1fr;
  }

  .topbar-meta {
    justify-items: start;
  }
}

@media (max-width: 760px) {
  .topbar,
  .panel {
    padding: 18px;
  }

  .topbar-copy h1 {
    font-size: 24px;
  }

  .workspace-grid {
    gap: 14px;
  }

  .bg-layer-main {
    width: 176vw;
    top: 56%;
  }
}
</style>
