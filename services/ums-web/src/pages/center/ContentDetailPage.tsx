import { Link, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import {
  Alert,
  Button,
  Card,
  Divider,
  Space,
  Spin,
  Tag,
  Timeline,
  Tooltip,
  Typography,
} from 'antd';
import {
  ArrowLeftOutlined,
  ExportOutlined,
  ClockCircleOutlined,
} from '@ant-design/icons';
import { api } from '../../api/client';
import type { NewsDetail } from '../../api/types';
import { EVENT_TYPE_TEXT, RELEVANCE_TEXT } from '../../api/types';
import type { RelatedContent, TimelineEvent } from '../../api/types';
import { AiTag } from '../../components/AiTag';
import { EmptyState } from '../../components/EmptyState';
import { ErrorState } from '../../components/ErrorState';

export function ContentDetailPage() {
  const { contentId } = useParams<{ contentId: string }>();

  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['content', contentId],
    queryFn: () => api.get<never, NewsDetail>(`/news/${contentId}`),
    enabled: Boolean(contentId),
    staleTime: 60_000,
  });

  const { data: related } = useQuery({
    queryKey: ['content-related', contentId],
    queryFn: () =>
      api.get<never, { items: RelatedContent[] }>(`/v1/contents/${contentId}/related`),
    enabled: Boolean(contentId),
    staleTime: 5 * 60_000,
  });

  const { data: timeline } = useQuery({
    queryKey: ['content-timeline', contentId],
    queryFn: () =>
      api.get<never, { events: TimelineEvent[] }>(`/v1/contents/${contentId}/timeline`),
    enabled: Boolean(contentId),
    staleTime: 5 * 60_000,
  });

  if (isLoading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', padding: 96 }}>
        <Spin size="large" tip="加载中…">
          <div style={{ minHeight: 120, minWidth: 240 }} />
        </Spin>
      </div>
    );
  }

  if (isError) {
    return <ErrorState error={error} onRetry={() => refetch()} />;
  }

  if (!data) {
    return (
      <EmptyState
        title="内容不存在"
        description="该内容可能已被删除或链接有误，可返回情报中心重新浏览。"
      />
    );
  }

  const hasOriginal = Boolean(data.originalUrl);
  // 事件时间线按 detectedAt 升序（IC-006）
  const events = [...(timeline?.events ?? [])].sort((a, b) =>
    a.detectedAt.localeCompare(b.detectedAt),
  );

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Space>
        <Link to="/center">
          <Button icon={<ArrowLeftOutlined />}>返回列表</Button>
        </Link>
      </Space>

      <Card>
        <Space direction="vertical" size={12} style={{ width: '100%' }}>
          <Typography.Title level={4} style={{ marginBottom: 0 }}>
            {data.title}
          </Typography.Title>
          <Space size={12} wrap>
            <Tag>{data.sourceName}</Tag>
            <Typography.Text type="secondary">
              {data.publishTime ? data.publishTime.replace('T', ' ') : data.publishDate}
            </Typography.Text>
            {data.author && (
              <Typography.Text type="secondary">作者：{data.author}</Typography.Text>
            )}
            <Tag>{data.language === 'en' ? 'English' : '中文'}</Tag>
            {data.manualReviewStatus === 'Approved' && <Tag color="green">人工审核通过</Tag>}
            {data.duplicateGroupId && <Tag color="orange">疑似重复</Tag>}
          </Space>
          {data.category.length > 0 && (
            <Space size={6} wrap>
              {data.category.map((c) => (
                <Tag key={c} color="blue">
                  {c}
                </Tag>
              ))}
            </Space>
          )}

          {/* IC-005：原文入口。originalUrl 可能为空（采集失败场景），必须禁用态 + 提示 */}
          {hasOriginal ? (
            <Button
              type="primary"
              icon={<ExportOutlined />}
              href={data.originalUrl ?? undefined}
              target="_blank"
              rel="noopener noreferrer"
            >
              查看原文
            </Button>
          ) : (
            <Tooltip title="原文链接在采集时缺失或已失效，无法跳转">
              <Button type="primary" icon={<ExportOutlined />} disabled>
                查看原文（不可用）
              </Button>
            </Tooltip>
          )}
        </Space>
      </Card>

      {/* CNT-003：AI 摘要与原文清晰视觉分隔，AI 字段带统一标记 */}
      {data.summary && (
        <Card
          title={
            <Space size={8}>
              <span>三段式摘要</span>
              <AiTag />
            </Space>
          }
          style={{ background: '#f9f5ff', borderColor: '#d3adf7' }}
        >
          <Typography.Paragraph
            style={{ whiteSpace: 'pre-line', marginBottom: 0 }}
            type="secondary"
          >
            {data.summary}
          </Typography.Paragraph>
          <Alert
            style={{ marginTop: 12 }}
            type="info"
            showIcon
            message="以上摘要由 AI 自动生成，可能与原文存在偏差，重要决策请以原文为准。"
          />
        </Card>
      )}

      <Divider orientation="left" plain>
        <Space>
          <ClockCircleOutlined />
          原文内容
        </Space>
      </Divider>
      <Card>
        {data.body ? (
          <Typography.Paragraph
            style={{ whiteSpace: 'pre-line', marginBottom: 0, lineHeight: 1.9 }}
          >
            {data.body}
          </Typography.Paragraph>
        ) : (
          <EmptyState description="该内容未采集到正文，可尝试通过原文链接查看。" />
        )}
      </Card>

      {/* 相关内容 */}
      <Card title="相关内容">
        {(related?.items ?? []).length === 0 ? (
          <EmptyState description="暂无相关内容推荐。" />
        ) : (
          <Space direction="vertical" size={12} style={{ width: '100%' }}>
            {(related?.items ?? []).map((r) => (
              <Link key={r.articleId} to={`/center/${r.articleId}`}>
                <Card size="small" hoverable>
                  <Space direction="vertical" size={4} style={{ width: '100%' }}>
                    <Typography.Text strong>{r.title}</Typography.Text>
                    <Space size={8}>
                      <Tag color={r.relevance === 'HIGH' ? 'orange' : 'default'}>
                        相关度{RELEVANCE_TEXT[r.relevance]}
                      </Tag>
                      <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                        {r.sourceName} · {r.publishDate}
                      </Typography.Text>
                    </Space>
                  </Space>
                </Card>
              </Link>
            ))}
          </Space>
        )}
      </Card>

      {/* 事件时间线 */}
      <Card title="事件时间线">
        {events.length === 0 ? (
          <EmptyState description="该内容暂未纳入事件跟踪。" />
        ) : (
          <Timeline
            items={events.map((e) => ({
              children: (
                <Space direction="vertical" size={2}>
                  <Space size={8}>
                    <Tag>{EVENT_TYPE_TEXT[e.eventType] ?? e.eventType}</Tag>
                    <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                      {e.detectedAt}
                    </Typography.Text>
                  </Space>
                  <Typography.Text>{e.description}</Typography.Text>
                </Space>
              ),
            }))}
          />
        )}
      </Card>
    </Space>
  );
}
