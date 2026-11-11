import { Link, useParams } from 'react-router-dom';

import { useQuery } from '@tanstack/react-query';
import {
  Alert,
  Button,
  Spin,
  Tag,
  Tooltip,
} from 'antd';
import {
  ArrowLeftOutlined,
  ExportOutlined,
} from '@ant-design/icons';
import { api } from '../../api/client';
import type { NewsDetail } from '../../api/types';
import { EVENT_TYPE_TEXT, RELEVANCE_TEXT } from '../../api/types';
import type { RelatedContent, TimelineEvent } from '../../api/types';
import { EmptyState } from '../../components/EmptyState';
import { ErrorState } from '../../components/ErrorState';

/** 政策状态中文展示与配色（DRAFT/PUBLISHED/IN_FORCE/AMENDED/NON_POLICY） */
const POLICY_STATUS_TEXT: Record<string, string> = {
  DRAFT: '征求意见/草案',
  PUBLISHED: '已发布',
  IN_FORCE: '已生效',
  AMENDED: '修订',
  NON_POLICY: '非政策资讯',
};
const POLICY_STATUS_COLOR: Record<string, string> = {
  DRAFT: 'gold',
  PUBLISHED: 'blue',
  IN_FORCE: 'green',
  AMENDED: 'purple',
  NON_POLICY: 'default',
};
/** 相关性配色（HIGH/MEDIUM/LOW），key 转宽松索引避免与 RelatedContent.relevance 的窄类型冲突 */
const RELEVANCE_COLOR: Record<string, string> = {
  HIGH: 'orange',
  MEDIUM: 'blue',
  LOW: 'default',
};
const RELEVANCE_LABEL: Record<string, string> = { ...RELEVANCE_TEXT };

