<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { adminApi, parseCategory, CATEGORY_DICTIONARY, STATUS_LABELS } from '../../api'
import StatusBadge from '../../components/StatusBadge.vue'
import AppModal from '../../components/Modal.vue'
import { useToast } from '../../composables/useToast'

const toast = useToast()
const router = useRouter()

const PAGE_SIZE = 10
const TABS = ['', 'Pending', 'NeedsReview', 'Approved', 'Rejected']

const status = ref('')
const items = ref([])
const offset = ref(0)
const loading = ref(true)
const pageEnd = ref(false)

const actingId = ref(null)

// 操作弹窗：approve（备注选填）/ reject（原因必填）
const actionOpen = ref(false)
const actionMode = ref('approve')
const actionText = ref('')
const actionSaving = ref(false)
const actionItem = ref(null)

// 分类修正弹窗
const catOpen = ref(false)
const catItem = ref(null)
const catSelected = ref([])
const catSaving = ref(false)

async function load() {
  loading.value = true
  try {
    const rows = await adminApi.listReviews({
      status: status.value, limit: PAGE_SIZE, offset: offset.value
    })
    items.value = rows
    pageEnd.value = rows.length < PAGE_SIZE
  } catch (e) {
    toast.error(e.message)
  } finally {
    loading.value = false
  }
}
onMounted(load)

function switchTab(s) {
  status.value = s
  offset.value = 0
  load()
}
function prev() { offset.value = Math.max(0, offset.value - PAGE_SIZE); load() }
function next() { offset.value += PAGE_SIZE; load() }

function openAction(item, mode) {
  actionItem.value = item
  actionMode.value = mode
  actionText.value = ''
  actionOpen.value = true
}

async function submitAction() {
  const item = actionItem.value
  if (actionMode.value === 'reject' && !actionText.value.trim()) {
    toast.error('驳回原因必填')
    return
  }
  actionSaving.value = true
  actingId.value = item.articleId
  try {
    if (actionMode.value === 'approve') {
      await adminApi.approveReview(item.articleId, actionText.value.trim())
      toast.success(`已通过：${item.title.slice(0, 24)}…`)
    } else {
      await adminApi.rejectReview(item.articleId, actionText.value.trim())
      toast.success(`已驳回：${item.title.slice(0, 24)}…`)
    }
    actionOpen.value = false
    load()
  } catch (e) {
    toast.error(e.message)
  } finally {
    actionSaving.value = false
    actingId.value = null
  }
}

function openCategory(item) {
  catItem.value = item
  catSelected.value = parseCategory(item.category)
  catOpen.value = true
}

async function saveCategory() {
  catSaving.value = true
  try {
    await adminApi.updateCategory(catItem.value.articleId, catSelected.value)
    toast.success('分类已修正')
    catOpen.value = false
    load()
  } catch (e) {
    toast.error(e.message)
  } finally {
    catSaving.value = false
  }
}

function fmtTime(t) { return t ? String(t).replace('T', ' ') : '—' }
const canReview = (it) => it.reviewStatus === 'Pending' || it.reviewStatus === 'NeedsReview'
</script>

