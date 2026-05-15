<template>
  <div id="app" class="app-shell">
    <router-view />
  </div>
</template>

<script setup>
</script>

<style scoped>
.app-shell {
  min-height: 100vh;
  padding: 24px;
}

@media (max-width: 768px) {
  .app-shell {
    padding: 14px;
  }
}
</style>
