import { useQuery } from '@tanstack/react-query';
import { Badge, Col, Row, Space, Statistic, Table, Tag, Typography } from 'antd';
import { api } from '../../api/client';
import type { SchedulerStatus, CrawlTaskRecord } from '../../api/types';
import { ErrorState } from '../../components/ErrorState';
import { ListSkeleton } from '../../components/ListSkeleton';

const TASK_STATUS: Record<CrawlTaskRecord['status'], { text: string; status: 'success' | 'warning' | 'error' }> = {
  SUCCESS: { text: '成功', status: 'success' },
  PARTIAL: { text: '部分失败', status: 'warning' },
  FAILED: { text: '失败', status: 'error' },
};

export function TasksPage() {
  const scheduler = useQuery({
    queryKey: ['admin-scheduler'],
    queryFn: () => api.get<never, SchedulerStatus>('/v1/admin/scheduler/status'),
    refetchInterval: 30_000,
  });

  const tasks = useQuery({
    queryKey: ['admin-tasks'],
    queryFn: () => api.get<never, CrawlTaskRecord[]>('/v1/admin/tasks'),
  });

  return (
    <>
      <div className="page-head">
        <div>
          <h1>采集任务</h1>
          <div className="sub">调度状态与任务执行记录，监控采集服务的运行情况</div>
        </div>
      </div>

      <div className="section-card">
        <div className="section-head">
          <div className="section-title">调度状态（XXL-JOB）</div>
        </div>
        {scheduler.isLoading ? (
          <ListSkeleton rows={2} />
        ) : scheduler.isError ? (
          <ErrorState error={scheduler.error} onRetry={() => scheduler.refetch()} />
        ) : (
          <>
            <Row gutter={16}>
              <Col span={8}>
                <Statistic title="单次触发上限" value={scheduler.data?.triggerLimit ?? 0} suffix="条" />
              </Col>
              <Col span={8}>
                <Statistic
                  title="当前到期信源"
                  value={scheduler.data?.dueCount ?? 0}
                  suffix="个"
                  valueStyle={{ color: (scheduler.data?.dueCount ?? 0) > 0 ? '#fa8c16' : undefined }}
                />
              </Col>
              <Col span={8}>
                <Statistic title="启用信源" value={scheduler.data?.sources.filter((s) => s.crawlEnabled).length ?? 0} suffix="个" />
              </Col>
            </Row>
            <Table<SchedulerStatus['sources'][number]>
              rowKey="sourceId"
              size="small"
              style={{ marginTop: 16 }}
              dataSource={scheduler.data?.sources ?? []}
              pagination={false}
              columns={[
                { title: '信源', dataIndex: 'sourceId', width: 90 },
                { title: '抓取开关', dataIndex: 'crawlEnabled', width: 90, render: (v: boolean) => (v ? <Tag color="green">开</Tag> : <Tag>关</Tag>) },
                {
                  title: '频率', dataIndex: 'crawlFrequency', width: 90, render: (v, r) => (
                    <Space size={4}>
                      <span>{v}</span>
                      {!r.frequencyValid && <Tag color="red">无效</Tag>}
                    </Space>
                  )
                },
                { title: '间隔(小时)', dataIndex: 'intervalHours', width: 100 },
                { title: '最近抓取', dataIndex: 'lastCrawlTime', render: (v: string | null) => v ?? '未运行' },
                {
                  title: '是否到期',
                  dataIndex: 'due',
                  width: 90,
                  render: (v: boolean) =>
                    v ? <Badge status="warning" text="待抓取" /> : <Badge status="default" text="未到期" />,
                },
              ]}
            />
          </>
        )}
      </div>

      <div className="section-card">
        <div className="section-head">
          <div className="section-title">任务执行记录</div>
        </div>
        {tasks.isLoading ? (
          <ListSkeleton rows={4} />
        ) : tasks.isError ? (
          <ErrorState error={tasks.error} onRetry={() => tasks.refetch()} />
        ) : (
          <Table<CrawlTaskRecord>
            rowKey="taskId"
            dataSource={tasks.data ?? []}
            pagination={{ pageSize: 10, showTotal: (t) => `共 ${t} 条` }}
            columns={[
              { title: '任务ID', dataIndex: 'taskId', width: 110, render: (v: string) => <Typography.Text code>{v}</Typography.Text> },
              { title: '信源', dataIndex: 'sourceId', width: 80 },
              { title: '来源', dataIndex: 'sourceName', width: 160 },
              { title: '触发方式', dataIndex: 'triggerType', width: 100, render: (v: string) => (v === 'MANUAL' ? <Tag>手动</Tag> : <Tag color="blue">定时</Tag>) },
              { title: '开始时间', dataIndex: 'startedAt', width: 170 },
              {
                title: '耗时', width: 90, render: (_, r) => {
                  const start = new Date(r.startedAt.replace(' ', 'T')).getTime();
                  const end = new Date(r.finishedAt.replace(' ', 'T')).getTime();
                  return <Typography.Text type="secondary">{Math.max(0, Math.round((end - start) / 1000))}s</Typography.Text>;
                }
              },
              { title: '抓取/入库/失败', width: 130, render: (_, r) => `${r.fetched} / ${r.published} / ${r.failed}` },
              {
                title: '状态', dataIndex: 'status', width: 100, render: (v: CrawlTaskRecord['status']) => {
                  const meta = TASK_STATUS[v];
                  return <Badge status={meta.status} text={meta.text} />;
                }
              },
            ]}
          />
        )}
      </div>
    </>
  );
}
