import { createRouter, createWebHistory } from 'vue-router'
import TimelineView from '../views/TimelineView.vue'
import NewsDetailView from '../views/NewsDetailView.vue'
import AdminLayout from '../views/admin/AdminLayout.vue'

const routes = [
  { path: '/', name: 'timeline', component: TimelineView },
  { path: '/news/:articleId', name: 'news-detail', component: NewsDetailView },
  {
    path: '/admin',
    component: AdminLayout,
    redirect: '/admin/reviews',
    children: [
      { path: 'sources', name: 'admin-sources', component: () => import('../views/admin/SourcesView.vue') },
      { path: 'reviews', name: 'admin-reviews', component: () => import('../views/admin/ReviewsView.vue') },
      { path: 'dlq', name: 'admin-dlq', component: () => import('../views/admin/DlqView.vue') },
      { path: 'audit', name: 'admin-audit', component: () => import('../views/admin/AuditView.vue') },
      { path: 'scheduler', name: 'admin-scheduler', component: () => import('../views/admin/SchedulerView.vue') }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

export default createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior(to, from, savedPosition) {
    return savedPosition || { top: 0 }
  }
})
