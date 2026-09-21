<script setup>
import { ref, onMounted } from 'vue'
import { adminApi } from '../../api'
import AppModal from '../../components/Modal.vue'
import { useToast } from '../../composables/useToast'

const toast = useToast()

const queues = ref([])
const loading = ref(true)
const busyQueue = ref(null)

const previewOpen = ref(false)
const previewQueue = ref('')
const previewMsgs = ref([])
const previewLoading = ref(false)

const QUEUE_DESC = {
  'ums.raw.item.fetched.dlq': '入库消费失败（采集消息）',
  'ums.content.parsed.dlq': 'AI 加工失败（摘要/分类）',
  'ums.content.processed.dlq': '去重聚合失败'
}

async function load() {
  loading.value = true
  try {
    queues.value = await adminApi.dlqQueues()
  } catch (e) {
    toast.error(e.message)
  } finally {
    loading.value = false
  }
}
onMounted(load)

async function openPreview(queue) {
  previewQueue.value = queue
  previewOpen.value = true
  previewLoading.value = true
  try {
    previewMsgs.value = await adminApi.dlqMessages(queue, 10)
  } catch (e) {
    toast.error(e.message)
  } finally {
    previewLoading.value = false
  }
}

async function requeue(queue) {
  if (!confirm(`确认将队列 ${queue} 的全部死信重放回原队列？`)) return
  busyQueue.value = queue
  try {
    const r = await adminApi.dlqRequeue(queue)
    toast.success(`已重放 ${r.requeued} 条`)
    load()
  } catch (e) {
    toast.error(e.message)
  } finally {
    busyQueue.value = null
  }
}

async function purge(queue) {
  if (!confirm(`确认清空队列 ${queue} 的全部死信？此操作不可恢复！`)) return
  busyQueue.value = queue
  try {
    const r = await adminApi.dlqPurge(queue)
    toast.success(`已清空 ${r.purged} 条`)
    load()
  } catch (e) {
    toast.error(e.message)
  } finally {
    busyQueue.value = null
  }
}

function pretty(payload) {
  try { return JSON.stringify(JSON.parse(payload), null, 2) } catch { return payload }
}
</script>

<template>
  <div>
    <div class="page-head">
      <h2>死信队列</h2>
      <span class="sub">消费重试 3 次仍失败的消息进入 DLQ · 支持重放与清空</span>
    </div>

    <div v-if="loading" class="spinner"></div>

    <div v-else class="table-wrap">
      <table class="table">
        <thead>
          <tr>
            <th>队列</th>
            <th>说明</th>
            <th>积压消息</th>
            <th style="width: 220px">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="q in queues" :key="q.queue">
            <td class="mono">{{ q.queue }}</td>
            <td class="muted">{{ QUEUE_DESC[q.queue] || '业务死信队列' }}</td>
            <td>
              <span class="badge" :class="q.messages > 0 ? 'badge-red' : 'badge-gray'">{{ q.messages }}</span>
            </td>
            <td>
              <div class="op-cell">
                <button class="btn-link" @click="openPreview(q.queue)">预览</button>
                <button class="btn-link" :disabled="busyQueue === q.queue || !q.messages" @click="requeue(q.queue)">重放</button>
                <button class="btn-link danger" :disabled="busyQueue === q.queue || !q.messages" @click="purge(q.queue)">清空</button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 消息预览 -->
    <AppModal v-if="previewOpen" :title="`死信预览 · ${previewQueue}`" wide @close="previewOpen = false">
      <div v-if="previewLoading" class="spinner"></div>
      <div v-else-if="!previewMsgs.length" class="empty">队列为空</div>
      <div v-else class="msg-list">
        <div v-for="(m, i) in previewMsgs" :key="i" class="msg-item">
          <div class="msg-meta">
            <span class="tag">routingKey: {{ m.routingKey }}</span>
            <span class="badge" :class="m.redelivered ? 'badge-amber' : 'badge-gray'">
              {{ m.redelivered ? '曾重投' : '首次死信' }}
            </span>
          </div>
          <pre class="msg-payload">{{ pretty(m.payload) }}</pre>
        </div>
      </div>
      <template #foot>
        <button class="btn btn-ghost" @click="previewOpen = false">关闭</button>
      </template>
    </AppModal>
  </div>
</template>

<style scoped>
.op-cell { display: flex; gap: 2px; }
.btn-link.danger { color: var(--danger); }
.msg-list { display: flex; flex-direction: column; gap: 14px; }
.msg-item { border: 1px solid var(--border); border-radius: 8px; padding: 12px 14px; }
.msg-meta { display: flex; gap: 8px; margin-bottom: 8px; align-items: center; }
.msg-payload {
  background: #0f1a2e;
  color: #c9d6ee;
  border-radius: 7px;
  padding: 12px 14px;
  font-size: 12px;
  line-height: 1.6;
  overflow-x: auto;
  max-height: 260px;
  white-space: pre-wrap;
  word-break: break-all;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}
</style>
