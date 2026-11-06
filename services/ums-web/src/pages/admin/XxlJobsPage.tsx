import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Button,
  Form,
  Input,
  Popconfirm,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Tooltip,
  Typography,
  message,
} from 'antd';
import type { FormInstance } from 'antd';
import {
  CaretRightOutlined,
  EditOutlined,
  MinusCircleOutlined,
  PlusOutlined,
  ReloadOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import { api, getErrorMessage } from '../../api/client';
import type { XxlJobInfo, XxlJobPage, XxlJobUpsertBody } from '../../api/types';
import { ErrorState } from '../../components/ErrorState';
import { ListSkeleton } from '../../components/ListSkeleton';
import {
  JobFormModal,
  SCHEDULE_TYPE,
  useJobStats,
  type JobFormValues,
} from './xxlJobParts';

function fmtTime(ms: number): string {
  if (!ms) return '—';
  const d = new Date(ms);
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`;
}

function fmtDuration(ms: number): string {
  if (!ms) return '—';
  const diff = Date.now() - ms;
  if (diff < 0) return fmtTime(ms);
  const min = Math.floor(diff / 60_000);
  if (min < 1) return '刚刚';
  if (min < 60) return `${min} 分钟前`;
  const hour = Math.floor(min / 60);
  if (hour < 24) return `${hour} 小时前`;
  return `${Math.floor(hour / 24)} 天前`;
}

/** 定时任务维护页：读取 XXL-JOB 调度中心全量任务，支持增删改、启停、手动执行 */
export function XxlJobsPage() {
  const queryClient = useQueryClient();
  const [page, setPage] = useState({ current: 1, pageSize: 10 });
  const [jobDesc, setJobDesc] = useState('');
  const [searchDesc, setSearchDesc] = useState('');
  const [triggerStatus, setTriggerStatus] = useState<number>(-1);
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<XxlJobInfo | null>(null);
  const [form] = Form.useForm<JobFormValues>();

  const jobs = useQuery({
    queryKey: ['admin-xxljob', page.current, page.pageSize, searchDesc, triggerStatus],
    queryFn: () =>
      api.get<never, XxlJobPage>('/v1/admin/xxljob/jobs', {
        params: {
          start: (page.current - 1) * page.pageSize,
          length: page.pageSize,
          jobDesc: searchDesc || undefined,
          triggerStatus,
        },
      }),
  });

  const stats = useJobStats(jobs.data?.list);

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['admin-xxljob'] });

  const onActionError = (e: unknown) => message.error(getErrorMessage(e));

  const startMutation = useMutation({
    mutationFn: (id: number) => api.post(`/v1/admin/xxljob/jobs/${id}/start`),
    onSuccess: () => {
      message.success('任务已启动');
      invalidate();
    },
    onError: onActionError,
  });

  const stopMutation = useMutation({
    mutationFn: (id: number) => api.post(`/v1/admin/xxljob/jobs/${id}/stop`),
    onSuccess: () => {
      message.success('任务已停止');
      invalidate();
    },
    onError: onActionError,
  });

  const triggerMutation = useMutation({
    mutationFn: (id: number) => api.post(`/v1/admin/xxljob/jobs/${id}/trigger`),
    onSuccess: () => message.success('已触发一次，可在调度中心查看执行日志'),
    onError: onActionError,
  });

  const removeMutation = useMutation({
    mutationFn: (id: number) => api.delete(`/v1/admin/xxljob/jobs/${id}`),
    onSuccess: () => {
      message.success('任务已删除');
      invalidate();
    },
    onError: onActionError,
  });

  const saveMutation = useMutation({
    mutationFn: (values: JobFormValues) => {
      const body: XxlJobUpsertBody = { ...values };
      return editing
        ? api.put(`/v1/admin/xxljob/jobs/${editing.id}`, body)
        : api.post<{ id: number }>('/v1/admin/xxljob/jobs', body);
    },
    onSuccess: () => {
      message.success(editing ? '任务已保存' : '任务已创建（默认停止，可点击开关启动）');
      setFormOpen(false);
      invalidate();
    },
    onError: onActionError,
  });

  const toggle = (record: XxlJobInfo, checked: boolean) =>
    checked ? startMutation.mutate(record.id) : stopMutation.mutate(record.id);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    setFormOpen(true);
  };

  const openEdit = (record: XxlJobInfo) => {
    setEditing(record);
    form.setFieldsValue({
      jobDesc: record.jobDesc,
      executorHandler: record.executorHandler,
      scheduleType: record.scheduleType === 'FIX_RATE' ? 'FIX_RATE' : 'CRON',
      scheduleConf: record.scheduleConf,
      executorParam: record.executorParam,
      author: record.author,
      alarmEmail: record.alarmEmail || undefined,
      misfireStrategy: record.misfireStrategy || 'DO_NOTHING',
      executorRouteStrategy: record.executorRouteStrategy || 'FIRST',
      executorBlockStrategy: record.executorBlockStrategy || 'SERIAL_EXECUTION',
    });
    setFormOpen(true);
  };

  const submitForm = async () => {
    const values = await form.validateFields();
    saveMutation.mutate(values);
  };

  return (
    <>
      <div className="page-head">
        <div>
          <h1>定时任务</h1>
          <div className="sub">
            来自 XXL-JOB 调度中心的全量定时任务，支持增删改、启停与手动执行；改动直接作用于调度中心。
          </div>
        </div>
      </div>

      <div className="section-card">
        <div className="section-head">
          <div className="section-title">
            任务列表
            <Space size={6} style={{ marginLeft: 12 }}>
              <Tag icon={<ThunderboltOutlined />} color="processing">{`运行中 ${stats.running}`}</Tag>
              <Tag color="default">{`已停止 ${stats.stopped}`}</Tag>
            </Space>
          </div>
          <Space>
            <Input
              allowClear
              placeholder="按任务描述搜索"
              style={{ width: 200 }}
              value={jobDesc}
              onChange={(e) => setJobDesc(e.target.value)}
              onPressEnter={() => {
                setPage({ ...page, current: 1 });
                setSearchDesc(jobDesc);
              }}
            />
            <Select<number>
              style={{ width: 110 }}
              value={triggerStatus}
              onChange={(v) => {
                setPage({ ...page, current: 1 });
                setTriggerStatus(v);
              }}
              options={[
                { value: -1, label: '全部状态' },
                { value: 1, label: '运行中' },
                { value: 0, label: '已停止' },
              ]}
            />
            <Button
              type="primary"
              ghost
              onClick={() => {
                setPage({ ...page, current: 1 });
                setSearchDesc(jobDesc);
              }}
            >
              查询
            </Button>
            <Tooltip title="刷新">
              <Button icon={<ReloadOutlined />} onClick={() => invalidate()} />
            </Tooltip>
            <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
              新增任务
            </Button>
          </Space>
        </div>

        {jobs.isLoading ? (
          <ListSkeleton rows={5} />
        ) : jobs.isError ? (
          <ErrorState error={jobs.error} onRetry={() => jobs.refetch()} />
        ) : (
          <Table<XxlJobInfo>
            rowKey="id"
            dataSource={jobs.data?.list ?? []}
            pagination={{
              current: page.current,
              pageSize: page.pageSize,
              total: jobs.data?.total ?? 0,
              showSizeChanger: false,
              showTotal: (t) => `共 ${t} 条`,
              onChange: (current, pageSize) => setPage({ current, pageSize }),
            }}
            columns={[
              {
                title: '任务',
                dataIndex: 'jobDesc',
                width: 260,
                render: (v: string, r) => (
                  <Space direction="vertical" size={2}>
                    <Space size={6}>
                      <Typography.Text strong>{v}</Typography.Text>
                      {r.alarmEmail && (
                        <Tooltip title={`报警邮件：${r.alarmEmail}`}>
                          <Tag color="volcano" style={{ marginInlineEnd: 0 }}>告警</Tag>
                        </Tooltip>
                      )}
                    </Space>
                    <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                      #{r.id} · {r.author || '未设置负责人'}
                    </Typography.Text>
                  </Space>
                ),
              },
              {
                title: '调度配置',
                dataIndex: 'scheduleConf',
                width: 200,
                render: (v: string, r) => (
                  <Space size={6} wrap>
                    <Tag color="geekblue" style={{ marginInlineEnd: 0 }}>
                      {SCHEDULE_TYPE[r.scheduleType] ?? r.scheduleType}
                    </Tag>
                    <Typography.Text code style={{ fontSize: 12 }}>
                      {v || '—'}
                    </Typography.Text>
                  </Space>
                ),
              },
              {
                title: 'JobHandler',
                dataIndex: 'executorHandler',
                width: 170,
                render: (v: string, r) => (
                  <Tooltip title={r.executorParam ? `参数：${r.executorParam}` : '无参数'}>
                    <Typography.Text code style={{ fontSize: 12 }}>
                      {v}
                    </Typography.Text>
                  </Tooltip>
                ),
              },
              {
                title: '上次触发',
                dataIndex: 'triggerLastTime',
                width: 110,
                render: (v: number) => (
                  <Tooltip title={fmtTime(v)}>
                    <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                      {fmtDuration(v)}
                    </Typography.Text>
                  </Tooltip>
                ),
              },
              {
                title: '下次触发',
                dataIndex: 'triggerNextTime',
                width: 170,
                render: (v: number) => (
                  <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                    {fmtTime(v)}
                  </Typography.Text>
                ),
              },
              {
                title: '运行',
                dataIndex: 'triggerStatus',
                width: 90,
                render: (v: number, r) => (
                  <Switch
                    size="small"
                    checked={v === 1}
                    checkedChildren="运行"
                    unCheckedChildren="停止"
                    loading={startMutation.isPending || stopMutation.isPending}
                    onChange={(checked) => toggle(r, checked)}
                  />
                ),
              },
              {
                title: '操作',
                width: 220,
                render: (_, r) => (
                  <Space size={4}>
                    <Button
                      size="small"
                      type="link"
                      icon={<CaretRightOutlined />}
                      loading={triggerMutation.isPending && triggerMutation.variables === r.id}
                      onClick={() => triggerMutation.mutate(r.id)}
                    >
                      执行
                    </Button>
                    <Button
                      size="small"
                      type="link"
                      icon={<EditOutlined />}
                      onClick={() => openEdit(r)}
                    >
                      编辑
                    </Button>
                    <Popconfirm
                      title="删除任务？"
                      description={`将永久删除「${r.jobDesc}」，不可恢复。`}
                      okText="删除"
                      okButtonProps={{ danger: true }}
                      onConfirm={() => removeMutation.mutate(r.id)}
                    >
                      <Button size="small" type="link" danger icon={<MinusCircleOutlined />}>
                        删除
                      </Button>
                    </Popconfirm>
                  </Space>
                ),
              },
            ]}
          />
        )}
      </div>

      <JobFormModal
        open={formOpen}
        editing={editing}
        form={form as FormInstance<JobFormValues>}
        confirmLoading={saveMutation.isPending}
        onOk={submitForm}
        onCancel={() => setFormOpen(false)}
      />
    </>
  );
}
