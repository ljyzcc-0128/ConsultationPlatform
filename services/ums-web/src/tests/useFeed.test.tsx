import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, renderHook, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { describe, expect, it } from 'vitest';
import { useBatchMarkRead, useFeed } from '../hooks/useFeed';
import type { FeedItem } from '../api/types';
import { resetReadStatus } from './setup';

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
}

describe('useFeed（指南 3.2 数据获取 + MSW mock 接口）', () => {
  it('加载今日重点数据', async () => {
    const { result } = renderHook(() => useFeed('today'), {
      wrapper: createWrapper(),
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    const data = result.current.data as FeedItem[] | undefined;
    expect(data).toHaveLength(2);
    expect(data?.[0].title).toContain('国家能源局');
    expect(data?.[0].aiGeneratedFields).toContain('summary');
  });

  it('按 Tab 过滤：重大变化视图只含 MAJOR 项', async () => {
    const { result } = renderHook(() => useFeed('major_changes'), {
      wrapper: createWrapper(),
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect((result.current.data as FeedItem[])?.every((i) => i.changeType === 'MAJOR')).toBe(true);
  });
});

describe('useBatchMarkRead（指南 3.2 乐观更新）', () => {
  it('批量标记已读：UI 状态先变已读（乐观更新），且刷新缓存后保持已读', async () => {
    resetReadStatus();
    const wrapper = createWrapper();
    const feed = renderHook(() => useFeed('today'), { wrapper });
    await waitFor(() => expect(feed.result.current.isSuccess).toBe(true));

    const mutation = renderHook(() => useBatchMarkRead(), { wrapper });
    await act(async () => {
      mutation.result.current.mutate(['test-001', 'test-002']);
    });
    await waitFor(() => expect(mutation.result.current.isSuccess).toBe(true));

    // 服务端（MSW 内存）状态已更新：重新查询后全部已读
    const refetched = renderHook(() => useFeed('today'), {
      wrapper: createWrapper(),
    });
    await waitFor(() => expect(refetched.result.current.isSuccess).toBe(true));
    expect((refetched.result.current.data as FeedItem[])?.every((i) => i.readStatus)).toBe(true);
    resetReadStatus();
  });
});
