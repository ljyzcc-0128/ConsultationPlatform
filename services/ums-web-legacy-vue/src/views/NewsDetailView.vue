<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { newsApi } from '../api'
import StatusBadge from '../components/StatusBadge.vue'

const route = useRoute()
const router = useRouter()

const detail = ref(null)
const loading = ref(true)
const error = ref('')

onMounted(async () => {
  try {
    detail.value = await newsApi.detail(route.params.articleId)
    if (!detail.value) error.value = '资讯不存在或已下线'
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
})

function fmtDate(d) {
  return d ? String(d).replace('T', ' ') : ''
}
</script>

<template>
  <div class="page detail-page">
    <header class="topbar slim">
      <router-link to="/" class="back">&larr; 返回资讯时间线</router-link>
    </header>

    <div v-if="loading" class="spinner"></div>

    <div v-else-if="error" class="empty card">
      <div class="empty-icon">!</div>
      {{ error }}
    </div>

    <article v-else-if="detail" class="detail card">
      <div class="d-meta">
        <span class="tag src-tag">{{ detail.sourceName || detail.sourceId }}</span>
        <StatusBadge :status="detail.manualReviewStatus" />
        <span class="dot">{{ fmtDate(detail.publishTime || detail.publishDate) }}</span>
        <span class="dot" v-if="detail.author">{{ detail.author }}</span>
        <span class="dot">{{ detail.language === 'en' ? 'English' : '中文' }}</span>
      </div>

      <h1 class="d-title">{{ detail.title }}</h1>

      <div class="d-tags" v-if="detail.category && detail.category.length">
        <span class="tag" v-for="c in detail.category" :key="c">{{ c }}</span>
      </div>

      <section class="d-summary" v-if="detail.summary">
        <h4>AI 摘要</h4>
        <p>{{ detail.summary }}</p>
      </section>

      <section class="d-body" v-if="detail.body">
        <h4>正文</h4>
        <p>{{ detail.body }}</p>
      </section>

      <!-- 政策与影响分析（一期 AI 加工暂未填充，结构先行） -->
      <section class="d-analysis card-inner" v-if="detail.analysis">
        <h4>政策与影响分析</h4>
        <dl class="analysis-grid">
          <div v-if="detail.analysis.policyStatus"><dt>政策状态</dt><dd>{{ detail.analysis.policyStatus }}</dd></div>
          <div v-if="detail.analysis.policyName"><dt>政策名称</dt><dd>{{ detail.analysis.policyName }}</dd></div>
          <div v-if="detail.analysis.effectiveDate"><dt>生效日期</dt><dd>{{ detail.analysis.effectiveDate }}</dd></div>
          <div v-if="detail.analysis.bessRelevance"><dt>BESS 相关性</dt><dd>{{ detail.analysis.bessRelevance }}</dd></div>
          <div v-if="detail.analysis.jinkoEssRelevance"><dt>对 Jinko ESS 的意义</dt><dd>{{ detail.analysis.jinkoEssRelevance }}</dd></div>
          <div v-if="detail.analysis.importanceScore != null"><dt>重要性评分</dt><dd>{{ detail.analysis.importanceScore }}</dd></div>
        </dl>
      </section>

      <footer class="d-foot">
        <a :href="detail.originalUrl" target="_blank" rel="noopener noreferrer" class="btn btn-ghost">
          查看原文 &nearr;
        </a>
      </footer>
    </article>
  </div>
</template>

<style scoped>
.topbar.slim { padding-bottom: 14px; }
.back { font-size: 13.5px; color: var(--text-2); }
.back:hover { color: var(--primary); }

.detail { padding: 30px 36px 26px; }
.d-meta { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; font-size: 12.5px; color: var(--text-3); margin-bottom: 14px; }
.src-tag { font-size: 12px; }
.dot::before { content: "·"; margin-right: 10px; color: var(--border-2); }
.d-title { font-size: 22px; line-height: 1.5; font-weight: 650; margin-bottom: 14px; }
.d-tags { display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 20px; }

.d-summary, .d-body { margin-bottom: 24px; }
.d-summary h4, .d-body h4, .d-analysis h4 {
  font-size: 13px;
  color: var(--primary);
  font-weight: 600;
  margin-bottom: 8px;
  letter-spacing: 0.5px;
}
.d-summary p {
  background: var(--primary-light);
  border-left: 3px solid var(--primary);
  border-radius: 0 8px 8px 0;
  padding: 13px 16px;
  color: var(--text-2);
  white-space: pre-line;
  font-size: 14px;
  line-height: 1.75;
}
.d-body p { white-space: pre-line; color: var(--text-2); font-size: 14.5px; line-height: 1.85; }

.d-analysis { border: 1px dashed var(--border-2); border-radius: 8px; padding: 14px 16px; }
.d-analysis h4 { margin-bottom: 10px; }
.analysis-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 10px 20px; }
.analysis-grid dt { font-size: 12px; color: var(--text-3); margin-bottom: 2px; }
.analysis-grid dd { font-size: 13.5px; }

.d-foot { margin-top: 8px; padding-top: 16px; border-top: 1px solid var(--border); }

@media (max-width: 640px) {
  .detail { padding: 20px 18px; }
  .d-title { font-size: 19px; }
}
</style>
