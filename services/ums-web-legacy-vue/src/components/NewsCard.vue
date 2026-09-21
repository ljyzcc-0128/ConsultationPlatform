<script setup>
import { computed } from 'vue'
import StatusBadge from './StatusBadge.vue'

const props = defineProps({
  item: { type: Object, required: true }
})

const emit = defineEmits(['open'])

const metaLine = computed(() => {
  const parts = [props.item.sourceName || props.item.sourceId]
  if (props.item.author) parts.push(props.item.author)
  parts.push(props.item.language === 'en' ? 'English' : '中文')
  return parts.join(' · ')
})

const timeText = computed(() => props.item.publishTime || props.item.publishDate || '未知时间')
</script>

<template>
  <article class="news-card card" @click="emit('open', item.articleId)">
    <div class="news-top">
      <span class="src">{{ metaLine }}</span>
      <span class="date">{{ timeText }}</span>
    </div>
    <h3 class="news-title">{{ item.title }}</h3>
    <div class="news-tags" v-if="item.category && item.category.length">
      <span class="tag" v-for="c in item.category" :key="c">{{ c }}</span>
    </div>
    <p class="news-summary" v-if="item.summary">{{ item.summary }}</p>
    <div class="news-bottom">
      <StatusBadge :status="item.manualReviewStatus" />
      <span class="read">阅读全文 &rarr;</span>
    </div>
  </article>
</template>

<style scoped>
.news-card {
  padding: 18px 22px 16px;
  margin-bottom: 14px;
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s, transform 0.15s;
}
.news-card:hover {
  border-color: #b9c8e8;
  box-shadow: var(--shadow-lg);
  transform: translateY(-1px);
}
.news-top { display: flex; justify-content: space-between; gap: 12px; font-size: 12.5px; color: var(--text-3); margin-bottom: 8px; flex-wrap: wrap; }
.news-top .src { color: var(--primary); font-weight: 500; }
.news-title { font-size: 16.5px; font-weight: 600; line-height: 1.45; margin-bottom: 8px; }
.news-card:hover .news-title { color: var(--primary-dark); }
.news-tags { display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 10px; }
.news-summary {
  color: var(--text-2);
  font-size: 13.5px;
  white-space: pre-line;
  display: -webkit-box;
  -webkit-line-clamp: 5;
  -webkit-box-orient: vertical;
  overflow: hidden;
  margin-bottom: 12px;
}
.news-bottom { display: flex; justify-content: space-between; align-items: center; }
.read { font-size: 12.5px; color: var(--text-3); }
.news-card:hover .read { color: var(--primary); }
</style>
