<!-- src/App.vue -->
<template>
  <!-- 这个标签是路由的出口，没有它，任何路由页面都装载不进来！ -->
  <router-view />
  <div class="toast-stack">
    <div
      v-for="toast in toasts"
      :key="toast.id"
      class="toast-item"
      :class="`toast-${toast.type}`"
    >
      {{ toast.message }}
    </div>
  </div>
</template>

<script>
export default {
  name: 'App',
  data() {
    return {
      toasts: []
    }
  },
  mounted() {
    this.toastHandler = (event) => {
      const detail = event?.detail || {}
      const message = detail.message || ''
      const type = detail.type || 'info'
      if (!message) return
      const id = `${Date.now()}-${Math.random()}`
      this.toasts.push({ id, message, type })
      window.setTimeout(() => {
        this.toasts = this.toasts.filter(item => item.id !== id)
      }, 2800)
    }
    window.addEventListener('app-toast', this.toastHandler)
  },
  beforeUnmount() {
    if (this.toastHandler) {
      window.removeEventListener('app-toast', this.toastHandler)
    }
  }
}
</script>

<style>
body {
  margin: 0;
  padding: 0;
}

:root {
  --ui-loading-bg: #eef4ff;
  --ui-loading-fg: #2f4f7f;
  --ui-empty-bg: #f4f8fb;
  --ui-empty-fg: #6f8392;
  --ui-error-bg: #fef2f2;
  --ui-error-fg: #b42318;
  --ui-error-border: #f7caca;
}

.ui-state {
  border-radius: 12px;
  padding: 12px 14px;
  font-size: 13px;
  text-align: center;
}

.ui-state-loading {
  background: var(--ui-loading-bg);
  color: var(--ui-loading-fg);
}

.ui-state-empty {
  background: var(--ui-empty-bg);
  color: var(--ui-empty-fg);
}

.ui-state-error {
  background: var(--ui-error-bg);
  color: var(--ui-error-fg);
  border: 1px solid var(--ui-error-border);
}

.toast-stack {
  position: fixed;
  top: 16px;
  right: 16px;
  z-index: 9999;
  display: grid;
  gap: 8px;
  max-width: min(360px, 92vw);
}

.toast-item {
  border-radius: 10px;
  padding: 10px 12px;
  font-size: 13px;
  box-shadow: 0 10px 20px rgba(29, 41, 57, 0.18);
}

.toast-info {
  background: #eef4ff;
  color: #1e3a8a;
}

.toast-success {
  background: #edf7ed;
  color: #166534;
}

.toast-error {
  background: #fef2f2;
  color: #b42318;
}
</style>