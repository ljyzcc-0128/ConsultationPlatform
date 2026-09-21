import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Badge, Button, Card, Space, Table, Tag, Typography, message } from 'antd';
import { PauseCircleOutlined, PlayCircleOutlined } from '@ant-design/icons';
import { api } from '../../api/client';
import { ErrorState } from '../../components/ErrorState';
import { LazyChart } from '../../components/LazyChart';
import { ListSkeleton } from '../../components/ListSkeleton';
import { EmptyState } from '../../components/EmptyState';
import type { PushTask } from '../../api/types';
import { CHANNEL_TEXT, FREQUENCY_TEXT } from '../../api/types';

const STATUS_META: Record<PushTask['status'], { text: string; status: 'processing' | 'warning' | 'default' }> = {
  RUNNING: { text: '运行中', status: 'processing' },
  PAUSED: { text: '已暂停', status: 'warning' },
  FINISHED: { text: '已结束', status: 'default' },
};

/** 发送效果统计：近 N 日发送量与点击量组合图（ADM-006 要求统计图表，不适合纯 CRUD） */
function pushTrendOption(task: PushTask) {
  return {
    grid: { left: 48, right: 24, top: 32, bottom: 28 },
    legend: { top: 4 },
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: task.trend.map((t) => t.date) },
    yAxis: { type: 'value' },
    series: [
      { name: '发送量', type: 'bar', data: task.trend.map((t) => t.sent), barMaxWidth: 20 },
      { name: '点击量', type: 'line', data: task.trend.map((t) => t.clicked), smooth: true },
    ],
  };
}

export function PushPage() {
  const queryClient = useQueryClient();
  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['admin-push'],
    queryFn: () => api.get<never, PushTask[]>('/v1/admin/push'),
  });

  const toggleMutation = useMutation({
    mutationFn: (id: string) => api.post(`/v1/admin/push/${id}/toggle`),
    onSuccess: () => {
      message.success('状态已更新');
      queryClient.invalidateQueries({ queryKey: ['admin-push'] });
    },
  });

  const tasks = data ?? [];

  return (
    <Card title="推送管理">
      <Typography.Paragraph type="secondary">
        推送任务按关注规则聚合内容，通过配置的渠道发送；此处查看发送效果统计。
      </Typography.Paragraph>
      {isLoading ? (
        <ListSkeleton rows={4} />
      ) : isError ? (
        <ErrorState error={error} onRetry={() => refetch()} />
      ) : (
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          <Table<PushTask>
            rowKey="id"
            dataSource={tasks}
            pagination={false}
            locale={{
              emptyText: <EmptyState description="还没有配置推送任务，可在关注规则中配置推送频率后自动生成。" />,
            }}
            columns={[
              { title: '任务', dataIndex: 'name', width: 200 },
              { title: '目标人群', dataIndex: 'target' },
              {
                title: '渠道',
                dataIndex: 'channel',
                width: 100,
                render: (c: PushTask['channel']) => CHANNEL_TEXT[c],
              },
              {
                title: '频率',
                dataIndex: 'frequency',
                width: 80,
                render: (f: PushTask['frequency']) => FREQUENCY_TEXT[f],
              },
              {
                title: '状态',
                dataIndex: 'status',
                width: 100,
                render: (v: PushTask['status']) => {
                  const meta = STATUS_META[v];
                  return <Badge status={meta.status} text={meta.text} />;
                },
              },
              { title: '最近发送', dataIndex: 'lastSentAt', width: 150 },
              {
                title: '发送效果',
                width: 220,
                render: (_, r) => (
                  <Space size={4}>
                    <Tag>发送 {r.stats.sent}</Tag>
                    <Tag color="blue">送达 {r.stats.delivered}</Tag>
                    <Tag color="green">点击 {r.stats.clicked}</Tag>
                  </Space>
                ),
              },
              {
                title: '操作',
                width: 110,
                render: (_, r) => (
                  <Button
                    size="small"
                    icon={r.status === 'RUNNING' ? <PauseCircleOutlined /> : <PlayCircleOutlined />}
                    disabled={r.status === 'FINISHED'}
                    onClick={() => toggleMutation.mutate(r.id)}
                  >
                    {r.status === 'RUNNING' ? '暂停' : '恢复'}
                  </Button>
                ),
              },
            ]}
            expandable={{
              expandedRowRender: (record) => (
                <div>
                  <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                    近期发送量 / 点击量趋势：
                  </Typography.Text>
                  <LazyChart option={pushTrendOption(record)} height={260} />
                </div>
              ),
            }}
          />
        </Space>
      )}
    </Card>
  );
}
