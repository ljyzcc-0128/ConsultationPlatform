import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Button,
  Input,
  Modal,
  Segmented,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import { CheckOutlined, CloseOutlined, TagsOutlined } from '@ant-design/icons';
import { api } from '../../api/client';
import { CATEGORY_DICT, REVIEW_STATUS_TEXT, type ReviewItem } from '../../api/types';
import { EmptyState } from '../../components/EmptyState';
import { ErrorState } from '../../components/ErrorState';
import { ListSkeleton } from '../../components/ListSkeleton';

function parseCategories(raw: string | null): string[] {
  if (!raw) return [];
  try {
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

const STATUS_FILTER = [
  { value: 'Pending', label: '待审核' },
  { value: 'NeedsReview', label: '需人工复核' },
  { value: 'Approved', label: '已通过' },
  { value: 'Rejected', label: '已驳回' },
  { value: 'ALL', label: '全部' },
];

export function ContentReviewPage() {
  const queryClient = useQueryClient();
  const [status, setStatus] = useState<string>('Pending');
  const [rejectTarget, setRejectTarget] = useState<ReviewItem | null>(null);
  const [rejectReason, setRejectReason] = useState('');
  const [categoryTarget, setCategoryTarget] = useState<ReviewItem | null>(null);
  const [categoryValue, setCategoryValue] = useState<string[]>([]);

  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['admin-reviews', status],
    queryFn: () =>
      api.get<never, ReviewItem[]>('/v1/admin/reviews', {
        params: status === 'ALL' ? {} : { status },
      }),
  });

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['admin-reviews'] });
    queryClient.invalidateQueries({ queryKey: ['admin-audit-logs'] });
  };

  const approveMutation = useMutation({
    mutationFn: (articleId: string) =>
      api.post(`/v1/admin/reviews/${articleId}/approve`, {}),
    onSuccess: () => {
      message.success('已通过');
      invalidate();
    },
  });

  const rejectMutation = useMutation({
    mutationFn: ({ articleId, reason }: { articleId: string; reason: string }) =>
      api.post(`/v1/admin/reviews/${articleId}/reject`, { reason }),
    onSuccess: () => {
      message.success('已驳回');
      setRejectTarget(null);
      setRejectReason('');
      invalidate();
    },
  });

  const categoryMutation = useMutation({
    mutationFn: ({ articleId, categories }: { articleId: string; categories: string[] }) =>
      api.put(`/v1/admin/reviews/${articleId}/category`, categories),
    onSuccess: () => {
      message.success('分类已修正');
      setCategoryTarget(null);
      invalidate();
    },
  });

  const items = useMemo(() => data ?? [], [data]);

  return (
    <>
      <div className="page-head">
        <div>
          <h1>内容质量处理</h1>
          <div className="sub">
            AI 加工后的内容进入人工审核队列；审核结论（含驳回原因）将作为 AI 优化的反馈样本。
          </div>
        </div>
        <div className="head-actions">
          <Segmented
            value={status}
            onChange={(v) => setStatus(v as string)}
            options={STATUS_FILTER}
          />
        </div>
      </div>

      <div className="admin-table-wrap">
        {isLoading ? (
          <ListSkeleton rows={5} />
        ) : isError ? (
          <ErrorState error={error} onRetry={() => refetch()} />
        ) : (
          <Table<ReviewItem>
            rowKey="articleId"
            dataSource={items}
            pagination={{ pageSize: 10, showTotal: (t) => `共 ${t} 条` }}
            locale={{
              emptyText: (
                <EmptyState
                  title="队列为空"
                  description={
                    status === 'Pending'
                      ? '当前没有待审核内容，新采集的内容经 AI 加工后会自动进入该队列。'
                      : '当前筛选状态下没有记录，可切换状态查看。'
                  }
                />
              ),
            }}
            columns={[
              { title: '标题', dataIndex: 'title', ellipsis: true },
              { title: '来源', dataIndex: 'sourceName', width: 150 },
              { title: '发布日期', dataIndex: 'publishDate', width: 110 },
              {
                title: '分类',
                dataIndex: 'category',
                width: 220,
                render: (raw: string | null, record) => (
                  <Space size={4} wrap>
                    {parseCategories(raw).map((c) => (
                      <Tag key={c} style={{ fontSize: 11 }}>{c}</Tag>
                    ))}
                    <Button
                      type="link"
                      size="small"
                      icon={<TagsOutlined />}
                      onClick={() => {
                        setCategoryTarget(record);
                        setCategoryValue(parseCategories(raw));
                      }}
                    >
                      修正
                    </Button>
                  </Space>
                ),
              },
              {
                title: '状态',
                dataIndex: 'reviewStatus',
                width: 100,
                render: (v: string) => {
                  const colorMap: Record<string, string> = {
                    Pending: 'default',
                    NeedsReview: 'orange',
                    Approved: 'green',
                    Rejected: 'red',
                  };
                  return <Tag color={colorMap[v]}>{REVIEW_STATUS_TEXT[v] ?? v}</Tag>;
                },
              },
              {
                title: '操作',
                width: 180,
                render: (_, record) =>
                  record.reviewStatus === 'Approved' || record.reviewStatus === 'Rejected' ? (
                    record.reviewComment ? (
                      <Typography.Text type="secondary" style={{ fontSize: 12 }} ellipsis={{ tooltip: record.reviewComment }}>
                        {record.reviewComment}
                      </Typography.Text>
                    ) : (
                      <Typography.Text type="secondary" style={{ fontSize: 12 }}>已完成</Typography.Text>
                    )
                  ) : (
                    <Space>
                      <Button
                        size="small"
                        type="primary"
                        icon={<CheckOutlined />}
                        loading={approveMutation.isPending && approveMutation.variables === record.articleId}
                        onClick={() => approveMutation.mutate(record.articleId)}
                      >
                        通过
                      </Button>
                      <Button
                        size="small"
                        danger
                        icon={<CloseOutlined />}
                        onClick={() => setRejectTarget(record)}
                      >
                        驳回
                      </Button>
                    </Space>
                  ),
              },
            ]}
            expandable={{
              expandedRowRender: (record) => (
                <Space direction="vertical" size={4}>
                  <Space size={8}>
                    <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                      以下摘要由 AI 生成，审核时请对照原文判断准确性
                    </Typography.Text>
                  </Space>
                  <Typography.Paragraph style={{ whiteSpace: 'pre-line', marginBottom: 0 }}>
                    {record.summary ?? '（无摘要）'}
                  </Typography.Paragraph>
                  <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                    更新时间：{record.updatedAt.replace('T', ' ')}
                  </Typography.Text>
                </Space>
              ),
            }}
          />
        )}

        {/* 驳回弹窗：原因必填 */}
        <Modal
          title="驳回内容"
          open={Boolean(rejectTarget)}
          onCancel={() => {
            setRejectTarget(null);
            setRejectReason('');
          }}
          onOk={() => {
            if (!rejectReason.trim()) {
              message.warning('请填写驳回原因（将作为 AI 优化反馈）');
              return;
            }
            if (rejectTarget) {
              rejectMutation.mutate({ articleId: rejectTarget.articleId, reason: rejectReason.trim() });
            }
          }}
          confirmLoading={rejectMutation.isPending}
          okText="确认驳回"
          okButtonProps={{ danger: true }}
        >
          <Typography.Paragraph type="secondary">{rejectTarget?.title}</Typography.Paragraph>
          <Input.TextArea
            rows={3}
            placeholder="驳回原因（必填），如：分类错误 / 摘要与原文不符 / 重复内容…"
            value={rejectReason}
            onChange={(e) => setRejectReason(e.target.value)}
          />
        </Modal>

        {/* 分类修正弹窗 */}
        <Modal
          title="修正分类"
          open={Boolean(categoryTarget)}
          onCancel={() => setCategoryTarget(null)}
          onOk={() => {
            if (categoryTarget) {
              categoryMutation.mutate({ articleId: categoryTarget.articleId, categories: categoryValue });
            }
          }}
          confirmLoading={categoryMutation.isPending}
        >
          <Typography.Paragraph type="secondary">{categoryTarget?.title}</Typography.Paragraph>
          <Select
            mode="multiple"
            style={{ width: '100%' }}
            placeholder="选择正确的内容域分类"
            value={categoryValue}
            onChange={setCategoryValue}
            options={[...CATEGORY_DICT, 'Battery Energy Storage'].map((c) => ({ value: c, label: c }))}
          />
        </Modal>
      </div>
    </>
  );
}
