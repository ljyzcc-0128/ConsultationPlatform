import { useEffect, useMemo, useRef, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  Col,
  Descriptions,
  Input,
  List,
  Row,
  Select,
  Space,
  Table,
  Tag,
  Timeline,
  Typography,
} from 'antd';
import type { InputRef } from 'antd';
import {
  ArrowDownOutlined,
  ArrowUpOutlined,
  DotChartOutlined,
  SearchOutlined,
} from '@ant-design/icons';
import { api } from '../../api/client';
import { DataSourceBadge } from '../../components/DataSourceBadge';
import { LazyChart } from '../../components/LazyChart';
import { ErrorState } from '../../components/ErrorState';
import { EmptyState } from '../../components/EmptyState';
import { ListSkeleton } from '../../components/ListSkeleton';
import { useTheme } from '../../theme/ThemeProvider';
import { moduleChartPalette, type ChartPalette } from '../../theme/chartTheme';
import type {
  IndicatorSeries,
  InventoryBlock,
  TradeRecord,
  SupplierUpdate,
} from '../../api/types';

/* =========================================================
   模块元数据（html-style.md：导航 / 章节卡 / 搜索索引同一数据源）
   ========================================================= */

interface ScTopic {
  no: string;
  title: string;
}

interface ScModule {
  id: string;
  no: string;
  kicker: string;
  title: string;
  summary: string;
  question: string;
  topics: ScTopic[];
  /** 模块识别色编号：映射 --sc-m1~m8，从前往后稳定取用 */
  block: number;
}

const MODULES: ScModule[] = [
  {
    id: 'materials',
    no: '01',
    kicker: 'RAW MATERIALS',
    title: '原材料市场',
    summary: '关键原材料价格指标与近 30 天走势。',
    question: '锂、钢材、氮气等关键原材料价格处于什么水平、往哪个方向走？',
    topics: [
      { no: '1.1', title: '关键价格指标总览' },
      { no: '1.2', title: '近 30 天指标走势' },
      { no: '1.3', title: '外部来源与口径标注' },
    ],
    block: 1,
  },
  {
    id: 'inventory',
    no: '02',
    kicker: 'SUPPLY & INVENTORY',
    title: '供需与库存',
    summary: '供给、排产与库存水位的月度组合走势。',
    question: '各环节供给、库存与排产是收紧还是宽松？',
    topics: [
      { no: '2.1', title: '供需平衡组合图' },
      { no: '2.2', title: '系列口径与数据来源' },
    ],
    block: 2,
  },
  {
    id: 'trade',
    no: '03',
    kicker: 'TRADE & LOGISTICS',
    title: '贸易与物流',
    summary: '提单列表、状态筛选与运踪时间线。',
    question: '进出口与运输通道是否顺畅，提单运踪到了哪一步？',
    topics: [
      { no: '3.1', title: '提单列表与状态筛选' },
      { no: '3.2', title: '提单运踪时间线' },
    ],
    block: 3,
  },
  {
    id: 'suppliers',
    no: '04',
    kicker: 'SUPPLIER DYNAMICS',
    title: '供应商动态',
    summary: '供应商经营与风险面的最新信号。',
    question: '供应商经营与风险面出现了哪些新信号？',
    topics: [
      { no: '4.1', title: '风险等级与动态分类' },
      { no: '4.2', title: '供应商动态明细' },
    ],
    block: 4,
  },
];

/** 搜索匹配：标题 / 眉题 / 摘要 / 核心问题 / 二级目录 */
function moduleMatches(m: ScModule, q: string): boolean {
  const haystack = [
    m.title,
    m.kicker,
    m.summary,
    m.question,
    ...m.topics.map((t) => `${t.no} ${t.title}`),
  ]
    .join('\n')
    .toLowerCase();
  return haystack.includes(q.toLowerCase());
}

const RISK_META: Record<SupplierUpdate['riskLevel'], { label: string }> = {
  high: { label: '风险高' },
  medium: { label: '风险中' },
  low: { label: '风险低' },
};

