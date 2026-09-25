import { useMemo, useState } from 'react';
import { useQuery, type QueryKey } from '@tanstack/react-query';
import type { TablePaginationConfig } from 'antd';

/**
 * 指南 6.2：通用 CRUD 列表钩子 —— 封装「列表查询 + 分页 + 筛选」，
 * 供信源管理 / 用户管理 / 分类标签等结构相似的模块复用。
 */
export function useCrudTable<T>({
  queryKey,
  fetcher,
  defaultPageSize = 10,
}: {
  queryKey: QueryKey;
  fetcher: () => Promise<T[]>;
  defaultPageSize?: number;
}) {
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(defaultPageSize);
  const [filters, setFilters] = useState<Record<string, string>>({});

  const query = useQuery({
    queryKey: [...(Array.isArray(queryKey) ? queryKey : [queryKey]), filters],
    queryFn: fetcher,
  });

  /** 客户端过滤（字段 → 包含匹配），服务端分页场景可改为透传 params */
  const filtered = useMemo(() => {
    let data = query.data ?? [];
    for (const [field, value] of Object.entries(filters)) {
      if (!value) continue;
      data = data.filter((item) => {
        const v = (item as Record<string, unknown>)[field];
        return String(v ?? '').toLowerCase().includes(value.toLowerCase());
      });
    }
    return data;
  }, [query.data, filters]);

  const total = filtered.length;
  const pageData = useMemo(
    () => filtered.slice((page - 1) * pageSize, page * pageSize),
    [filtered, page, pageSize],
  );

  const pagination: TablePaginationConfig = {
    current: page,
    pageSize,
    total,
    showSizeChanger: true,
    showTotal: (t) => `共 ${t} 条`,
    onChange: (p, ps) => {
      setPage(p);
      setPageSize(ps);
    },
  };

  return {
    query,
    data: pageData,
    allData: filtered,
    pagination,
    filters,
    setFilter: (field: string, value: string) => {
      setFilters((prev) => ({ ...prev, [field]: value }));
      setPage(1);
    },
    reload: () => query.refetch(),
  };
}
