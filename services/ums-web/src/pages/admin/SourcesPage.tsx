import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Badge,
  Button,
  Card,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import { PlusOutlined, PlayCircleOutlined, ReloadOutlined } from '@ant-design/icons';
import { api } from '../../api/client';
import type { Source, SourceUpdateBody, SourceCreateBody } from '../../api/types';
import { ErrorState } from '../../components/ErrorState';
import { ListSkeleton } from '../../components/ListSkeleton';
import { useCrudTable } from '../../hooks/useCrudTable';

const FREQUENCY_OPTIONS = ['1/day', '1/12h', '1/6h', '1/hour'].map((f) => ({
  value: f,
  label: f,
}));

const PRIORITY_OPTIONS = ['P0', 'P1', 'P2'].map((p) => ({ value: p, label: p }));

const LANGUAGE_OPTIONS = [
  { value: 'zh-CN', label: '中文' },
  { value: 'en', label: 'English' },
];

const CRAWL_STATUS_TEXT: Record<string, { text: string; status: 'default' | 'processing' | 'success' | 'error' | 'warning' }> = {
  NotRun: { text: '未运行', status: 'default' },
  Running: { text: '运行中', status: 'processing' },
  Success: { text: '成功', status: 'success' },
  Failed: { text: '失败', status: 'error' },
};

interface SourceFormValues extends SourceUpdateBody {
  sourceName: string;
}

interface CreateFormValues extends SourceCreateBody { }

