<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { adminApi } from '../../api'
import StatusBadge from '../../components/StatusBadge.vue'
import { useToast } from '../../composables/useToast'

const toast = useToast()

const status = ref(null)
const loading = ref(true)
let timer = null

async function load() {
  try {
    status.value = await adminApi.schedulerStatus()
  } catch (e) {
    toast.error(e.message)
  } finally {
    loading.value = false
  }
}
onMounted(() => {
  load()
  timer = setInterval(load, 15000) // 15s 自动刷新
})
onBeforeUnmount(() => clearInterval(timer))

function fmtTime(t) { return t ? String(t).replace('T', ' ') : '—' }
</script>

<template>
  <div>
    <div class="page-head">
      <h2>调度状态</h2>
      <span class="sub">XXL-JOB 定时采集调度视角 · 每 15 秒自动刷新</span>
    </div>

    <div v-if="loading" class="spinner"></div>

    <template v-else-if="status">
      <div class="stat-cards">
        <div class="stat card">
          <div class="stat-label">单次触发上限</div>
          <div class="stat-value">{{ status.triggerLimit }} <span class="stat-unit">条/信源</span></div>
        </div>
        <div class="stat card" :class="{ warn: status.dueCount > 0 }">
          <div class="stat-label">当前到期信源</div>
          <div class="stat-value">{{ status.dueCount }} <span class="stat-unit">个</span></div>
        </div>
      </div>

      <div class="table-wrap">
        <table class="table">
          <thead>
            <tr>
              <th>信源</th>
              <th>调度开关</th>
              <th>频率</th>
              <th>间隔</th>
              <th>最近采集</th>
              <th>采集状态</th>
              <th>到期</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="s in status.sources" :key="s.sourceId">
              <td class="mono">{{ s.sourceId }}</td>
              <td>
                <span class="badge" :class="s.crawlEnabled ? 'badge-green' : 'badge-gray'">
                  {{ s.crawlEnabled ? '参与调度' : '停用' }}
                </span>
              </td>
              <td>
                <span class="mono">{{ s.crawlFrequency || '—' }}</span>
                <span v-if="!s.frequencyValid" class="badge badge-red" style="margin-left: 6px">格式无效</span>
              </td>
              <td class="mono">{{ s.intervalHours }}h</td>
              <td class="mono sm">{{ fmtTime(s.lastCrawlTime) }}</td>
              <td><StatusBadge :status="s.crawlStatus || 'NotRun'" /></td>
              <td>
                <span class="badge" :class="s.due ? 'badge-amber' : 'badge-gray'">
                  {{ s.due ? '已到期' : '未到期' }}
                </span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <p class="foot-note muted">
        调度由 XXL-JOB 调度中心统一触发（任务 crawlSourcesJob），此处为只读监控视图。
      </p>
    </template>
  </div>
</template>

<style scoped>
.stat-cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 14px; margin-bottom: 18px; }
.stat { padding: 16px 20px; }
.stat.warn { border-color: #f0c98a; background: #fffdf7; }
.stat-label { font-size: 12.5px; color: var(--text-3); margin-bottom: 6px; }
.stat-value { font-size: 26px; font-weight: 700; line-height: 1.2; }
.stat.warn .stat-value { color: var(--warning); }
.stat-unit { font-size: 12.5px; color: var(--text-3); font-weight: 400; }
.mono.sm { font-size: 12px; }
.foot-note { margin-top: 14px; font-size: 12.5px; }
</style>