function lineOption(
  points: Array<{ date: string; value: number }>,
  unit: string,
  chart: ChartPalette,
) {
  return {
    color: chart.colors,
    grid: { left: 48, right: 16, top: 24, bottom: 28 },
    xAxis: {
      type: 'category',
      data: points.map((p) => p.date.slice(5)),
      axisLabel: { fontSize: 11, color: chart.label },
    },
    yAxis: {
      type: 'value',
      name: unit,
      scale: true,
      axisLabel: { color: chart.label },
      splitLine: { lineStyle: { color: chart.line } },
    },
    tooltip: { trigger: 'axis' },
    series: [
      {
        type: 'line',
        data: points.map((p) => p.value),
        smooth: true,
        showSymbol: false,
        lineStyle: { width: 2 },
        areaStyle: { opacity: 0.08 },
      },
    ],
  };
}

function comboOption(block: InventoryBlock, chart: ChartPalette) {
  return {
    color: chart.colors,
    grid: { left: 48, right: 56, top: 40, bottom: 28 },
    legend: { top: 4, textStyle: { color: chart.label } },
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      data: block.months,
      axisLabel: { color: chart.label },
    },
    yAxis: [
      {
        type: 'value',
        name: block.series.find((s) => s.type === 'bar')?.unit,
        scale: true,
        axisLabel: { color: chart.label },
        splitLine: { lineStyle: { color: chart.line } },
      },
      {
        type: 'value',
        name: block.series.find((s) => s.type === 'line')?.unit,
        scale: true,
        axisLabel: { color: chart.label },
        splitLine: { show: false },
      },
    ],
    series: block.series.map((s) => ({
      name: s.name,
      type: s.type,
      data: s.data,
      yAxisIndex: s.type === 'line' ? 1 : 0,
      smooth: true,
      barMaxWidth: 22,
    })),
  };
}

/** SC-001：原材料市场指标卡片（当前值+环比箭头，点击展开时序折线） */
function IndicatorCard({
  indicator,
  chart,
}: {
  indicator: IndicatorSeries;
  chart: ChartPalette;
}) {
  const [expanded, setExpanded] = useState(false);
  const up = indicator.momChange >= 0;
  return (
    <div
      className="indicator-card"
      role="button"
      tabIndex={0}
      onClick={() => setExpanded((v) => !v)}
      onKeyDown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault();
          setExpanded((v) => !v);
        }
      }}
    >
      <div style={{ marginBottom: 8 }}>
        <DataSourceBadge scope={indicator.dataScope} sourceName={indicator.sourceName} />
      </div>
      <div className="ind-name">{indicator.name}</div>
      <Space size={8} align="baseline">
        <span className="ind-value">{indicator.latestValue}</span>
        <span className="ind-unit">{indicator.unit}</span>
        <Typography.Text className={up ? 'ind-trend up' : 'ind-trend down'}>
          {up ? <ArrowUpOutlined /> : <ArrowDownOutlined />} {Math.abs(indicator.momChange)}%
        </Typography.Text>
      </Space>
      {expanded ? (
        <LazyChart
          option={lineOption(indicator.points, indicator.unit, chart)}
          height={220}
          fallback={
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              <DotChartOutlined /> 图表加载中…
            </Typography.Text>
          }
        />
      ) : (
        <div className="ind-hint">点击展开近 30 天走势</div>
      )}
    </div>
  );
}

const TRADE_STATUS_COLOR: Record<TradeRecord['status'], string> = {
  在途: 'processing',
  已到港: 'cyan',
  清关中: 'orange',
  已完成: 'success',
};

