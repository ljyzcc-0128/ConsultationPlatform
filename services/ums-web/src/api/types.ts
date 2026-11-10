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
  exchange: string;
  /** AMQP 消息属性（message_id/timestamp/content_type/priority 等） */
  properties: Record<string, unknown>;
  /** 自定义消息头（properties.headers） */
  headers: Record<string, unknown>;
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

export const AUDIT_ACTION_TEXT: Record<string, string> = {
  SOURCE_CREATE: '新增信源',
  SOURCE_UPDATE: '信源配置更新',
  SOURCE_TRIGGER: '手动触发采集',
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

/** 同指标多市场明细（卓创华东各省市行）：市场名 + 最新价 */
export interface IndicatorSubjectPoint {
  subject: string;
  value: number;
  date: string;
}

/** 一个交易日下各市场的价格快照（多市场按日趋势，供横轴市场、多日曲线对比图） */
export interface IndicatorSubjectDay {
  date: string;
  points: IndicatorSubjectPoint[];
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
  /** 明细主体最新值；无细分指标为空数组（此时展示大数值） */
  subjects: IndicatorSubjectPoint[];
  /** 多市场按日趋势（最新 3 个交易日，日期升序）；无细分指标为空数组 */
  subjectTrend: IndicatorSubjectDay[];
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

// ---------- AI 智能问答（POST /v1/qa/ask） ----------

export interface QaReference {
  articleId: string;
  title: string;
  sourceName: string;
  originalUrl: string | null;
  publishDate: string | null;
}

export interface QaAnswer {
  answer: string;
  mode: 'NEWS' | 'PLATFORM';
  references: QaReference[];
  provider: string;
  model: string;
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

// ---------- XXL-JOB 定时任务（管理后台维护列表） ----------

export interface XxlJobInfo {
  id: number;
  jobGroup: number;
  jobDesc: string;
  scheduleType: string;
  scheduleConf: string;
  executorRouteStrategy: string;
  executorHandler: string;
  executorParam: string;
  misfireStrategy: string;
  executorBlockStrategy: string;
  author: string;
  alarmEmail: string;
  /** 0 停止 / 1 运行 */
  triggerStatus: number;
  /** 毫秒时间戳，0 表示从未触发/未排程 */
  triggerLastTime: number;
  triggerNextTime: number;
  createTime: number;
  updateTime: number;
}

export interface XxlJobPage {
  total: number;
  list: XxlJobInfo[];
}

/** 任务新增/编辑请求体（未填字段由后端给调度中心默认值） */
export interface XxlJobUpsertBody {
  jobDesc: string;
  scheduleType: string;
  scheduleConf: string;
  executorHandler: string;
  executorParam?: string;
  author?: string;
  alarmEmail?: string;
  misfireStrategy?: string;
  executorRouteStrategy?: string;
  executorBlockStrategy?: string;
}
