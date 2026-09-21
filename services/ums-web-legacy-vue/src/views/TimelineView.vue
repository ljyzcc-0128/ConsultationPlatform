<script setup>
import { reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { newsApi, adminApi, CATEGORY_DICTIONARY, STATUS_LABELS } from '../api'
import NewsCard from '../components/NewsCard.vue'

const router = useRouter()

const filters = reactive({
  sourceId: '', category: '', language: '', dateFrom: '', dateTo: '', reviewStatus: ''
})
const sources = ref([])
const items = ref([])
const nextCursor = ref(null)
const hasMore = ref(false)
const loading = ref(false)
const loadingMore = ref(false)
const error = ref('')

function resetFilters() {
  Object.assign(filters, { sourceId: '', category: '', language: '', dateFrom: '', dateTo: '', reviewStatus: '' })
}

async function load(isMore = false) {
  if (isMore) loadingMore.value = true
  else { loading.value = true; error.value = ''; items.value = [] }
  try {
    const page = await newsApi.list({
      ...filters,
      cursor: isMore ? nextCursor.value : undefined
    })
    items.value = isMore ? items.value.concat(page.items) : page.items
    nextCursor.value = page.nextCursor
    hasMore.value = page.hasMore
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

// 筛选变化 → 重新加载首页（简单深比较触发）
let filterTimer = null
function onFilterChange() {
  clearTimeout(filterTimer)
  filterTimer = setTimeout(() => load(false), 250)
}

onMounted(async () => {
  load(false)
  // 信源下拉复用管理端接口（一期本地无鉴权）
  try { sources.value = await adminApi.listSources() } catch { /* 下拉退化为手填 */ }
})
</script>

<template>
  <div class="page">
    <!-- 顶部导航 -->
    <header class="topbar">
      <div class="brand">
        <span class="brand-mark">ESS</span>
        <div class="brand-text">
          <strong>储能资讯平台</strong>
          <span class="brand-sub">Energy Storage Insight</span>
        </div>
      </div>
      <router-link to="/admin" class="admin-entry">管理后台</router-link>
    </header>

    <!-- 筛选栏 -->
    <section class="filter-bar card">
      <div class="filter-grid">
        <div class="f-item">
          <label>信源</label>
          <select class="select" v-model="filters.sourceId" @change="onFilterChange">
            <option value="">全部</option>
            <option v-for="s in sources" :key="s.sourceId" :value="s.sourceId">
              {{ s.sourceId }} · {{ s.sourceName }}
            </option>
          </select>
        </div>
        <div class="f-item">
          <label>分类</label>
          <select class="select" v-model="filters.category" @change="onFilterChange">
            <option value="">全部</option>
            <option v-for="c in CATEGORY_DICTIONARY" :key="c" :value="c">{{ c }}</option>
          </select>
        </div>
        <div class="f-item">
          <label>语言</label>
          <select class="select" v-model="filters.language" @change="onFilterChange">
            <option value="">全部</option>
            <option value="zh-CN">中文</option>
            <option value="en">English</option>
          </select>
        </div>
        <div class="f-item">
          <label>开始日期</label>
          <input type="date" class="input" v-model="filters.dateFrom" @change="onFilterChange" />
        </div>
        <div class="f-item">
          <label>结束日期</label>
          <input type="date" class="input" v-model="filters.dateTo" @change="onFilterChange" />
        </div>
        <div class="f-item">
          <label>审核状态</label>
          <select class="select" v-model="filters.reviewStatus" @change="onFilterChange">
            <option value="">全部</option>
            <option v-for="s in ['Pending', 'NeedsReview', 'Approved', 'Rejected']" :key="s" :value="s">
              {{ STATUS_LABELS[s] }}
            </option>
          </select>
        </div>
        <div class="f-item f-reset">
          <label>&nbsp;</label>
          <button class="btn btn-ghost" @click="resetFilters(); onFilterChange()">重置</button>
        </div>
      </div>
    </section>

    <!-- 列表 -->
    <main class="timeline">
      <div v-if="loading" class="spinner"></div>
      <div v-else-if="error" class="empty">
        <div class="empty-icon">!</div>
        加载失败：{{ error }}
      </div>
      <div v-else-if="!items.length" class="empty card">
        <div class="empty-icon">◎</div>
        暂无符合条件的资讯
      </div>
      <template v-else>
        <NewsCard
          v-for="item in items"
          :key="item.articleId"
          :item="item"
          @open="id => router.push(`/news/${id}`)"
        />
        <div class="load-more" v-if="hasMore">
          <button class="btn btn-ghost" :disabled="loadingMore" @click="load(true)">
            {{ loadingMore ? '加载中…' : '加载更多' }}
          </button>
        </div>
        <p class="list-end" v-else>— 已到末尾 —</p>
      </template>
    </main>
  </div>
</template>

<style scoped>
.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 0 18px;
}
.brand { display: flex; align-items: center; gap: 12px; }
.brand-mark {
  width: 42px; height: 42px;
  border-radius: 11px;
  background: linear-gradient(135deg, #1d5bd6, #0ea5a4);
  color: #fff;
  font-weight: 700;
  font-size: 13px;
  display: flex; align-items: center; justify-content: center;
  letter-spacing: 0.5px;
}
.brand-text { display: flex; flex-direction: column; line-height: 1.3; }
.brand-text strong { font-size: 17px; }
.brand-sub { font-size: 11.5px; color: var(--text-3); letter-spacing: 0.4px; }
.admin-entry { font-size: 13.5px; color: var(--text-2); padding: 6px 14px; border: 1px solid var(--border-2); border-radius: 99px; }
.admin-entry:hover { color: var(--primary); border-color: var(--primary); }

.filter-bar { padding: 16px 18px 4px; margin-bottom: 20px; }
.filter-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(140px, 1fr)); gap: 12px 14px; }
.f-item { display: flex; flex-direction: column; gap: 5px; }
.f-item label { font-size: 12px; color: var(--text-3); font-weight: 500; }
.f-item .select, .f-item .input { width: 100%; }

.load-more { text-align: center; padding: 10px 0 4px; }
.list-end { text-align: center; color: var(--text-3); font-size: 12.5px; padding: 14px 0 0; }

@media (max-width: 640px) {
  .filter-grid { grid-template-columns: repeat(2, 1fr); }
  .f-reset { grid-column: span 2; }
}
</style>
