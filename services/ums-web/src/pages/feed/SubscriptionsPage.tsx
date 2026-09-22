import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Button,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  message,
} from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { api } from '../../api/client';
import { EmptyState } from '../../components/EmptyState';
import { ErrorState } from '../../components/ErrorState';
import {
  type Subscription,
  type SubscriptionType,
  type Frequency,
  type Channel,
  SUBSCRIPTION_TYPE_TEXT,
  FREQUENCY_TEXT,
  CHANNEL_TEXT,
} from '../../api/types';

const TYPE_OPTIONS: SubscriptionType[] = [
  'CONTENT_DOMAIN',
  'THEME',
  'COMPANY',
  'KEYWORD',
  'INDICATOR',
  'EVENT',
];
const FREQUENCY_OPTIONS: Frequency[] = ['INSTANT', 'DAILY', 'WEEKLY'];
const CHANNEL_OPTIONS: Channel[] = ['IN_APP', 'EMAIL', 'WECHAT'];

interface SubscriptionFormValues {
  type: SubscriptionType;
  value: string;
  frequency: Frequency;
  channel: Channel;
}

export function SubscriptionsPage() {
  const queryClient = useQueryClient();
  const [modalOpen, setModalOpen] = useState(false);
  const [form] = Form.useForm<SubscriptionFormValues>();

  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['subscriptions'],
    queryFn: () => api.get<never, Subscription[]>('/me/subscriptions'),
  });

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['subscriptions'] });

  const addMutation = useMutation({
    mutationFn: (values: SubscriptionFormValues) =>
      api.post('/me/subscriptions', { ...values, enabled: true }),
    onSuccess: () => {
      message.success('关注规则已添加');
      setModalOpen(false);
      form.resetFields();
      invalidate();
    },
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, ...body }: Partial<Subscription> & { id: string }) =>
      api.put(`/me/subscriptions/${id}`, body),
    onSuccess: invalidate,
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => api.delete(`/me/subscriptions/${id}`),
    onSuccess: () => {
      message.success('已删除');
      invalidate();
    },
  });

  const list = data ?? [];

  return (
    <>
      <div className="page-head">
        <div>
          <h1>我的关注</h1>
          <div className="sub">
            关注规则决定「我的情报」聚合哪些内容，以及推送的频率（实时 / 日报 / 周报）与渠道。
          </div>
        </div>
        <div className="head-actions">
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setModalOpen(true)}>
            新增关注
          </Button>
        </div>
      </div>

      {isError ? (
        <ErrorState error={error} onRetry={() => refetch()} />
      ) : (
        <Table<Subscription>
          rowKey="id"
          loading={isLoading}
          dataSource={list}
          pagination={{ pageSize: 10, showTotal: (t) => `共 ${t} 条` }}
          locale={{
            emptyText: (
              <EmptyState
                description="您还没有设置任何关注规则，「我的情报」将无法为您聚合个性化内容。"
                actionText="新增第一条关注"
                onAction={() => setModalOpen(true)}
              />
            ),
          }}
          columns={[
            {
              title: '类型',
              dataIndex: 'type',
              width: 100,
              render: (t: SubscriptionType) => <Tag>{SUBSCRIPTION_TYPE_TEXT[t]}</Tag>,
            },
            { title: '关注对象', dataIndex: 'value' },
            {
              title: '推送频率',
              dataIndex: 'frequency',
              width: 110,
              render: (f: Frequency) => FREQUENCY_TEXT[f],
            },
            {
              title: '推送渠道',
              dataIndex: 'channel',
              width: 110,
              render: (c: Channel) => CHANNEL_TEXT[c],
            },
            {
              title: '启用',
              dataIndex: 'enabled',
              width: 90,
              render: (enabled: boolean, record) => (
                <Switch
                  checked={enabled}
                  onChange={(val) => {
                    updateMutation.mutate({ id: record.id, enabled: val });
                    if (!val) message.info('停用后该规则不再参与聚合与推送');
                  }}
                />
              ),
            },
            {
              title: '操作',
              width: 120,
              render: (_, record) => (
                <Space>
                  <Select
                    size="small"
                    variant="borderless"
                    value={record.frequency}
                    style={{ width: 84 }}
                    options={FREQUENCY_OPTIONS.map((f) => ({
                      value: f,
                      label: FREQUENCY_TEXT[f],
                    }))}
                    onChange={(frequency: Frequency) =>
                      updateMutation.mutate({ id: record.id, frequency })
                    }
                  />
                  <Popconfirm
                    title="确定删除该关注规则？"
                    description="删除后相关内容将不再进入「我的情报」。"
                    onConfirm={() => deleteMutation.mutate(record.id)}
                  >
                    <Button size="small" danger>
                      删除
                    </Button>
                  </Popconfirm>
                </Space>
              ),
            },
          ]}
        />
      )}

      <Modal
        title="新增关注"
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={addMutation.isPending}
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={(values) => addMutation.mutate(values)}
          initialValues={{ type: 'KEYWORD', frequency: 'DAILY', channel: 'IN_APP' }}
        >
          <Form.Item name="type" label="关注类型" rules={[{ required: true }]}>
            <Select
              options={TYPE_OPTIONS.map((t) => ({
                value: t,
                label: SUBSCRIPTION_TYPE_TEXT[t],
              }))}
            />
          </Form.Item>
          <Form.Item
            name="value"
            label="关注对象"
            rules={[{ required: true, message: '请输入关注对象' }]}
          >
            <Input placeholder="如：钠离子电池 / 宁德时代 / 碳酸锂现货价" />
          </Form.Item>
          <Space size={16} style={{ display: 'flex' }}>
            <Form.Item name="frequency" label="推送频率" style={{ minWidth: 160 }}>
              <Select
                options={FREQUENCY_OPTIONS.map((f) => ({
                  value: f,
                  label: FREQUENCY_TEXT[f],
                }))}
              />
            </Form.Item>
            <Form.Item name="channel" label="推送渠道" style={{ minWidth: 160 }}>
              <Select
                options={CHANNEL_OPTIONS.map((c) => ({
                  value: c,
                  label: CHANNEL_TEXT[c],
                }))}
              />
            </Form.Item>
          </Space>
        </Form>
      </Modal>
    </>
  );
}
