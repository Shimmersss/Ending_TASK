<template>
  <div class="home-view fade-rise">
    <section class="hero">
      <div>
        <p class="eyebrow">MULTI DETECTION CENTER</p>
        <h1>多模态目标检测工作台</h1>
        <p class="sub">上传图片或 KITTI 点云，切换 YOLO / MMDet3D 实时推理并查看检测结果。</p>
      </div>
      <div class="user-info" v-if="user">
        <span>当前用户</span>
        <strong>{{ user.userName }}</strong>
        <button class="btn btn-danger" @click="logout">退出登录</button>
      </div>
    </section>

    <div class="workspace-grid">
      <section class="panel detection-panel">
        <div class="panel-title">
          <div>
            <h3>检测推理</h3>
            <p>对接 YOLO (9000) 与 MMDet3D (8000)</p>
          </div>
          <span class="chip">YOLO / MMDet3D</span>
        </div>
        <ImageUpload />
      </section>

      <section class="panel chat-panel">
        <div class="panel-title">
          <div>
            <h3>模型对话</h3>
            <p>用于辅助分析检测结果和业务说明</p>
          </div>
          <span class="chip muted">Chat</span>
        </div>
        <ModelChat />
      </section>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
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
  display: grid;
  gap: 18px;
}

.hero {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 16px;
  padding: 28px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background:
    linear-gradient(120deg, rgba(15, 118, 110, 0.12), transparent 45%),
    #ffffff;
  box-shadow: var(--shadow);
}

.eyebrow {
  color: var(--brand);
  font-size: 12px;
  letter-spacing: 0.16em;
  font-weight: 800;
}

.hero h1 {
  margin-top: 6px;
  font-size: clamp(30px, 4vw, 46px);
  line-height: 1.15;
}

.sub {
  color: var(--text-muted);
  margin-top: 10px;
  max-width: 650px;
}

.workspace-grid {
  display: grid;
  grid-template-columns: minmax(420px, 0.95fr) minmax(420px, 1.05fr);
  gap: 18px;
  align-items: start;
}

.panel {
  padding: 18px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--surface-strong);
  box-shadow: var(--shadow);
}

.panel-title p {
  color: var(--text-muted);
  font-size: 13px;
  margin-top: 2px;
}

.chip {
  font-size: 12px;
  font-weight: 800;
  color: var(--brand);
  background: #e7f5f1;
  border: 1px solid #b8ded6;
  border-radius: 8px;
  padding: 5px 9px;
}

.chip.muted {
  color: #40566f;
  background: #edf2f7;
  border-color: #d6e0ea;
}

.user-info {
  min-width: 180px;
  padding: 14px;
  border-radius: 8px;
  border: 1px solid var(--line);
  background: #f8fafc;
  display: grid;
  gap: 8px;
}

.user-info span {
  color: var(--text-muted);
  font-size: 13px;
}

@media (max-width: 980px) {
  .workspace-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .hero {
    flex-direction: column;
    align-items: stretch;
    padding: 20px;
  }
}
</style>
