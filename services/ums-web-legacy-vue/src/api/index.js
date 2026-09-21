/**
 * 后端 REST API 封装。
 * 开发态经 Vite 代理 /api → localhost:8080；生产同源部署无需改路径。
 */

class ApiError extends Error {
  constructor(status, message) {
    super(message)
    this.status = status
  }
}

async function request(path, { method = 'GET', params, body } = {}) {
  let url = path
  if (params) {
    const qs = new URLSearchParams()
    for (const [k, v] of Object.entries(params)) {
      if (v !== undefined && v !== null && v !== '') qs.append(k, v)
    }
    const s = qs.toString()
    if (s) url += (url.includes('?') ? '&' : '?') + s
  }
  const opts = { method, headers: {} }
  if (body !== undefined) {
    opts.headers['Content-Type'] = 'application/json'
    opts.body = JSON.stringify(body)
  }
  const res = await fetch(url, opts)
  if (!res.ok) {
    let msg = `HTTP ${res.status}`
    try {
      const data = await res.json()
      msg = data.message || data.error || msg
    } catch { /* 非 JSON 错误体，保留状态码信息 */ }
    throw new ApiError(res.status, msg)
  }
  return res.json()
}

/** 主题字典（与后端 GLM 分类 prompt 同源） */
export const CATEGORY_DICTIONARY = [
  'BESS', 'Policy', 'Power Market', 'Electricity Price', 'Renewable Energy',
  'Supply Chain', 'Technology', 'Market Analysis', 'Company News', 'Regulation',
  'Safety', 'Finance'
]

export const REVIEW_STATUSES = ['Pending', 'NeedsReview', 'Approved', 'Rejected']

export const STATUS_LABELS = {
  Pending: '待审核', NeedsReview: '需复核', Approved: '已通过', Rejected: '已驳回',
  NotRun: '未运行', Success: '成功', Failed: '失败'
}

/** JSON 数组字符串（如 ["BESS","Policy"]）→ 数组；容错返回空数组 */
export function parseCategory(json) {
  if (!json) return []
  if (Array.isArray(json)) return json
  try { return JSON.parse(json) } catch { return [] }
}

/* ---------- 用户端：内容查询 ---------- */
export const newsApi = {
  list(filters) {
    return request('/api/news', { params: { limit: 20, ...filters } })
  },
  detail(articleId) {
    return request(`/api/news/${articleId}`)
  }
}

/* ---------- 管理端 ---------- */
export const adminApi = {
  // 信源管理
  listSources() { return request('/api/v1/admin/sources') },
  updateSource(sourceId, body) { return request(`/api/v1/admin/sources/${sourceId}`, { method: 'PUT', body }) },
  triggerSource(sourceId, limit = 3) {
    return request(`/api/v1/admin/sources/${sourceId}/trigger`, { method: 'POST', params: { limit } })
  },
  // 审核队列
  listReviews(params) { return request('/api/v1/admin/reviews', { params }) },
  approveReview(articleId, comment) {
    return request(`/api/v1/admin/reviews/${articleId}/approve`, { method: 'POST', body: { comment: comment || null } })
  },
  rejectReview(articleId, reason) {
    return request(`/api/v1/admin/reviews/${articleId}/reject`, { method: 'POST', body: { reason } })
  },
  updateCategory(articleId, categories) {
    return request(`/api/v1/admin/reviews/${articleId}/category`, { method: 'PUT', body: categories })
  },
  // 死信队列
  dlqQueues() { return request('/api/v1/admin/dlq') },
  dlqMessages(queue, count = 10) { return request(`/api/v1/admin/dlq/${queue}/messages`, { params: { count } }) },
  dlqRequeue(queue) { return request(`/api/v1/admin/dlq/${queue}/requeue`, { method: 'POST' }) },
  dlqPurge(queue) { return request(`/api/v1/admin/dlq/${queue}`, { method: 'DELETE' }) },
  // 审计日志
  auditLogs(params) { return request('/api/v1/admin/audit-logs', { params }) },
  // 调度状态
  schedulerStatus() { return request('/api/v1/admin/scheduler/status') }
}
