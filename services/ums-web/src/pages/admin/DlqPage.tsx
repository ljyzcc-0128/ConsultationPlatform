import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Badge,
  Button,
  Card,
  Descriptions,
  Drawer,
  Modal,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import { api } from '../../api/client';
import type { DlqMessage, DlqQueue } from '../../api/types';
import { EmptyState } from '../../components/EmptyState';
import { ErrorState } from '../../components/ErrorState';
import { ListSkeleton } from '../../components/ListSkeleton';

/** RabbitMQ properties.timestamp 为 epoch 秒 */
function fmtTimestamp(v: unknown): string {
  const n = typeof v === 'number' ? v : Number(v);
  if (!Number.isFinite(n) || n <= 0) return '—';
  return new Date(n * 1000).toLocaleString();
}

/** properties 中值得逐条展示的字段（其余字段统一收进 JSON） */
const PROPERTY_FIELDS: [string, string, (v: unknown) => string][] = [
  ['message_id', '消息ID', (v) => String(v ?? '—')],
  ['timestamp', '发布时间', fmtTimestamp],
  ['content_type', '内容类型', (v) => String(v ?? '—')],
  ['type', '类型', (v) => String(v ?? '—')],
  ['correlation_id', '关联ID', (v) => String(v ?? '—')],
  ['priority', '优先级', (v) => (v === undefined || v === 0 ? '0' : String(v))],
  ['delivery_mode', '投递模式', (v) => (v === 2 ? '2（持久化）' : String(v ?? '—'))],
  ['app_id', '应用', (v) => String(v ?? '—')],
];

function prettyJson(value: unknown): string {
  if (value === undefined || value === null || (typeof value === 'object' && Object.keys(value as object).length === 0)) {
    return '';
  }
  try {
    return JSON.stringify(value, null, 2);
  } catch {
    return String(value);
  }
}

/** 单条死信消息详情：元信息 + 消息头 + 报文 */
function MessageDetail({ msg }: { msg: DlqMessage }) {
  const headersJson = prettyJson(msg.headers);
  return (
    <Card size="small">
      <Space direction="vertical" size={12} style={{ width: '100%' }}>
        <Descriptions
          size="small"
          column={2}
          bordered
          items={[
            { key: 'exchange', label: '交换机', children: msg.exchange || '（默认）' },
            { key: 'routing', label: 'Routing Key', children: <Tag color="blue">{msg.routingKey}</Tag> },
            {
              key: 'redelivered',
              label: '曾重投',
              children: msg.redelivered ? <Tag color="orange">是</Tag> : <Tag>否</Tag>,
            },
            ...PROPERTY_FIELDS.map(([key, label, fmt]) => ({
              key,
              label,
              children: fmt(msg.properties?.[key]),
            })),
          ]}
        />
        {headersJson && (
          <>
            <Typography.Text type="secondary">消息头（headers）</Typography.Text>
            <pre style={{ margin: 0, fontSize: 12, whiteSpace: 'pre-wrap', wordBreak: 'break-all' }}>
              {headersJson}
            </pre>
          </>
        )}
        <Typography.Text type="secondary">报文（payload）</Typography.Text>
        <pre style={{ margin: 0, fontSize: 12, whiteSpace: 'pre-wrap', wordBreak: 'break-all' }}>
          {(() => {
            try {
              return JSON.stringify(JSON.parse(msg.payload), null, 2);
            } catch {
              return msg.payload;
            }
          })()}
        </pre>
      </Space>
    </Card>
  );
}

export function DlqPage() {
  const queryClient = useQueryClient();
  const [viewing, setViewing] = useState<DlqQueue | null>(null);

  const queues = useQuery({
    queryKey: ['admin-dlq'],
    queryFn: () => api.get<never, DlqQueue[]>('/v1/admin/dlq'),
  });

  const messages = useQuery({
    queryKey: ['admin-dlq-messages', viewing?.queue],
    queryFn: () =>
      api.get<never, DlqMessage[]>(`/v1/admin/dlq/${viewing!.queue}/messages`, {
        params: { count: 20 },
      }),
    enabled: Boolean(viewing),
  });

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['admin-dlq'] });

  const requeueMutation = useMutation({
    mutationFn: (queue: string) => api.post(`/v1/admin/dlq/${queue}/requeue`),
    onSuccess: () => {
      message.success('死信消息已重新入队');
      invalidate();
      queryClient.invalidateQueries({ queryKey: ['admin-dlq-messages'] });
    },
  });

  const purgeMutation = useMutation({
    mutationFn: (queue: string) => api.delete(`/v1/admin/dlq/${queue}`),
    onSuccess: () => {
      message.success('死信队列已清空');
      setViewing(null);
      invalidate();
    },
  });

  return (
    <>
      <div className="page-head">
        <div>
          <h1>死信队列（DLQ）</h1>
          <div className="sub">
            消费失败的消息会进入死信队列；处理后可重新入队（requeue），确认无价值可清空。
          </div>
        </div>
      </div>

      <div className="admin-table-wrap">
        {queues.isLoading ? (
          <ListSkeleton rows={3} />
        ) : queues.isError ? (
          <ErrorState error={queues.error} onRetry={() => queues.refetch()} />
        ) : (
          <Table<DlqQueue>
            rowKey="queue"
            dataSource={queues.data ?? []}
            pagination={false}
            locale={{
              emptyText: <EmptyState title="死信队列为空" description="当前没有消费失败的消息，链路运行正常。" />,
            }}
            columns={[
              { title: '队列', dataIndex: 'queue' },
              {
                title: '积压消息数',
                dataIndex: 'messages',
                width: 130,
                render: (v: number) => (
                  <Badge count={v} showZero style={{ backgroundColor: v > 0 ? '#fa8c16' : '#52c41a' }} />
                ),
              },
              {
                title: '操作',
                width: 240,
                render: (_, record) => (
                  <Space>
                    <Button size="small" onClick={() => setViewing(record)}>
                      查看消息
                    </Button>
                    <Button
                      size="small"
                      icon={<ReloadOutlined />}
                      disabled={record.messages === 0}
                      onClick={() => requeueMutation.mutate(record.queue)}
                    >
                      重新入队
                    </Button>
                    <Button
                      size="small"
                      danger
                      disabled={record.messages === 0}
                      onClick={() => {
                        Modal.confirm({
                          title: '清空死信队列？',
                          content: `将删除队列 ${record.queue} 中的全部 ${record.messages} 条消息，操作不可恢复。`,
                          okText: '确认清空',
                          okButtonProps: { danger: true },
                          onOk: () => purgeMutation.mutate(record.queue),
                        });
                      }}
                    >
                      清空
                    </Button>
                  </Space>
                ),
              },
            ]}
          />
        )}

        <Drawer
          title={`死信消息：${viewing?.queue ?? ''}`}
          width={640}
          open={Boolean(viewing)}
          onClose={() => setViewing(null)}
        >
          {messages.isLoading ? (
            <ListSkeleton rows={4} />
          ) : messages.isError ? (
            <ErrorState error={messages.error} onRetry={() => messages.refetch()} />
          ) : (
            <Space direction="vertical" size={12} style={{ width: '100%' }}>
              {(messages.data ?? []).map((msg, i) => (
                <MessageDetail key={i} msg={msg} />
              ))}
              {(messages.data ?? []).length === 0 && (
                <EmptyState title="没有消息" description="该队列当前没有死信消息。" />
              )}
            </Space>
          )}
        </Drawer>
      </div>
    </>
  );
}
