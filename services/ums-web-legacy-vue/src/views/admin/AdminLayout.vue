<script setup>
import { ref, computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import { adminApi } from '../../api'
import { useToast } from '../../composables/useToast'

const route = useRoute()
const toast = useToast()

const NAV = [
  { path: '/admin/reviews', label: '审核队列', icon: 'M4 6h16M4 12h16M4 18h10' },
  { path: '/admin/sources', label: '信源管理', icon: 'M4 6h16M4 6v12M8 6v12M4 6l4-2 12 2-4 2L4 6z' },
  { path: '/admin/dlq', label: '死信队列', icon: 'M12 8v5m0 3h.01M10.3 3.9 2.8 17a2 2 0 0 0 1.7 3h15a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z' },
  { path: '/admin/scheduler', label: '调度状态', icon: 'M12 6v6l4 2m6-2a10 10 0 1 1-20 0 10 10 0 0 1 20 0z' },
  { path: '/admin/audit', label: '审计日志', icon: 'M6 3h12v18l-6-3-6 3V3z' }
]

const dlqCount = ref(null)

async function refreshDlqBadge() {
  try {
    const queues = await adminApi.dlqQueues()
    dlqCount.value = queues.reduce((s, q) => s + (q.messages || 0), 0)
  } catch { dlqCount.value = null }
}

// 路由切换时刷新 DLQ 徽标（审核等操作会间接影响队列）
watch(() => route.fullPath, refreshDlqBadge, { immediate: true })

const activeNav = computed(() => route.path)
</script>

<template>
  <div class="admin-shell">
    <aside class="sidebar">
      <div class="s-brand">
        <span class="s-mark">ESS</span>
        <div class="s-text">
          <strong>储能资讯平台</strong>
          <span>管理后台</span>
        </div>
      </div>

      <nav class="s-nav">
        <router-link
          v-for="n in NAV"
          :key="n.path"
          :to="n.path"
          class="s-item"
          :class="{ active: activeNav === n.path }"
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
            <path :d="n.icon" />
          </svg>
          <span>{{ n.label }}</span>
          <em v-if="n.path === '/admin/dlq' && dlqCount > 0" class="s-badge">{{ dlqCount }}</em>
        </router-link>
      </nav>

      <div class="s-foot">
        <router-link to="/" class="s-item site-link">&larr; 返回用户端</router-link>
      </div>
    </aside>

    <main class="admin-main">
      <router-view />
    </main>
  </div>
</template>

<style scoped>
.admin-shell { display: flex; min-height: 100vh; }

.sidebar {
  width: 218px;
  flex-shrink: 0;
  background: var(--sidebar-bg);
  color: var(--sidebar-text);
  display: flex;
  flex-direction: column;
  position: sticky;
  top: 0;
  height: 100vh;
}
.s-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 20px 18px 18px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.07);
}
.s-mark {
  width: 36px; height: 36px;
  border-radius: 9px;
  background: linear-gradient(135deg, #1d5bd6, #0ea5a4);
  color: #fff;
  font-size: 11.5px;
  font-weight: 700;
  display: flex; align-items: center; justify-content: center;
}
.s-text { display: flex; flex-direction: column; line-height: 1.35; }
.s-text strong { color: #fff; font-size: 13.5px; }
.s-text span { font-size: 11px; color: #64748b; }

.s-nav { flex: 1; padding: 12px 10px; display: flex; flex-direction: column; gap: 3px; }
.s-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 12px;
  border-radius: 8px;
  color: var(--sidebar-text);
  font-size: 13.5px;
  transition: all 0.15s;
}
.s-item svg { width: 17px; height: 17px; flex-shrink: 0; opacity: 0.85; }
.s-item:hover { color: #e2e8f0; background: rgba(255, 255, 255, 0.05); }
.s-item.active { color: #fff; background: var(--primary); }
.s-badge {
  margin-left: auto;
  background: var(--danger);
  color: #fff;
  font-size: 11px;
  font-style: normal;
  font-weight: 600;
  border-radius: 99px;
  min-width: 18px;
  height: 18px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0 5px;
}
.s-foot { padding: 12px 10px 16px; border-top: 1px solid rgba(255, 255, 255, 0.07); }
.site-link { font-size: 12.5px; color: #64748b; }

.admin-main { flex: 1; padding: 26px 30px 60px; min-width: 0; max-width: 1280px; }

@media (max-width: 768px) {
  .admin-shell { flex-direction: column; }
  .sidebar { width: 100%; height: auto; position: static; }
  .s-nav { flex-direction: row; overflow-x: auto; }
  .s-foot { display: none; }
  .admin-main { padding: 18px 16px 40px; }
}
</style>
