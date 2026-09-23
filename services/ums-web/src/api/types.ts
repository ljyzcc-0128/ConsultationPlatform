/** 真实后端接口类型（Java ums-main 8080） */

export interface NewsItem {
  articleId: string;
  sourceId: string;
  sourceName: string;
  title: string;
  originalUrl: string | null;
  publishDate: string;
  publishTime: string | null;
  author: string | null;
  language: 'zh-CN' | 'en';
  category: string[];
  summary: string | null;
  duplicateGroupId: string | null;
  manualReviewStatus: 'Pending' | 'NeedsReview' | 'Approved' | 'Rejected';
}

export interface NewsAnalysis {
  policyStatus: string | null;
  policyName: string | null;
  effectiveDate: string | null;
  bessRelevance: string | null;
  jinkoEssRelevance: string | null;
  importanceScore: number | null;
}

export interface NewsDetail extends NewsItem {
  body: string | null;
  analysis: NewsAnalysis | null;
}

export interface NewsListResponse {
  items: NewsItem[];
  nextCursor: string | null;
  hasMore: boolean;
}

export interface Source {
  sourceId: string;
  sourceName: string;
  region: string;
  sourceType: string;
  entryUrl: string;
  crawlEnabled: boolean;
  crawlFrequency: string;
  priority: string;
  language: string;
  category: string | null; // JSON 字符串
  lastCrawlTime: string | null;
  crawlStatus: string;
  remark: string | null;
}

export interface SourceUpdateBody {
  crawlEnabled?: boolean;
  crawlFrequency?: string;
  priority?: string;
  sourceName?: string;
  remark?: string;
}

export interface ReviewItem {
  articleId: string;
  title: string;
  sourceId: string;
  sourceName: string;
  publishDate: string;
  language: string;
  category: string | null; // JSON 字符串
  summary: string | null;
  reviewStatus: 'Pending' | 'NeedsReview' | 'Approved' | 'Rejected';
  reviewComment: string | null;
  updatedAt: string;
}

export interface AuditLog {
  logId: number;
  operator: string;
  action: string;
  objectType: string;
  objectId: string;
  detail: string | null;
  createdAt: string;
}

export interface SchedulerSourceStatus {
  sourceId: string;
  crawlEnabled: boolean;
  crawlFrequency: string;
  frequencyValid: boolean;
  intervalHours: number;
  lastCrawlTime: string | null;
  crawlStatus: string;
  due: boolean;
}

export interface SchedulerStatus {
  triggerLimit: number;
  dueCount: number;
  sources: SchedulerSourceStatus[];
}

export interface DlqQueue {
  queue: string;
  messages: number;
}

export interface DlqMessage {
  payload: string;
  routingKey: string;
  redelivered: boolean;
}

/** 分类字典（12 个） */
export const CATEGORY_DICT = [
  'BESS',
  'Policy',
  'Power Market',
  'Electricity Price',
  'Renewable Energy',
  'Supply Chain',
  'Technology',
  'Market Analysis',
  'Company News',
  'Regulation',
  'Safety',
  'Finance',
] as const;

export const REVIEW_STATUS_TEXT: Record<string, string> = {
  Pending: '待审核',
  NeedsReview: '需人工复核',
  Approved: '已通过',
  Rejected: '已驳回',
};

export const AUDIT_ACTION_TEXT: Record<string, string> = {
  SOURCE_CREATE: '新增信源',
  SOURCE_UPDATE: '信源配置更新',
  SOURCE_TRIGGER: '手动触发采集',
  REVIEW_APPROVE: '审核通过',
  REVIEW_REJECT: '审核驳回',
  REVIEW_CATEGORY_FIX: '分类修正',
  DLQ_REQUEUE: '死信消息重新入队',
  DLQ_PURGE: '死信队列清空',
  USER_CREATE: '创建用户',
  USER_UPDATE: '更新用户',
  USER_DELETE: '删除用户',
  PUSH_TOGGLE: '推送任务启停',
};

// ---------- Phase 5：用户 / 订阅 / Feed / 推送 ----------

export type Role = 'USER' | 'ADMIN';

export interface FeedItem {
  articleId: string;
  title: string;
  summary: string | null;
  sourceName: string;
  publishDate: string;
  publishTime: string | null;
  language: string;
  category: string[];
  readStatus: boolean;
  changeType: 'MAJOR' | 'NORMAL';
  importance: 'HIGH' | 'NORMAL';
  aiGeneratedFields: string[];
}

export interface PushRecord {
  id: string;
  contentTitle: string;
  channel: Channel;
  sentAt: string;
  status: PushMessageStatus;
}

export type SubscriptionType =
  | 'CONTENT_DOMAIN'
  | 'THEME'
  | 'COMPANY'
  | 'KEYWORD'
  | 'INDICATOR'
  | 'EVENT';

export type Frequency = 'INSTANT' | 'DAILY' | 'WEEKLY';
export type Channel = 'IN_APP' | 'EMAIL' | 'WECHAT';
export type UserStatus = 'ACTIVE' | 'DISABLED';
export type PushTaskStatus = 'RUNNING' | 'PAUSED' | 'FINISHED';
export type PushMessageStatus = 'SENT' | 'DELIVERED' | 'CLICKED' | 'FAILED';

