import { bypass } from 'msw';
import { FALLBACK_NEWS } from './data';

/**
 * Phase 6.2/6.3 后端已全部切真实后端：
 * 供应链看板（/v1/supply-chain/*）+ 采集任务（/v1/admin/tasks）已移除 mock。
 * 仅保留搜索兜底数据（后端不可用时降级到静态新闻列表）。
 */

interface RawNewsItem {
  articleId: string;
  title: string;
  summary: string | null;
  sourceName: string;
  publishDate: string;
  publishTime: string | null;
  language: string;
  category: string[];
}

/** 尝试取真实 /api/news 数据，失败（后端未启动/测试环境）则回退静态数据 */
export async function fetchLiveNews(limit: number): Promise<RawNewsItem[]> {
  try {
    const res = await fetch(bypass(`/api/news?limit=${limit}`));
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const data = (await res.json()) as { items: RawNewsItem[] };
    return data.items ?? [];
  } catch {
    return FALLBACK_NEWS.slice(0, limit) as RawNewsItem[];
  }
}

export const handlers: import('msw').HttpHandler[] = [];
