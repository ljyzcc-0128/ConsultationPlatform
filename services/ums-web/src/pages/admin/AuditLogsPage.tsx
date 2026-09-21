import { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Button, Card, Select, Space, Table, Tag, Typography } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import { api } from '../../api/client';
import { AUDIT_ACTION_TEXT, type AuditLog } from '../../api/types';
import { EmptyState } from '../../components/EmptyState';
import { ErrorState } from '../../components/ErrorState';
import { JsonDiff } from '../../components/JsonDiff';
import { ListSkeleton } from '../../components/ListSkeleton';

/**
 * 指南 6.3：审计日志是只读列表；detail（含 before/after 语义的 JSON）
 * 用通用 JsonDiff 组件高亮变化字段，不把 JSON 字符串直接堆在页面上。
 */
export function AuditLogsPage() {
  const [action, setAction] = useState<string>();
  const [page, setPage] = useState(1);
  const pageSize = 10;

  const { data, isLoading, isError, error, refetch, isFetching } = useQuery({
    queryKey: ['admin-audit-logs', action],
    queryFn: () =>
      api.get<never, AuditLog[]>('/v1/admin/audit-logs', {
        params: { ...(action ? { action } : {}), limit: 100, offset: 0 },
      }),
  });

  const allLogs = useMemo(() => data ?? [], [data]);
  const pageData = useMemo(
    () => allLogs.slice((page - 1) * pageSize, page * pageSize),
    [allLogs, page],
  );

  /** 把 detail JSON 解析成「变更后」快照：args 里最后一个对象参数即本次写入的值 */
  function extractAfter(detail: string | null): unknown {
    if (!detail) return null;
    try {
      const parsed = JSON.parse(detail) as { args?: unknown[] };
      const args = parsed.args ?? [];
      return args.length > 1 ? args[args.length - 1] : { args };
    } catch {
      return detail;
    }
  }

  return (
    <Card
      title="审计日志"
      extra={
        <Space>
          <Select
            allowClear
            placeholder="操作类型"
            style={{ width: 170 }}
            value={action}
            onChange={(v) => {
              setAction(v);
              setPage(1);
            }}
            options={Object.entries(AUDIT_ACTION_TEXT).map(([value, label]) => ({ value, label }))}
          />
          <Button icon={<ReloadOutlined />} onClick={() => refetch()} loading={isFetching}>
            刷新
          </Button>
        </Space>
      }
    >
      {isLoading ? (
        <ListSkeleton rows={6} />
      ) : isError ? (
        <ErrorState error={error} onRetry={() => refetch()} />
      ) : (
        <Table<AuditLog>
          rowKey="logId"
          dataSource={pageData}
          pagination={{
            current: page,
            pageSize,
            total: allLogs.length,
            onChange: setPage,
            showTotal: (t) => `共 ${t} 条`,
          }}
          locale={{
            emptyText: <EmptyState description="暂无审计记录。管理端的信源配置、审核、DLQ 等操作都会记录在此。" />,
          }}
          columns={[
            { title: 'ID', dataIndex: 'logId', width: 70 },
            { title: '操作人', dataIndex: 'operator', width: 110 },
            {
              title: '操作',
              dataIndex: 'action',
              width: 140,
              render: (v: string) => (
                <Tag color="blue">{AUDIT_ACTION_TEXT[v] ?? v}</Tag>
              ),
            },
            { title: '对象类型', dataIndex: 'objectType', width: 100 },
            {
              title: '对象ID',
              dataIndex: 'objectId',
              width: 150,
              render: (v: string | null) =>
                v ? (
                  <Typography.Text code style={{ fontSize: 11 }}>
                    {v.length > 18 ? `${v.slice(0, 16)}…` : v}
                  </Typography.Text>
                ) : (
                  <Typography.Text type="secondary">—</Typography.Text>
                ),
            },
            {
              title: '时间',
              dataIndex: 'createdAt',
              width: 170,
              render: (v: string) => v.replace('T', ' ').slice(0, 19),
            },
          ]}
          expandable={{
            expandedRowRender: (record) => (
              <Space direction="vertical" size={8} style={{ width: '100%' }}>
                <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                  操作详情（高亮展示本次写入/变更的字段）：
                </Typography.Text>
                <JsonDiff after={extractAfter(record.detail)} />
              </Space>
            ),
          }}
        />
      )}
    </Card>
  );
}
