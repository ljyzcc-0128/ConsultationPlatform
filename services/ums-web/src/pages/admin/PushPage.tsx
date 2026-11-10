import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Badge, Button, Space, Table, Tag, Typography, message } from 'antd';
import { PauseCircleOutlined, PlayCircleOutlined, SendOutlined } from '@ant-design/icons';
import { api } from '../../api/client';
import { ErrorState } from '../../components/ErrorState';
import { LazyChart } from '../../components/LazyChart';
import { ListSkeleton } from '../../components/ListSkeleton';
import { EmptyState } from '../../components/EmptyState';
import { useTheme } from '../../theme/ThemeProvider';
import { getChartTheme, type ChartPalette } from '../../theme/chartTheme';
import type { PushTask } from '../../api/types';
import { CHANNEL_TEXT, FREQUENCY_TEXT } from '../../api/types';

const STATUS_META: Record<PushTask['status'], { text: string; status: 'processing' | 'warning' | 'default' }> = {
  RUNNING: { text: '运行中', status: 'processing' },
  PAUSED: { text: '已暂停', status: 'warning' },
  FINISHED: { text: '已结束', status: 'default' },
};

/** 发送效果统计：近 N 日发送量与点击量组合图（ADM-006 要求统计图表，不适合纯 CRUD） */
function pushTrendOption(task: PushTask, chart: ChartPalette) {
  return {
    color: chart.colors,
    grid: { left: 48, right: 24, top: 32, bottom: 28 },
    legend: { top: 4, textStyle: { color: chart.label } },
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      data: task.trend.map((t) => t.date),
      axisLabel: { color: chart.label },
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: chart.label },
      splitLine: { lineStyle: { color: chart.line } },
    },
    series: [
      { name: '发送量', type: 'bar', data: task.trend.map((t) => t.sent), barMaxWidth: 20 },
      { name: '点击量', type: 'line', data: task.trend.map((t) => t.clicked), smooth: true },
    ],
  };
}

export function PushPage() {
  const queryClient = useQueryClient();
  const { theme } = useTheme();
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

  const jingcaiMutation = useMutation({
    mutationFn: () =>
      api.post<never, { title: string; taskId: number; receivers: string[] }>('/v1/admin/push/jingcai/digest'),
    onSuccess: (res) => {
      message.success(`已推送「${res.title}」，接收 ${res.receivers.length} 人，钉钉 task_id=${res.taskId}`);
    },
    onError: () => {
      message.error('推送失败，请检查钉钉推送配置（appKey/appSecret/agentId）');
    },
  });

  const tasks = data ?? [];

  return (
    <>
      <div className="page-head">
        <div>
          <h1>推送管理</h1>
          <div className="sub">
            推送任务按关注规则聚合内容，通过配置的渠道发送；此处查看发送效果统计。
          </div>
        </div>
        <Button
          type="primary"
          icon={<SendOutlined />}
          loading={jingcaiMutation.isPending}
          onClick={() => jingcaiMutation.mutate()}
        >
          立即推送晶彩日报
        </Button>
      </div>

      <div className="admin-table-wrap">
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
                    <LazyChart option={pushTrendOption(record, getChartTheme(theme))} height={260} />
                  </div>
                ),
              }}
            />
          </Space>
        )}
      </div>
    </>
  );
}
