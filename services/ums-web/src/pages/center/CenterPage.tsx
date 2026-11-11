import { useCallback, useMemo } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import {
  Col,
  DatePicker,
  Empty,
  Input,
  Row,
  Select,
  Typography,
} from 'antd';
import {
  ArrowLeftOutlined,
  DatabaseOutlined,
  SearchOutlined,
} from '@ant-design/icons';
import dayjs, { type Dayjs } from 'dayjs';
import { api } from '../../api/client';
import { CATEGORY_DICT, type NewsItem, type NewsListResponse } from '../../api/types';
import { EmptyState } from '../../components/EmptyState';
import { ErrorState } from '../../components/ErrorState';
import { ListSkeleton } from '../../components/ListSkeleton';
import { HighlightText } from '../../components/HighlightText';
import { useDebouncedValue } from '../../hooks/useDebouncedValue';

const { RangePicker } = DatePicker;

const LANGUAGE_OPTIONS = [
  { value: 'zh-CN', label: '中文' },
  { value: 'en', label: '英文' },
];

/** 信源视图条目：每信源最新一条 + 入库总数（GET /news/sources-latest） */
export interface SourceLatest {
  latest: NewsItem;
  total: number;
}

export function CenterPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const navigate = useNavigate();

  // 筛选状态全部同步到 URL query（IC-002），刷新/分享链接不丢条件
  const keyword = searchParams.get('q') ?? '';
  const categories = searchParams.get('category')?.split(',').filter(Boolean) ?? [];
  const language = searchParams.get('language') ?? '';
  const sourceId = searchParams.get('sourceId') ?? '';
  const dateFrom = searchParams.get('dateFrom') ?? '';
  const dateTo = searchParams.get('dateTo') ?? '';

  const debouncedKeyword = useDebouncedValue(keyword, 300);
  const debouncedCategories = useDebouncedValue(categories.join(','), 300);
  const debouncedLanguage = useDebouncedValue(language, 300);
  const debouncedSource = useDebouncedValue(sourceId, 300);

  const updateParam = useCallback(
    (key: string, value: string | null) => {
      setSearchParams(
        (prev) => {
          const next = new URLSearchParams(prev);
          if (value) next.set(key, value);
          else next.delete(key);
          return next;
        },
        { replace: true },
      );
    },
    [setSearchParams],
  );

  const isSearching = debouncedKeyword.trim().length > 0;
  const isSourceView = !sourceId;

  /** 信源视图首屏：每信源最新一条（/news/sources-latest） */
  const sourcesQuery = useQuery({
    queryKey: ['center-sources'],
    enabled: isSourceView,
    staleTime: 30_000,
    queryFn: async () =>
      api.get<never, SourceLatest[]>('/news/sources-latest'),
  });
  const sourceCards = useMemo(() => sourcesQuery.data ?? [], [sourcesQuery.data]);

  /** 来源下拉选项从信源视图数据动态生成（新接入信源无需改代码） */
  const sourceOptions = useMemo(
    () => sourceCards.map((s) => ({ value: s.latest.sourceId, label: s.latest.sourceName })),
    [sourceCards],
  );

  /** 搜索（mock /api/v1/search）与真实列表（/api/news）二选一，筛选条件叠加 */
  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['center', { q: debouncedKeyword, category: debouncedCategories, language: debouncedLanguage, sourceId: debouncedSource, dateFrom, dateTo }],
    staleTime: 30_000,
    queryFn: async () => {
      if (isSearching) {
        // 契约收口：/v1/search 与 /news 同构响应（NewsListResponse）
        return api.get<never, NewsListResponse>('/v1/search', {
          params: { q: debouncedKeyword.trim() },
        }).then((res) => ({ items: res.items, total: res.items.length, hasMore: res.hasMore }));
      }
      return api.get<never, { items: NewsItem[]; hasMore: boolean }>('/news', {
        params: {
          limit: 20,
          ...(categories.length ? { category: categories.join(',') } : {}),
          ...(language ? { language } : {}),
          ...(sourceId ? { sourceId } : {}),
          ...(dateFrom ? { dateFrom } : {}),
          ...(dateTo ? { dateTo } : {}),
        },
      }).then((res) => ({ items: res.items, total: res.items.length, hasMore: res.hasMore }));
    },
  });

  const items = useMemo(() => data?.items ?? [], [data]);

  const rangeValue = useMemo<[Dayjs | null, Dayjs | null] | null>(
    () => (dateFrom || dateTo ? [dateFrom ? dayjs(dateFrom) : null, dateTo ? dayjs(dateTo) : null] : null),
    [dateFrom, dateTo],
  );

  return (
    <>
      <div className="page-head">
        <div>
          {isSourceView ? (
            <h1>情报中心</h1>
          ) : (
            <a
              className="back-link"
              role="button"
              tabIndex={0}
              onClick={() => updateParam('sourceId', null)}
              onKeyDown={(e) => {
                if (e.key === 'Enter' || e.key === ' ') {
                  e.preventDefault();
                  updateParam('sourceId', null);
                }
              }}
            >
              <ArrowLeftOutlined /> 返回信源视图
            </a>
          )}
          <div className="sub">
            {isSourceView
              ? (sourcesQuery.isFetching ? '查询中…' : '按信源浏览资讯：每个信源展示最新一条，点击查看该信源全部内容')
              : `「${sourceOptions.find((o) => o.value === sourceId)?.label ?? sourceId}」信源全部资讯，可按关键词 / 语言 / 时间筛选`}
          </div>
        </div>
      </div>

      {/* 信源视图：每信源一张卡片，展示最新一条抓取内容 */}
      {isSourceView ? (
        sourcesQuery.isLoading ? (
          <ListSkeleton rows={6} />
        ) : sourcesQuery.isError ? (
          <ErrorState error={sourcesQuery.error} onRetry={() => sourcesQuery.refetch()} />
        ) : sourceCards.length === 0 ? (
          <Empty description={<Typography.Text type="secondary">还没有信源入库数据，等待定时采集任务执行</Typography.Text>} />
        ) : (
          <Row gutter={[16, 16]}>
            {sourceCards.map((s) => (
              <Col xs={24} lg={12} key={s.latest.sourceId}>
                <div
                  className="feed-card source-card"
                  data-testid="source-card"
                  role="button"
                  tabIndex={0}
                  onClick={() => updateParam('sourceId', s.latest.sourceId)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' || e.key === ' ') {
                      e.preventDefault();
                      updateParam('sourceId', s.latest.sourceId);
                    }
                  }}
                >
                  <div className="source-head">
                    <span className="src">{s.latest.sourceName}</span>
                    <span className="tag">{s.latest.sourceId}</span>
                    <span className="dot" />
                    <span className="src-count">
                      <DatabaseOutlined /> 共 {s.total} 条
                    </span>
                  </div>
                  <div className="feed-title">
                    <HighlightText text={s.latest.title} keyword="" />
                  </div>
                  {s.latest.summary && (
                    <div className="feed-summary">
                      <HighlightText text={s.latest.summary} keyword="" />
                    </div>
                  )}
                  <div className="feed-meta">
                    {s.latest.language && <span className="tag">{s.latest.language}</span>}
                    <span className="dot" />
                    <span>最新：{s.latest.publishDate ?? '—'}</span>
                  </div>
                </div>
              </Col>
            ))}
          </Row>
        )
      ) : (
        <>
          {/* 信源列表视图：搜索框独立于组合筛选，可叠加使用（IC-003） */}
          <div className="filter-bar">
        <Input
          allowClear
          size="large"
          prefix={<SearchOutlined />}
          placeholder="搜索标题 / 摘要关键词…"
          value={keyword}
          onChange={(e) => updateParam('q', e.target.value || null)}
          className="filter-search"
          data-testid="center-search"
        />
        <div className="filter-row">
          <Select
            mode="multiple"
            allowClear
            placeholder="内容域 / 分类"
            value={categories}
            style={{ minWidth: 240 }}
            maxTagCount="responsive"
            options={[...CATEGORY_DICT, 'Battery Energy Storage'].map((c) => ({ value: c, label: c }))}
            onChange={(vals) => updateParam('category', vals.length ? vals.join(',') : null)}
          />
          <Select
            allowClear
            placeholder="语言"
            value={language || undefined}
            style={{ width: 110 }}
            options={LANGUAGE_OPTIONS}
            onChange={(v) => updateParam('language', v ?? null)}
          />
          <Select
            allowClear
            placeholder="来源"
            value={sourceId || undefined}
            style={{ width: 200 }}
            options={sourceOptions}
            onChange={(v) => updateParam('sourceId', v ?? null)}
          />
          <RangePicker
            placeholder={['发布起日', '发布止日']}
            value={rangeValue}
            onChange={(dates) => {
              updateParam('dateFrom', dates?.[0] ? dates[0].format('YYYY-MM-DD') : null);
              updateParam('dateTo', dates?.[1] ? dates[1].format('YYYY-MM-DD') : null);
            }}
          />
        </div>
      </div>

      {isLoading ? (
        <ListSkeleton rows={6} />
      ) : isError ? (
        <ErrorState error={error} onRetry={() => refetch()} />
      ) : items.length === 0 ? (
        isSearching ? (
          <EmptyState
            title="没有找到匹配的内容"
            description={`关键词「${debouncedKeyword}」与当前筛选条件下没有结果，可尝试更换关键词或放宽筛选。`}
          />
        ) : (
          <Empty description={<Typography.Text type="secondary">当前筛选条件下没有内容，可调整筛选条件</Typography.Text>} />
        )
      ) : (
        <Row gutter={[16, 16]}>
          {items.map((item) => (
            <Col xs={24} lg={12} key={item.articleId}>
              <div
                className="feed-card"
                data-testid="news-card"
                role="button"
                tabIndex={0}
                onClick={() => navigate(`/center/${item.articleId}`)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' || e.key === ' ') {
                    e.preventDefault();
                    navigate(`/center/${item.articleId}`);
                  }
                }}
              >
                <div className="feed-src">{item.sourceName}</div>
                <div className="feed-title">
                  <HighlightText text={item.title} keyword={debouncedKeyword} />
                </div>
                {item.summary && (
                  <div className="feed-summary">
                    <HighlightText text={item.summary} keyword={debouncedKeyword} />
                  </div>
                )}
                <div className="feed-meta">
                  {(item.category ?? []).slice(0, 3).map((c) => (
                    <span key={c} className="tag">{c}</span>
                  ))}
                  <span className="dot" />
                  <span>{item.publishDate}</span>
                </div>
              </div>
            </Col>
          ))}
        </Row>
      )}
        </>
      )}
      <div className="filter-stat">
        {!isSourceView && (
          <Typography.Text type="secondary">
            共 {data?.total ?? 0} 条 ·{' '}
            <Link to="/feed/subscriptions">设置关注规则</Link> 可让相关信息自动进入「我的情报」
          </Typography.Text>
        )}
      </div>
    </>
  );
}