/** 发布机构 JSON 数组文本解析（["国家能源局","国家发改委"] → ['国家能源局','国家发改委']），解析失败容错为空 */
function parseIssuingAuthority(raw?: string | null): string[] {
  if (!raw) return [];
  try {
    const parsed: unknown = JSON.parse(raw);
    return Array.isArray(parsed)
      ? parsed.filter((x): x is string => typeof x === 'string' && x.trim() !== '')
      : [];
  } catch {
    return [];
  }
}

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
  /** 发布机构 JSON 数组文本解析（["国家能源局","国家发改委"] → ['国家能源局','国家发改委']），解析失败容错为空 */
  const issuingAuthorities = parseIssuingAuthority(data.analysis?.issuingAuthority);
  // 事件时间线按 detectedAt 升序（IC-006）
  const events = [...(timeline?.events ?? [])].sort((a, b) =>
    a.detectedAt.localeCompare(b.detectedAt),
  );

  return (
    <>
      <div style={{ marginBottom: 16 }}>
        <Link to="/center">
          <Button icon={<ArrowLeftOutlined />}>返回列表</Button>
        </Link>
      </div>

      {/* 标题区 */}
      <div className="detail-section">
        <div className="detail-header">
          <h2>{data.title}</h2>
          <div className="meta">
            <Tag>{data.sourceName}</Tag>
            <span>
              {data.publishTime ? data.publishTime.replace('T', ' ') : data.publishDate}
            </span>
            {data.author && <span>作者：{data.author}</span>}
            <Tag>{data.language === 'en' ? 'English' : '中文'}</Tag>
            {data.duplicateGroupId && <Tag color="orange">疑似重复</Tag>}
          </div>
          {(data.category ?? []).length > 0 && (
            <div className="meta">
              {(data.category ?? []).map((c) => (
                <Tag key={c} color="blue">
                  {c}
                </Tag>
              ))}
            </div>
          )}
        </div>

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
      </div>

      {/* CNT-003：AI 摘要与原文清晰视觉分隔，AI 字段带统一标记 */}
      {data.summary && (
        <div className="detail-section ai-summary">
          <h3>三段式摘要</h3>
          <div className="detail-body" style={{ marginBottom: 12 }}>
            {data.summary}
          </div>
          <Alert
            type="info"
            showIcon
            message="以上摘要由 AI 自动生成，可能与原文存在偏差，重要决策请以原文为准。"
          />
        </div>
      )}

      {/* 政策与影响分析（AI 加工，说明书政策扩展字段） */}
      {data.analysis && (
        <div className="detail-section ai-summary">
          <h3>政策与影响分析</h3>
          <table className="analysis-table">
            <tbody>
              {data.analysis.policyName && (
                <tr>
                  <th>政策名称</th>
                  <td>{data.analysis.policyName}</td>
                </tr>
              )}
              {issuingAuthorities.length > 0 && (
                <tr>
                  <th>发布机构</th>
                  <td>{issuingAuthorities.join('、')}</td>
                </tr>
              )}
              {data.analysis.policyStatus && (
                <tr>
                  <th>政策状态</th>
                  <td>
                    <Tag color={POLICY_STATUS_COLOR[data.analysis.policyStatus] ?? 'default'}>
                      {POLICY_STATUS_TEXT[data.analysis.policyStatus] ?? data.analysis.policyStatus}
                    </Tag>
                  </td>
                </tr>
              )}
              {data.analysis.effectiveDate && (
                <tr>
                  <th>生效日期</th>
                  <td>{data.analysis.effectiveDate}</td>
                </tr>
              )}
              {data.analysis.bessRelevance && (
                <tr>
                  <th>储能行业相关性</th>
                  <td>
                    <Tag color={RELEVANCE_COLOR[data.analysis.bessRelevance] ?? 'default'}>
                      {RELEVANCE_LABEL[data.analysis.bessRelevance] ?? data.analysis.bessRelevance}
                    </Tag>
                  </td>
                </tr>
              )}
              {data.analysis.jinkoEssRelevance && (
                <tr>
                  <th>晶科储能相关性</th>
                  <td>
                    <Tag color={RELEVANCE_COLOR[data.analysis.jinkoEssRelevance] ?? 'default'}>
                      {RELEVANCE_LABEL[data.analysis.jinkoEssRelevance] ?? data.analysis.jinkoEssRelevance}
                    </Tag>
                  </td>
                </tr>
              )}
              {data.analysis.importanceScore != null && (
                <tr>
                  <th>行业重要性</th>
                  <td>
                    <Tag color={data.analysis.importanceScore >= 8 ? 'orange' : 'default'}>
                      {data.analysis.importanceScore} / 10
                    </Tag>
                  </td>
                </tr>
              )}
            </tbody>
          </table>
          <Alert
            type="info"
            showIcon
            message="以上分析由 AI 自动生成，可能与原文存在偏差，重要决策请以原文为准。"
          />
        </div>
      )}

      {/* 原文内容 */}
      <div className="detail-section">
        <h3>原文内容</h3>
        {data.body ? (
          <div className="detail-body">{data.body}</div>
        ) : (
          <EmptyState description="该内容未采集到正文，可尝试通过原文链接查看。" />
        )}
      </div>

      {/* 相关内容 */}
      <div className="detail-section">
        <h3>相关内容</h3>
        {(related?.items ?? []).length === 0 ? (
          <EmptyState description="暂无相关内容推荐。" />
        ) : (
          (related?.items ?? []).map((r) => (
            <Link key={r.articleId} to={`/center/${r.articleId}`} className="related-card">
              <div className="rel-title">{r.title}</div>
              <div className="rel-meta">
                <Tag color={r.relevance === 'HIGH' ? 'orange' : 'default'}>
                  相关度{RELEVANCE_TEXT[r.relevance]}
                </Tag>
                <span>{r.sourceName} · {r.publishDate}</span>
              </div>
            </Link>
          ))
        )}
      </div>

      {/* 事件时间线 */}
      <div className="detail-section">
        <h3>事件时间线</h3>
        {events.length === 0 ? (
          <EmptyState description="该内容暂未纳入事件跟踪。" />
        ) : (
          <div className="detail-timeline">
            {events.map((e, i) => (
              <div className="tl-item" key={i}>
                <div className="tl-time">
                  <Tag>{EVENT_TYPE_TEXT[e.eventType] ?? e.eventType}</Tag>
                  <span style={{ marginLeft: 6 }}>{e.detectedAt}</span>
                </div>
                <div className="tl-desc">{e.description}</div>
              </div>
            ))}
          </div>
        )}
      </div>
    </>
  );
}
