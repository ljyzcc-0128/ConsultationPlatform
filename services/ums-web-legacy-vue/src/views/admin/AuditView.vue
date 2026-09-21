<script setup>
import { ref, onMounted } from 'vue'
import { adminApi } from '../../api'
import { useToast } from '../../composables/useToast'

const toast = useToast()

const PAGE_SIZE = 20
const ACTIONS = [
  'SOURCE_UPDATE', 'SOURCE_TRIGGER',
  'REVIEW_APPROVE', 'REVIEW_REJECT', 'REVIEW_CATEGORY_FIX',
  'DLQ_REQUEUE', 'DLQ_PURGE'
]
const ACTION_LABELS = {
  SOURCE_UPDATE: '信源更新', SOURCE_TRIGGER: '手动触发采集',
  REVIEW_APPROVE: '审核通过', REVIEW_REJECT: '审核驳回', REVIEW_CATEGORY_FIX: '修正分类',
  DLQ_REQUEUE: '死信重放', DLQ_PURGE: '死信清空'
}

const action = ref('')
const objectType = ref('')
const logs = ref([])
const offset = ref(0)
const loading = ref(true)
const pageEnd = ref(false)
const expanded = ref(new Set())

async function load() {
  loading.value = true
  try {
    const rows = await adminApi.auditLogs({
      action: action.value, objectType: objectType.value,
      limit: PAGE_SIZE, offset: offset.value
    })
    logs.value = rows
    pageEnd.value = rows.length < PAGE_SIZE
  } catch (e) {
    toast.error(e.message)
  } finally {
    loading.value = false
  }
}
onMounted(load)

function onFilter() { offset.value = 0; load() }
function prev() { offset.value = Math.max(0, offset.value - PAGE_SIZE); load() }
function next() { offset.value += PAGE_SIZE; load() }
function toggle(id) {
  expanded.value.has(id) ? expanded.value.delete(id) : expanded.value.add(id)
}
function pretty(detail) {
  try { return JSON.stringify(JSON.parse(detail), null, 2) } catch { return detail }
}
function fmtTime(t) { return t ? String(t).replace('T', ' ') : '—' }
</script>

<template>
  <div>
    <div class="page-head">
      <h2>审计日志</h2>
      <span class="sub">管理操作自动留痕（谁 · 何时 · 对什么做了什么）</span>
    </div>

    <div class="filters">
      <select class="select" v-model="action" @change="onFilter">
        <option value="">全部操作</option>
        <option v-for="a in ACTIONS" :key="a" :value="a">{{ ACTION_LABELS[a] || a }}</option>
      </select>
      <select class="select" v-model="objectType" @change="onFilter">
        <option value="">全部对象</option>
        <option value="cp_source">cp_source</option>
        <option value="cp_news">cp_news</option>
        <option value="dlq">dlq</option>
      </select>
    </div>

    <div v-if="loading" class="spinner"></div>

    <div v-else-if="!logs.length" class="empty card">
      <div class="empty-icon">◎</div>
      暂无审计记录
    </div>

    <template v-else>
      <div class="table-wrap">
        <table class="table">
          <thead>
            <tr>
              <th style="width: 64px">ID</th>
              <th>操作人</th>
              <th>操作</th>
              <th>对象</th>
              <th>详情</th>
              <th style="width: 150px">时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="log in logs" :key="log.logId">
              <td class="mono">{{ log.logId }}</td>
              <td>{{ log.operator }}</td>
              <td>
                <span class="badge badge-blue">{{ ACTION_LABELS[log.action] || log.action }}</span>
              </td>
              <td>
                <div class="mono">{{ log.objectType }}</div>
                <div class="muted mono obj-id" :title="log.objectId">{{ log.objectId }}</div>
              </td>
              <td class="detail-cell">
                <template v-if="log.detail">
                  <pre v-if="expanded.has(log.logId)" class="detail-full">{{ pretty(log.detail) }}</pre>
                  <button v-else class="btn-link" @click="toggle(log.logId)">展开 JSON</button>
                </template>
                <span v-else class="muted">—</span>
              </td>
              <td class="mono sm">{{ fmtTime(log.createdAt) }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="pager">
        <button class="btn btn-ghost btn-sm" :disabled="offset === 0 || loading" @click="prev">上一页</button>
        <span class="page-info">第 {{ offset / PAGE_SIZE + 1 }} 页</span>
        <button class="btn btn-ghost btn-sm" :disabled="pageEnd || loading" @click="next">下一页</button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.filters { display: flex; gap: 10px; margin-bottom: 16px; flex-wrap: wrap; }
.filters .select { min-width: 160px; }
.obj-id { max-width: 130px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.detail-cell { max-width: 340px; }
.detail-full {
  background: #f6f8fb;
  border: 1px solid var(--border);
  border-radius: 7px;
  padding: 10px 12px;
  font-size: 12px;
  line-height: 1.55;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 240px;
  overflow-y: auto;
}
.mono.sm { font-size: 12px; }
</style>
