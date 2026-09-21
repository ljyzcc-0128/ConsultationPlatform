<script setup>
import { ref, onMounted } from 'vue'
import { adminApi, parseCategory } from '../../api'
import StatusBadge from '../../components/StatusBadge.vue'
import AppModal from '../../components/Modal.vue'
import { useToast } from '../../composables/useToast'

const toast = useToast()

const sources = ref([])
const loading = ref(true)

const editOpen = ref(false)
const editForm = ref({})
const saving = ref(false)

const triggeringId = ref(null)

async function load() {
  loading.value = true
  try {
    sources.value = await adminApi.listSources()
  } catch (e) {
    toast.error(e.message)
  } finally {
    loading.value = false
  }
}
onMounted(load)

function openEdit(s) {
  editForm.value = {
    sourceId: s.sourceId,
    sourceName: s.sourceName || '',
    crawlEnabled: !!s.crawlEnabled,
    crawlFrequency: s.crawlFrequency || '',
    priority: s.priority || '',
    remark: s.remark || ''
  }
  editOpen.value = true
}

async function saveEdit() {
  const { sourceId, ...body } = editForm.value
  saving.value = true
  try {
    await adminApi.updateSource(sourceId, body)
    toast.success(`信源 ${sourceId} 已更新`)
    editOpen.value = false
    load()
  } catch (e) {
    toast.error(e.message)
  } finally {
    saving.value = false
  }
}

async function trigger(s) {
  if (!confirm(`确认触发信源 ${s.sourceId}（${s.sourceName}）抓取 3 条？`)) return
  triggeringId.value = s.sourceId
  try {
    const r = await adminApi.triggerSource(s.sourceId, 3)
    toast.success(`触发成功：${r.crawlerResponse || '已提交'}`)
    load()
  } catch (e) {
    toast.error(`触发失败：${e.message}`)
  } finally {
    triggeringId.value = null
  }
}

function fmtTime(t) { return t ? String(t).replace('T', ' ') : '—' }
</script>

<template>
  <div>
    <div class="page-head">
      <h2>信源管理</h2>
      <span class="sub">共 {{ sources.length }} 个信源 · 启停 / 频率 / 优先级 / 手动触发</span>
    </div>

    <div v-if="loading" class="spinner"></div>

    <div v-else class="table-wrap">
      <table class="table">
        <thead>
          <tr>
            <th>信源</th>
            <th>区域</th>
            <th>类型 / 语言</th>
            <th>分类</th>
            <th>采集</th>
            <th>频率</th>
            <th>优先级</th>
            <th>最近采集</th>
            <th>状态</th>
            <th style="width: 130px">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="s in sources" :key="s.sourceId">
            <td>
              <div class="mono">{{ s.sourceId }}</div>
              <div class="src-name">{{ s.sourceName }}</div>
              <div class="src-url" :title="s.entryUrl">{{ s.entryUrl }}</div>
            </td>
            <td>{{ s.region || '—' }}</td>
            <td>
              <div>{{ s.sourceType || '—' }}</div>
              <div class="muted">{{ s.language }}</div>
            </td>
            <td>
              <div class="cat-cell">
                <span class="tag" v-for="c in parseCategory(s.category)" :key="c">{{ c }}</span>
              </div>
            </td>
            <td>
              <span class="badge" :class="s.crawlEnabled ? 'badge-green' : 'badge-gray'">
                {{ s.crawlEnabled ? '启用' : '停用' }}
              </span>
            </td>
            <td class="mono">{{ s.crawlFrequency || '—' }}</td>
            <td class="mono">{{ s.priority || '—' }}</td>
            <td class="mono sm">{{ fmtTime(s.lastCrawlTime) }}</td>
            <td><StatusBadge :status="s.crawlStatus || 'NotRun'" /></td>
            <td>
              <div class="op-cell">
                <button class="btn-link" @click="openEdit(s)">编辑</button>
                <button class="btn-link" :disabled="triggeringId === s.sourceId" @click="trigger(s)">
                  {{ triggeringId === s.sourceId ? '触发中…' : '触发采集' }}
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 编辑弹窗 -->
    <AppModal v-if="editOpen" :title="`编辑信源 ${editForm.sourceId}`" @close="editOpen = false">
      <div class="form-item">
        <label>信源名称</label>
        <input class="input" v-model="editForm.sourceName" placeholder="展示名称" />
      </div>
      <div class="form-item">
        <label>采集开关</label>
        <label class="switch-line">
          <input type="checkbox" v-model="editForm.crawlEnabled" />
          <span>{{ editForm.crawlEnabled ? '启用（参与定时调度）' : '停用（不调度）' }}</span>
        </label>
      </div>
      <div class="form-item">
        <label>采集频率</label>
        <input class="input" v-model="editForm.crawlFrequency" placeholder="如 1/day、12/hour" />
        <span class="hint">格式：次数/单位（day | hour），如 1/day 表示每天一次</span>
      </div>
      <div class="form-item">
        <label>优先级</label>
        <input class="input" v-model="editForm.priority" placeholder="如 P0、P1" />
      </div>
      <div class="form-item">
        <label>备注</label>
        <textarea class="textarea" v-model="editForm.remark" placeholder="选填"></textarea>
      </div>
      <template #foot>
        <button class="btn btn-ghost" @click="editOpen = false">取消</button>
        <button class="btn" :disabled="saving" @click="saveEdit">{{ saving ? '保存中…' : '保存' }}</button>
      </template>
    </AppModal>
  </div>
</template>

<style scoped>
.src-name { font-weight: 600; margin-top: 2px; }
.src-url {
  max-width: 240px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--text-3);
  font-size: 12px;
}
.cat-cell { display: flex; flex-wrap: wrap; gap: 4px; max-width: 200px; }
.mono.sm { font-size: 12px; }
.op-cell { display: flex; gap: 2px; }
.switch-line { display: flex; align-items: center; gap: 8px; font-size: 13.5px; color: var(--text); cursor: pointer; }
.switch-line input { width: 16px; height: 16px; accent-color: var(--primary); }
</style>
