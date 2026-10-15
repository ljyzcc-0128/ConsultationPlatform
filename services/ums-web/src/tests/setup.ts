import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterAll, afterEach, beforeAll } from 'vitest';
import { setupServer } from 'msw/node';
import { http, HttpResponse } from 'msw';
import { handlers } from '../mocks/handlers';

/** jsdom 环境补齐：antd 组件依赖 window.matchMedia */
if (typeof window !== 'undefined' && !window.matchMedia) {
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: (query: string) => ({
      matches: false,
      media: query,
      onchange: null,
      addListener: () => { },
      removeListener: () => { },
      addEventListener: () => { },
      removeEventListener: () => { },
      dispatchEvent: () => false,
    }),
  });
}

/** Node 端 MSW server：复用浏览器端 handlers */
export const server = setupServer(...handlers);

/** 测试用内存已读状态集合（替代已移除的 mock data 函数） */
const readStatusSet = new Set<string>();
export function resetReadStatus() {
  readStatusSet.clear();
}

beforeAll(() => {
  // 测试环境不依赖真实后端：覆盖 feed 与 read-status 接口
  server.use(
    http.get('/api/me/feed', ({ request }) => {
      const url = new URL(request.url);
      const tab = url.searchParams.get('tab') ?? 'today';
      const items = [
        {
          articleId: 'test-001',
          title: '国家能源局公示新型储能技术装备清单',
          summary: '摘要一：12 项新型储能技术装备入围。',
          sourceName: '中国储能网',
          publishDate: '2026-09-18',
          publishTime: '2026-09-18T08:00:00',
          language: 'zh-CN',
          category: ['BESS', 'Policy'],
          readStatus: readStatusSet.has('test-001'),
          changeType: 'MAJOR',
          importance: 'HIGH',
          aiGeneratedFields: ['summary'],
        },
        {
          articleId: 'test-002',
          title: '某公司发布储能新品',
          summary: '摘要二：新品发布。',
          sourceName: 'Energy-Storage.news',
          publishDate: '2026-09-17',
          publishTime: '2026-09-17T10:00:00',
          language: 'en',
          category: ['Company News'],
          readStatus: readStatusSet.has('test-002'),
          changeType: 'NORMAL',
          importance: 'NORMAL',
          aiGeneratedFields: ['summary'],
        },
      ];
      if (tab === 'major_changes')
        return HttpResponse.json(items.filter((i) => i.changeType === 'MAJOR'));
      if (tab === 'updates')
        return HttpResponse.json(items.filter((i) => i.changeType === 'NORMAL'));
      return HttpResponse.json(items);
    }),
    http.post('/api/me/read-status/batch', async ({ request }) => {
      const body = (await request.json()) as { contentIds: string[] };
      (body.contentIds ?? []).forEach((id) => readStatusSet.add(id));
      return HttpResponse.json({ success: true });
    }),
    http.post('/api/me/read-status/reset', () => {
      readStatusSet.clear();
      return HttpResponse.json({ success: true });
    }),
  );
  server.listen({ onUnhandledRequest: 'error' });
});

afterEach(() => {
  cleanup();
});

afterAll(() => {
  server.close();
});
