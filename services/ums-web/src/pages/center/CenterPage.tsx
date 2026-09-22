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
import { SearchOutlined } from '@ant-design/icons';
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

const SOURCE_OPTIONS = [
  { value: 'CN-01', label: '中国储能网' },
  { value: 'CN-02', label: '广东水力学会' },
  { value: 'INT-01', label: 'Utility Dive' },
  { value: 'INT-02', label: 'Energy-Storage.news' },
  { value: 'INT-03', label: 'eszoneo' },
];

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

  /** 搜索（mock /api/v1/search）与真实列表（/api/news）二选一，筛选条件叠加 */
  const { data, isLoading, isError, error, refetch, isFetching } = useQuery({
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
          <h1>情报中心</h1>
          <div className="sub">
            {isFetching ? '查询中…' : '检索全量资讯，按内容域 / 语言 / 来源 / 时间筛选，关键词全文匹配高亮'}
          </div>
        </div>
      </div>

      {/* 搜索框独立于组合筛选，可叠加使用（IC-003） */}
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
            options={SOURCE_OPTIONS}
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
                <div className="feed-title">
                  <HighlightText text={item.title} keyword={debouncedKeyword} />
                </div>
                {item.summary && (
                  <div className="feed-summary">
                    <HighlightText text={item.summary} keyword={debouncedKeyword} />
                  </div>
                )}
                <div className="feed-meta">
                  {item.category.slice(0, 3).map((c) => (
                    <span key={c} className="tag">{c}</span>
                  ))}
                  <span className="dot" />
                  <span className="src">{item.sourceName}</span>
                  <span className="dot" />
                  <span>{item.publishDate}</span>
                </div>
              </div>
            </Col>
          ))}
        </Row>
      )}
      <div className="filter-stat">
        <Typography.Text type="secondary">
          共 {data?.total ?? 0} 条 ·{' '}
          <Link to="/feed/subscriptions">设置关注规则</Link> 可让相关信息自动进入「我的情报」
        </Typography.Text>
      </div>
    </>
  );
}