export function SupplyChainPage() {
  const { theme, toggleTheme } = useTheme();

  /* ---------- 四个 react-query 数据源（与 modules.id 一一对应） ---------- */
  const materials = useQuery({
    queryKey: ['sc-materials'],
    queryFn: () =>
      api.get<never, { indicators: IndicatorSeries[] }>('/v1/supply-chain/materials'),
    staleTime: 5 * 60_000,
  });

  const inventory = useQuery({
    queryKey: ['sc-inventory'],
    queryFn: () => api.get<never, InventoryBlock>('/v1/supply-chain/inventory'),
    staleTime: 5 * 60_000,
  });

  const [tradeStatus, setTradeStatus] = useState<string>();
  const trade = useQuery({
    queryKey: ['sc-trade', tradeStatus],
    queryFn: () =>
      api.get<never, { records: TradeRecord[] }>('/v1/supply-chain/trade-logistics', {
        params: tradeStatus ? { status: tradeStatus } : {},
      }),
    staleTime: 60_000,
  });

  const suppliers = useQuery({
    queryKey: ['sc-suppliers'],
    queryFn: () =>
      api.get<never, { updates: SupplierUpdate[] }>('/v1/supply-chain/suppliers'),
    staleTime: 5 * 60_000,
  });

  /* ---------- 交互状态 ---------- */
  const [openIds, setOpenIds] = useState<ReadonlySet<string>>(
    () => new Set(MODULES.map((m) => m.id)),
  );
  const [query, setQuery] = useState('');
  const [activeId, setActiveId] = useState<string>(MODULES[0].id);
  const searchRef = useRef<InputRef>(null);
  const progressRef = useRef<HTMLDivElement>(null);
  const openIdsBeforePrint = useRef<ReadonlySet<string> | null>(null);

  const searching = query.trim().length > 0;
  const visibleModules = useMemo(
    () => (searching ? MODULES.filter((m) => moduleMatches(m, query.trim())) : MODULES),
    [query, searching],
  );

  /** 搜索时自动展开匹配章节（不自动滚动页面）；非搜索态跟随用户折叠 */
  const isModuleOpen = (m: ScModule) => (searching ? true : openIds.has(m.id));

  const toggleModule = (id: string) =>
    setOpenIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });

  const expandAll = () => setOpenIds(new Set(MODULES.map((m) => m.id)));
  const collapseAll = () => setOpenIds(new Set());

  /* 阅读进度条：滚动时直接写 transform，避免整页重渲染 */
  useEffect(() => {
    let raf = 0;
    const update = () => {
      const el = document.documentElement;
      const max = el.scrollHeight - el.clientHeight;
      const p = max > 0 ? el.scrollTop / max : 0;
      if (progressRef.current) {
        progressRef.current.style.transform = `scaleX(${Math.min(1, Math.max(0, p))})`;
      }
    };
    const onScroll = () => {
      cancelAnimationFrame(raf);
      raf = requestAnimationFrame(update);
    };
    window.addEventListener('scroll', onScroll, { passive: true });
    update();
    return () => {
      window.removeEventListener('scroll', onScroll);
      cancelAnimationFrame(raf);
    };
  }, []);

  /* Ctrl/Cmd + K 聚焦搜索 */
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault();
        searchRef.current?.focus();
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, []);

  /* IntersectionObserver：高亮导航中当前章节（取命中检测带中位置最靠下的章节） */
  useEffect(() => {
    const intersecting = new Map<string, number>();
    const observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          const id = entry.target.id.replace(/^sc-/, '');
          if (entry.isIntersecting) intersecting.set(id, entry.boundingClientRect.top);
          else intersecting.delete(id);
        }
        let best: string | null = null;
        let bestTop = -Infinity;
        intersecting.forEach((top, id) => {
          if (top > bestTop) {
            bestTop = top;
            best = id;
          }
        });
        if (best) setActiveId(best);
      },
      { rootMargin: '-80px 0px -55% 0px', threshold: 0 },
    );
    for (const m of visibleModules) {
      const el = document.getElementById(`sc-${m.id}`);
      if (el) observer.observe(el);
    }
    return () => observer.disconnect();
  }, [visibleModules, openIds, searching]);

  /* 打印：展开全部章节，打印后恢复用户折叠状态 */
  useEffect(() => {
    const beforePrint = () => {
      openIdsBeforePrint.current = openIds;
      setOpenIds(new Set(MODULES.map((m) => m.id)));
    };
    const afterPrint = () => {
      const prev = openIdsBeforePrint.current;
      if (prev) setOpenIds(new Set(prev));
      openIdsBeforePrint.current = null;
    };
    window.addEventListener('beforeprint', beforePrint);
    window.addEventListener('afterprint', afterPrint);
    return () => {
      window.removeEventListener('beforeprint', beforePrint);
      window.removeEventListener('afterprint', afterPrint);
    };
  }, [openIds]);

  const scrollToModule = (id: string) => (e: React.MouseEvent) => {
    e.preventDefault();
    document.getElementById(`sc-${id}`)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  };

  /* ---------- 各模块正文（数据与 modules.id 一一绑定） ---------- */
  const renderBody = (m: ScModule) => {
    const chart = moduleChartPalette(theme, m.block);
    switch (m.id) {
      case 'materials':
        if (materials.isLoading) return <ListSkeleton rows={2} />;
        if (materials.isError)
          return <ErrorState error={materials.error} onRetry={() => materials.refetch()} />;
        return (
          <Row gutter={[12, 12]}>
            {materials.data?.indicators.map((ind) => (
              <Col xs={24} sm={12} lg={8} key={ind.code}>
                <IndicatorCard indicator={ind} chart={chart} />
              </Col>
            ))}
          </Row>
        );
      case 'inventory':
        return (
          <>
            <div className="module-tools">
              {inventory.data
                ? inventory.data.series.map((s) => (
                  <DataSourceBadge key={s.name} scope={s.dataScope} sourceName={s.sourceName} />
                ))
                : null}
            </div>
            {inventory.isLoading ? (
              <ListSkeleton rows={2} />
            ) : inventory.isError ? (
              <ErrorState error={inventory.error} onRetry={() => inventory.refetch()} />
            ) : inventory.data ? (
              <LazyChart option={comboOption(inventory.data, chart)} height={320} />
            ) : null}
          </>
        );
      case 'trade':
        return (
          <>
            <div className="module-tools">
              <Select
                allowClear
                placeholder="状态筛选"
                style={{ width: 140 }}
                value={tradeStatus}
                onChange={setTradeStatus}
                options={['在途', '已到港', '清关中', '已完成'].map((s) => ({ value: s, label: s }))}
              />
            </div>
            {trade.isLoading ? (
              <ListSkeleton rows={3} />
            ) : trade.isError ? (
              <ErrorState error={trade.error} onRetry={() => trade.refetch()} />
            ) : (
              <div className="table-scroll">
                <Table<TradeRecord>
                  rowKey="id"
                  dataSource={trade.data?.records ?? []}
                  pagination={false}
                  expandable={{
                    expandedRowRender: (record) => (
                      <Timeline
                        items={record.timeline.map((t) => ({
                          children: (
                            <Space direction="vertical" size={0}>
                              <Typography.Text>{t.event}</Typography.Text>
                              <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                                {t.time}
                              </Typography.Text>
                            </Space>
                          ),
                        }))}
                      />
                    ),
                  }}
                  locale={{
                    emptyText: <EmptyState description="当前筛选条件下没有提单记录。" />,
                  }}
                  columns={[
                    { title: '提单号', dataIndex: 'id', width: 160 },
                    { title: '货物', dataIndex: 'goods' },
                    { title: '交易对手', dataIndex: 'counterparty', width: 160 },
                    { title: '航线', dataIndex: 'route', width: 200 },
                    { title: '日期', dataIndex: 'date', width: 110 },
                    {
                      title: '状态',
                      dataIndex: 'status',
                      width: 100,
                      render: (s: TradeRecord['status']) => (
                        <Tag color={TRADE_STATUS_COLOR[s]}>{s}</Tag>
                      ),
                    },
                  ]}
                />
              </div>
            )}
          </>
        );
      case 'suppliers':
        if (suppliers.isLoading) return <ListSkeleton rows={3} />;
        if (suppliers.isError)
          return <ErrorState error={suppliers.error} onRetry={() => suppliers.refetch()} />;
        return (
          <List
            grid={{ gutter: 12, xs: 24, sm: 12, lg: 8 }}
            dataSource={suppliers.data?.updates ?? []}
            renderItem={(u) => {
              const meta = RISK_META[u.riskLevel];
              return (
                <List.Item>
                  <div className={`supplier-card risk-${u.riskLevel}`}>
                    <Space direction="vertical" size={6} style={{ width: '100%' }}>
                      <Space size={6} wrap>
                        <Tag
                          color={
                            u.riskLevel === 'high'
                              ? 'red'
                              : u.riskLevel === 'medium'
                                ? 'orange'
                                : 'green'
                          }
                        >
                          {meta.label}
                        </Tag>
                        <Tag>{u.updateType}</Tag>
                      </Space>
                      <Typography.Text strong>{u.supplierName}</Typography.Text>
                      <Typography.Paragraph
                        type="secondary"
                        ellipsis={{ rows: 3 }}
                        style={{ marginBottom: 0, fontSize: 12 }}
                      >
                        {u.description}
                      </Typography.Paragraph>
                      <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                        {u.date}
                      </Typography.Text>
                    </Space>
                  </div>
                </List.Item>
              );
            }}
          />
        );
      default:
        return null;
    }
  };

  const renderModule = (m: ScModule) => {
    const open = isModuleOpen(m);
    return (
      <article
        key={m.id}
        id={`sc-${m.id}`}
        className={`module-card sc-block-${m.block}${open ? ' open' : ''}`}
      >
        <button
          type="button"
          className="module-trigger"
          aria-expanded={open}
          aria-controls={`sc-${m.id}-body`}
          onClick={() => toggleModule(m.id)}
        >
          <span className="module-id">{m.kicker}</span>
          <span className="module-heading-row">
            <span className="module-number">{m.no}</span>
            <span className="module-heading">
              <span className="module-title">{m.title}</span>
              <span className="module-summary">{m.summary}</span>
            </span>
            <span className="chevron" aria-hidden="true" />
          </span>
        </button>
        <div className="module-body" id={`sc-${m.id}-body`}>
          <div className="module-body-inner">
            {/* 目录先于正文：核心问题 + 二级章节目录 */}
            <div className="module-intro">
              <div>
                <div className="module-label">核心问题</div>
                <p className="module-question">{m.question}</p>
              </div>
              <div>
                <div className="module-label">本章目录</div>
                <ul className="module-toc">
                  {m.topics.map((t) => (
                    <li key={t.no}>
                      <span className="toc-no">{t.no}</span>
                      <span className="toc-title">{t.title}</span>
                    </li>
                  ))}
                </ul>
              </div>
            </div>
            {renderBody(m)}
          </div>
        </div>
      </article>
    );
  };

  return (
    <>
      {/* 阅读进度条 */}
      <div className="progress-bar" ref={progressRef} aria-hidden="true" />

      <div className="page-head">
        <div>
          <h1>供应链看板</h1>
          <div className="sub">
            原材料市场 · 供需库存 · 贸易物流 · 供应商动态，外部市场数据与公司内部数据分区呈现
          </div>
        </div>
        <div className="sc-toolbar">
          {searching ? (
            <span className="search-count">
              匹配 {visibleModules.length} / {MODULES.length} 个模块
            </span>
          ) : null}
          <Input
            ref={searchRef}
            className="page-search"
            prefix={<SearchOutlined style={{ color: 'var(--muted)' }} />}
            placeholder="搜索章节 · Ctrl+K"
            allowClear
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Escape') {
                setQuery('');
                searchRef.current?.blur();
              }
            }}
          />
          <button type="button" className="sc-tool-btn" onClick={expandAll}>
            展开全部
          </button>
          <button type="button" className="sc-tool-btn" onClick={collapseAll}>
            收起全部
          </button>
          <button
            type="button"
            className="sc-tool-btn"
            onClick={toggleTheme}
            aria-label="切换深浅色模式"
          >
            {theme === 'cool' ? '切换浅色' : '切换深色'}
          </button>
        </div>
      </div>

      <AlertExternalData />

      <div className="sc-layout">
        {/* 左侧章节导航（与章节卡、搜索同一数据源） */}
        <nav className="module-nav" aria-label="章节导航">
          <div className="module-nav-title">目录</div>
          <ul className="module-nav-list">
            {visibleModules.map((m) => (
              <li key={m.id}>
                <a
                  href={`#sc-${m.id}`}
                  className={activeId === m.id ? 'active' : undefined}
                  onClick={scrollToModule(m.id)}
                >
                  <span className="nav-no">{m.no}</span>
                  <span className="nav-title">{m.title}</span>
                </a>
              </li>
            ))}
          </ul>
        </nav>

        <div className="sc-main">
          {visibleModules.length === 0 ? (
            <EmptyState description="没有匹配的章节，请调整搜索关键词。" />
          ) : (
            visibleModules.map(renderModule)
          )}
        </div>
      </div>

      <div className="scope-card">
        <Descriptions size="small" column={1}>
          <Descriptions.Item label="数据口径">
            外部市场数据来自 SMM 等第三方供应商，仅供内部参考，对外引用需遵循供应商授权条款；
            内部数据来自公司采购与供应链系统。
          </Descriptions.Item>
        </Descriptions>
      </div>
    </>
  );
}

/** 页面顶部对外部数据红线的整体提示 */
function AlertExternalData() {
  return (
    <div className="alert-card">
      <Typography.Text strong>合规提示（SC-007）：</Typography.Text>
      <span>
        本看板同时展示「外部市场数据」与「公司内部数据」，外部数据均以
        <Tag color="warning" style={{ margin: '0 4px' }}>
          外部市场数据 · 来源: SMM
        </Tag>
        样式醒目标注，请注意区分，避免将外部数据误作内部结论使用。
      </span>
    </div>
  );
}