export interface Subscription {
  id: string;
  type: SubscriptionType;
  value: string;
  frequency: Frequency;
  channel: Channel;
  enabled: boolean;
}

export interface AdminUser {
  id: string;
  username: string;
  displayName: string;
  email: string | null;
  roles: Role[];
  status: UserStatus;
  provider: string;
  createdAt: string;
}

export interface PushTask {
  id: string;
  name: string;
  target: string | null;
  channel: Channel;
  frequency: Frequency;
  status: PushTaskStatus;
  lastSentAt: string | null;
  stats: { sent: number; delivered: number; clicked: number };
  trend: Array<{ date: string; sent: number; clicked: number }>;
}

/** 枚举码 → 中文标签（DB/API 用英文码，前端展示翻译） */
export const SUBSCRIPTION_TYPE_TEXT: Record<SubscriptionType, string> = {
  CONTENT_DOMAIN: '内容域',
  THEME: '主题',
  COMPANY: '企业',
  KEYWORD: '关键词',
  INDICATOR: '指标',
  EVENT: '事件',
};

export const FREQUENCY_TEXT: Record<Frequency, string> = {
  INSTANT: '实时',
  DAILY: '日报',
  WEEKLY: '周报',
};

export const CHANNEL_TEXT: Record<Channel, string> = {
  IN_APP: '站内',
  EMAIL: '邮件',
  WECHAT: '企业微信',
};

export const PUSH_MSG_STATUS_TEXT: Record<PushMessageStatus, string> = {
  SENT: '已发送',
  DELIVERED: '已送达',
  CLICKED: '已点击',
  FAILED: '发送失败',
};

export const PUSH_TASK_STATUS_TEXT: Record<PushTaskStatus, string> = {
  RUNNING: '运行中',
  PAUSED: '已暂停',
  FINISHED: '已结束',
};

export const USER_STATUS_TEXT: Record<UserStatus, string> = {
  ACTIVE: '启用',
  DISABLED: '已禁用',
};

// ---------- Phase 6.1：搜索 / 相关内容 / 事件时间线 ----------

export interface RelatedContent {
  articleId: string;
  title: string;
  sourceName: string;
  publishDate: string;
  relevance: 'HIGH' | 'NORMAL';
}

export interface TimelineEvent {
  detectedAt: string;
  eventType: 'FIRST_DETECTED' | 'HEAT_UP' | 'MAJOR_CHANGE' | 'STABLE_TRACKING';
  description: string;
}

export const RELEVANCE_TEXT: Record<RelatedContent['relevance'], string> = {
  HIGH: '高',
  NORMAL: '中',
};

export const EVENT_TYPE_TEXT: Record<TimelineEvent['eventType'], string> = {
  FIRST_DETECTED: '首次检测',
  HEAT_UP: '热度上升',
  MAJOR_CHANGE: '重大变化',
  STABLE_TRACKING: '进入稳定跟踪',
};

// ---------- Phase 6.2：供应链看板 ----------

export interface IndicatorPoint {
  date: string;
  value: number;
}

export interface IndicatorSeries {
  code: string;
  name: string;
  unit: string;
  dataScope: 'EXTERNAL_MARKET_DATA' | 'INTERNAL';
  sourceName: string;
  latestValue: number;
  momChange: number;
  points: IndicatorPoint[];
}

export interface InventoryBlock {
  months: string[];
  series: Array<{
    name: string;
    type: 'bar' | 'line';
    unit: string;
    data: number[];
    dataScope: 'EXTERNAL_MARKET_DATA' | 'INTERNAL';
    sourceName: string;
  }>;
}

export interface TradeRecord {
  id: string;
  goods: string;
  counterparty: string;
  route: string;
  date: string;
  status: '在途' | '已到港' | '清关中' | '已完成';
  timeline: Array<{ time: string; event: string }>;
}

export interface SupplierUpdate {
  id: number;
  supplierName: string;
  updateType: '扩产' | '减产' | '停产' | '信用风险上升' | '信用风险下降' | '新品发布';
  riskLevel: 'high' | 'medium' | 'low';
  date: string;
  description: string;
}

// ---------- Phase 6.3：采集任务记录 ----------

export interface CrawlTaskRecord {
  taskId: string;
  sourceId: string;
  sourceName: string;
  triggerType: 'MANUAL' | 'SCHEDULED';
  startedAt: string;
  finishedAt: string;
  fetched: number;
  published: number;
  failed: number;
  status: 'SUCCESS' | 'PARTIAL' | 'FAILED';
}

// ---------- 信源新增请求体 ----------

export interface SourceCreateBody {
  sourceId: string;
  sourceName: string;
  region?: string;
  sourceType?: string;
  entryUrl: string;
  language: string;
  crawlEnabled?: boolean;
  crawlFrequency?: string;
  priority?: string;
  pagination?: boolean;
  fetchDetail?: boolean;
  dateFilter?: boolean;
  category?: string;
  remark?: string;
}