export function SourcesPage() {
  const queryClient = useQueryClient();
  const [editing, setEditing] = useState<Source | null>(null);
  const [creating, setCreating] = useState(false);
  const [form] = Form.useForm<SourceFormValues>();
  const [createForm] = Form.useForm<CreateFormValues>();

  const table = useCrudTable<Source>({
    queryKey: ['admin-sources'],
    fetcher: () => api.get<never, Source[]>('/v1/admin/sources'),
  });

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['admin-sources'] });

  const updateMutation = useMutation({
    mutationFn: ({ id, body }: { id: string; body: SourceUpdateBody }) =>
      api.put(`/v1/admin/sources/${id}`, body),
    onSuccess: () => {
      message.success('信源配置已保存');
      setEditing(null);
      invalidate();
    },
    onError: () => message.error('保存失败，请重试'),
  });

  const createMutation = useMutation({
    mutationFn: (body: SourceCreateBody) => api.post('/v1/admin/sources', body),
    onSuccess: () => {
      message.success('信源已创建');
      setCreating(false);
      createForm.resetFields();
      invalidate();
    },
    onError: () => message.error('创建失败，请检查信源ID是否已存在'),
  });

  const triggerMutation = useMutation({
    mutationFn: (id: string) => api.post(`/v1/admin/sources/${id}/trigger?limit=10`),
    onSuccess: (res) => {
      const summary = (res as { fetched?: number; published?: number }) ?? {};
      message.success(`触发成功：抓取 ${summary.fetched ?? '-'} 条 / 入库 ${summary.published ?? '-'} 条`);
      invalidate();
    },
    onError: () =>
      message.error('触发失败：采集服务未启动或该信源当前不可抓取（详见任务记录）'),
  });

  const openEdit = (record: Source) => {
    setEditing(record);
    form.setFieldsValue({
      sourceName: record.sourceName,
      crawlEnabled: record.crawlEnabled,
      crawlFrequency: record.crawlFrequency,
      priority: record.priority,
      remark: record.remark ?? '',
    });
  };

  const openCreate = () => {
    createForm.setFieldsValue({
      language: 'zh-CN',
      crawlEnabled: true,
      crawlFrequency: '1/day',
      priority: 'P1',
      pagination: true,
      fetchDetail: true,
      dateFilter: true,
    });
    setCreating(true);
  };

  return (
    <Card
      title="信源管理"
      extra={
        <Space>
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            新增信源
          </Button>
          <Input.Search
            placeholder="按信源名称筛选"
            allowClear
            style={{ width: 200 }}
            onSearch={(v) => table.setFilter('sourceName', v)}
          />
          <Button icon={<ReloadOutlined />} onClick={() => table.reload()}>
            刷新
          </Button>
        </Space>
      }
    >
      {table.query.isError ? (
        <ErrorState error={table.query.error} onRetry={() => table.reload()} />
      ) : table.query.isLoading ? (
        <ListSkeleton rows={5} />
      ) : (
        <Table<Source>
          rowKey="sourceId"
          dataSource={table.data}
          pagination={table.pagination}
          columns={[
            { title: '信源ID', dataIndex: 'sourceId', width: 90 },
            { title: '名称', dataIndex: 'sourceName', width: 180 },
            { title: '地区', dataIndex: 'region', width: 80 },
            {
              title: '抓取开关',
              dataIndex: 'crawlEnabled',
              width: 100,
              render: (v: boolean, record) => (
                <Switch
                  checked={v}
                  size="small"
                  onChange={(crawlEnabled) =>
                    updateMutation.mutate({ id: record.sourceId, body: { crawlEnabled } })
                  }
                />
              ),
            },
            { title: '频率', dataIndex: 'crawlFrequency', width: 90 },
            { title: '优先级', dataIndex: 'priority', width: 80 },
            {
              title: '最近抓取',
              dataIndex: 'lastCrawlTime',
              width: 160,
              render: (v: string | null) => v ?? <Typography.Text type="secondary">未运行</Typography.Text>,
            },
            {
              title: '状态',
              dataIndex: 'crawlStatus',
              width: 90,
              render: (v: string) => {
                const meta = CRAWL_STATUS_TEXT[v] ?? { text: v, status: 'default' as const };
                return <Badge status={meta.status} text={meta.text} />;
              },
            },
            {
              title: '操作',
              width: 200,
              render: (_, record) => (
                <Space>
                  <Button size="small" onClick={() => openEdit(record)}>
                    编辑
                  </Button>
                  <Popconfirm
                    title="手动触发一次抓取？"
                    description="将调用 Python 采集服务抓取该信源最新内容。"
                    onConfirm={() => triggerMutation.mutate(record.sourceId)}
                  >
                    <Button
                      size="small"
                      icon={<PlayCircleOutlined />}
                      loading={triggerMutation.isPending && triggerMutation.variables === record.sourceId}
                    >
                      触发抓取
                    </Button>
                  </Popconfirm>
                </Space>
              ),
            },
          ]}
          expandable={{
            expandedRowRender: (record) => (
              <Space direction="vertical" size={2}>
                <Typography.Text type="secondary">
                  入口：{record.entryUrl} · 语言：{record.language} · 类型：{record.sourceType}
                </Typography.Text>
                <Space size={6} wrap>
                  {(() => {
                    try {
                      const cats = record.category ? (JSON.parse(record.category) as string[]) : [];
                      return cats.map((c) => <Tag key={c}>{c}</Tag>);
                    } catch {
                      return record.category ? <Tag>{record.category}</Tag> : null;
                    }
                  })()}
                </Space>
                {record.remark && (
                  <Typography.Text type="secondary">备注：{record.remark}</Typography.Text>
                )}
              </Space>
            ),
          }}
        />
      )}

      <Modal
        title={`编辑信源：${editing?.sourceId ?? ''}`}
        open={Boolean(editing)}
        onCancel={() => setEditing(null)}
        onOk={() => form.submit()}
        confirmLoading={updateMutation.isPending}
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={(values) => {
            if (!editing) return;
            const body: SourceUpdateBody = {
              sourceName: values.sourceName,
              crawlEnabled: values.crawlEnabled,
              crawlFrequency: values.crawlFrequency,
              priority: values.priority,
              remark: values.remark ?? undefined,
            };
            updateMutation.mutate({ id: editing.sourceId, body });
          }}
        >
          <Form.Item name="sourceName" label="信源名称" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="crawlFrequency" label="抓取频率">
            <Select options={FREQUENCY_OPTIONS} />
          </Form.Item>
          <Form.Item name="priority" label="优先级">
            <Select options={PRIORITY_OPTIONS} />
          </Form.Item>
          <Form.Item name="crawlEnabled" label="启用定时抓取" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="新增信源"
        open={creating}
        onCancel={() => setCreating(false)}
        onOk={() => createForm.submit()}
        confirmLoading={createMutation.isPending}
        width={600}
      >
        <Form
          form={createForm}
          layout="vertical"
          onFinish={(values) => createMutation.mutate(values)}
        >
          <Form.Item name="sourceId" label="信源ID" rules={[{ required: true, message: '请输入信源ID' }]}>
            <Input placeholder="如 CN-03" />
          </Form.Item>
          <Form.Item name="sourceName" label="信源名称" rules={[{ required: true, message: '请输入信源名称' }]}>
            <Input placeholder="如 中国储能网" />
          </Form.Item>
          <Form.Item name="entryUrl" label="入口URL" rules={[{ required: true, message: '请输入入口URL' }]}>
            <Input placeholder="https://..." />
          </Form.Item>
          <Form.Item name="region" label="覆盖区域">
            <Input placeholder="如 中国 / 海外" />
          </Form.Item>
          <Form.Item name="sourceType" label="来源类型">
            <Input placeholder="如 行业媒体 / 政策聚合" />
          </Form.Item>
          <Form.Item name="language" label="默认语言" rules={[{ required: true }]}>
            <Select options={LANGUAGE_OPTIONS} />
          </Form.Item>
          <Form.Item name="crawlFrequency" label="抓取频率">
            <Select options={FREQUENCY_OPTIONS} />
          </Form.Item>
          <Form.Item name="priority" label="优先级">
            <Select options={PRIORITY_OPTIONS} />
          </Form.Item>
          <Form.Item name="crawlEnabled" label="启用定时抓取" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item name="category" label="主题标签（JSON 数组）">
            <Input placeholder='如 ["BESS","Policy"]' />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
}