<template>
  <div>
    <div class="page-head">
      <h2>审核队列</h2>
      <span class="sub">AI 加工完成后进入人工审核（Pending → Approved / Rejected）</span>
    </div>

    <div class="tabs">
      <button
        v-for="t in TABS"
        :key="t"
        class="tab"
        :class="{ active: status === t }"
        @click="switchTab(t)"
      >
        {{ t === '' ? '全部' : STATUS_LABELS[t] }}
      </button>
    </div>

    <div v-if="loading" class="spinner"></div>

    <div v-else-if="!items.length" class="empty card">
      <div class="empty-icon">✓</div>
      该状态下暂无待审资讯
    </div>

    <template v-else>
      <div v-for="it in items" :key="it.articleId" class="review-card card">
        <div class="r-top">
          <span class="r-src">{{ it.sourceName || it.sourceId }}</span>
          <span class="r-date">{{ it.publishDate || '无日期' }} · {{ it.language === 'en' ? 'EN' : '中' }}</span>
          <StatusBadge :status="it.reviewStatus" />
        </div>

        <h3 class="r-title" @click="router.push(`/news/${it.articleId}`)">{{ it.title }}</h3>

        <div class="r-cats">
          <span class="tag" v-for="c in parseCategory(it.category)" :key="c">{{ c }}</span>
          <button class="btn-link" @click="openCategory(it)">修正分类</button>
        </div>

        <p class="r-summary">{{ it.summary || '（暂无摘要）' }}</p>

        <div class="r-comment" v-if="it.reviewComment">
          <span class="c-label">{{ it.reviewStatus === 'Rejected' ? '驳回原因' : '审核备注' }}：</span>{{ it.reviewComment }}
        </div>

        <div class="r-foot">
          <span class="r-time">更新于 {{ fmtTime(it.updatedAt) }}</span>
          <div class="r-ops" v-if="canReview(it)">
            <button class="btn btn-ghost" :disabled="actingId === it.articleId" @click="openAction(it, 'reject')">驳回</button>
            <button class="btn btn-success" :disabled="actingId === it.articleId" @click="openAction(it, 'approve')">通过</button>
          </div>
        </div>
      </div>

      <div class="pager">
        <button class="btn btn-ghost btn-sm" :disabled="offset === 0 || loading" @click="prev">上一页</button>
        <span class="page-info">第 {{ offset / PAGE_SIZE + 1 }} 页</span>
        <button class="btn btn-ghost btn-sm" :disabled="pageEnd || loading" @click="next">下一页</button>
      </div>
    </template>

    <!-- 通过 / 驳回弹窗 -->
    <AppModal
      v-if="actionOpen"
      :title="actionMode === 'approve' ? '审核通过' : '驳回资讯'"
      @close="actionOpen = false"
    >
      <p class="a-title">{{ actionItem?.title }}</p>
      <div class="form-item">
        <label :class="{ req: actionMode === 'reject' }">
          {{ actionMode === 'approve' ? '审核备注（选填）' : '驳回原因（必填）' }}
        </label>
        <textarea
          class="textarea"
          v-model="actionText"
          :placeholder="actionMode === 'approve' ? '可通过后展示的备注说明' : '请填写驳回原因，将记录到审计日志'"
        ></textarea>
      </div>
      <template #foot>
        <button class="btn btn-ghost" @click="actionOpen = false">取消</button>
        <button
          v-if="actionMode === 'approve'"
          class="btn btn-success"
          :disabled="actionSaving"
          @click="submitAction"
        >{{ actionSaving ? '提交中…' : '确认通过' }}</button>
        <button
          v-else
          class="btn btn-danger"
          :disabled="actionSaving"
          @click="submitAction"
        >{{ actionSaving ? '提交中…' : '确认驳回' }}</button>
      </template>
    </AppModal>

    <!-- 分类修正弹窗 -->
    <AppModal v-if="catOpen" title="修正分类" @close="catOpen = false">
      <p class="a-title">{{ catItem?.title }}</p>
      <div class="form-item">
        <label>主题标签（选 1 - 3 个）</label>
        <div class="cat-pick">
          <label
            v-for="c in CATEGORY_DICTIONARY"
            :key="c"
            class="cat-opt"
            :class="{ on: catSelected.includes(c) }"
          >
            <input type="checkbox" :value="c" v-model="catSelected" />
            <span>{{ c }}</span>
          </label>
        </div>
        <span class="hint">当前已选 {{ catSelected.length }} 个，保存后将覆盖原分类</span>
      </div>
      <template #foot>
        <button class="btn btn-ghost" @click="catOpen = false">取消</button>
        <button class="btn" :disabled="catSaving || !catSelected.length" @click="saveCategory">
          {{ catSaving ? '保存中…' : '保存' }}
        </button>
      </template>
    </AppModal>
  </div>
</template>

<style scoped>
.tabs { display: flex; gap: 6px; margin-bottom: 16px; flex-wrap: wrap; }
.tab {
  padding: 6px 16px;
  border-radius: 99px;
  border: 1px solid var(--border-2);
  background: var(--surface);
  color: var(--text-2);
  font-size: 13px;
  font-family: var(--font);
  cursor: pointer;
  transition: all 0.15s;
}
.tab:hover { color: var(--primary); border-color: var(--primary); }
.tab.active { background: var(--primary); border-color: var(--primary); color: #fff; font-weight: 500; }

.review-card { padding: 16px 20px 14px; margin-bottom: 12px; }
.r-top { display: flex; align-items: center; gap: 10px; font-size: 12.5px; color: var(--text-3); margin-bottom: 7px; flex-wrap: wrap; }
.r-src { color: var(--primary); font-weight: 500; }
.r-title { font-size: 15.5px; font-weight: 600; line-height: 1.5; margin-bottom: 8px; cursor: pointer; }
.r-title:hover { color: var(--primary-dark); }
.r-cats { display: flex; align-items: center; flex-wrap: wrap; gap: 6px; margin-bottom: 10px; }
.r-summary {
  color: var(--text-2);
  font-size: 13.5px;
  white-space: pre-line;
  display: -webkit-box;
  -webkit-line-clamp: 4;
  -webkit-box-orient: vertical;
  overflow: hidden;
  margin-bottom: 12px;
}
.r-comment {
  background: var(--warning-bg);
  border-radius: 7px;
  padding: 8px 12px;
  font-size: 13px;
  color: var(--text-2);
  margin-bottom: 12px;
}
.c-label { font-weight: 600; color: var(--warning); }
.r-foot { display: flex; align-items: center; justify-content: space-between; gap: 10px; border-top: 1px solid var(--border); padding-top: 11px; flex-wrap: wrap; }
.r-time { font-size: 12px; color: var(--text-3); }
.r-ops { display: flex; gap: 8px; }

.a-title { font-size: 14.5px; font-weight: 600; margin-bottom: 16px; line-height: 1.5; }

.cat-pick { display: flex; flex-wrap: wrap; gap: 8px; }
.cat-opt {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 12px;
  border: 1px solid var(--border-2);
  border-radius: 99px;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.12s;
  user-select: none;
}
.cat-opt input { display: none; }
.cat-opt.on { background: var(--primary); border-color: var(--primary); color: #fff; }
</style>
