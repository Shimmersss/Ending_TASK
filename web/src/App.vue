<template>
  <div id="app">
    <router-view />
  </div>
</template>

<script setup>
</script>

<style>
:root {
  --bg: #f3f6fa;
  --surface: #ffffff;
  --surface-strong: #ffffff;
  --text-main: #172033;
  --text-muted: #667085;
  --line: #d9e2ec;
  --brand: #0f766e;
  --brand-2: #2563eb;
  --danger: #dc2626;
  --shadow: 0 16px 38px rgba(23, 32, 51, 0.08);
}

* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: "Microsoft YaHei", "PingFang SC", "Noto Sans SC", Arial, sans-serif;
  line-height: 1.6;
  color: var(--text-main);
  background: var(--bg);
}

#app {
  position: relative;
  max-width: 1280px;
  min-height: 100vh;
  margin: 0 auto;
  padding: 24px;
}

h1,
h2,
h3,
h4 {
  letter-spacing: 0;
}

.glass {
  border: 1px solid var(--line);
  background: var(--surface);
  box-shadow: var(--shadow);
  border-radius: 8px;
}

.btn {
  border: none;
  border-radius: 8px;
  padding: 10px 18px;
  font-weight: 700;
  cursor: pointer;
  transition: transform 180ms ease, box-shadow 180ms ease, opacity 180ms ease;
}

.btn:hover {
  transform: translateY(-1px);
}

.btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.btn-primary {
  color: #fff;
  background: var(--brand);
  box-shadow: 0 10px 20px rgba(15, 118, 110, 0.22);
}

.btn-danger {
  color: #fff;
  background: var(--danger);
}

.panel-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 14px;
}

.fade-rise {
  animation: fadeRise 420ms ease both;
}

@keyframes fadeRise {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (max-width: 768px) {
  #app {
    padding: 14px;
  }
}
</style>
