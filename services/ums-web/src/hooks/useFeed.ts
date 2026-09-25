import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../api/client';
import type { FeedItem, PushRecord } from '../api/types';

export type FeedTab = 'today' | 'major_changes' | 'updates' | 'pushes';

/** 指南 3.2：我的情报数据获取，staleTime 60s 减少重复请求 */
export function useFeed(tab: FeedTab) {
  return useQuery({
    queryKey: ['feed', tab],
    queryFn: () =>
      api.get<never, FeedItem[] | PushRecord[]>('/me/feed', { params: { tab } }),
    staleTime: 60_000,
  });
}

/**
 * 指南 3.2：批量已读 —— 乐观更新，不等接口返回就先把 UI 状态改成已读。
 */
export function useBatchMarkRead() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (contentIds: string[]) =>
      api.post('/me/read-status/batch', { contentIds }),
    onMutate: async (contentIds) => {
      queryClient.setQueriesData<FeedItem[]>({ queryKey: ['feed'] }, (old) =>
        old?.map((item) =>
          contentIds.includes(item.articleId) ? { ...item, readStatus: true } : item,
        ),
      );
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ['feed'] });
    },
  });
}
