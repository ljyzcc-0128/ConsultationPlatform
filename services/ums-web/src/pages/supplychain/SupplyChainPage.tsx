import { useEffect, useRef, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  Col,
  Descriptions,
  Row,
  Select,
  Space,
  Table,
  Tag,
  Timeline,
  Typography,
} from 'antd';
import {
  ArrowDownOutlined,
  ArrowUpOutlined,
  DotChartOutlined,
} from '@ant-design/icons';
import { api } from '../../api/client';
import { DataSourceBadge } from '../../components/DataSourceBadge';
import { LazyChart } from '../../components/LazyChart';
import { ErrorState } from '../../components/ErrorState';
import { EmptyState } from '../../components/EmptyState';
import { ListSkeleton } from '../../components/ListSkeleton';
import { useTheme } from '../../theme/ThemeProvider';
import { moduleChartPalette, getTrendColors, type ChartPalette } from '../../theme/chartTheme';
import type { IndicatorSeries, InventoryBlock, TradeRecord } from '../../api/types';

/* =========================================================
   模块元数据（章节卡与渲染分支同一数据源）
   ========================================================= */

interface ScModule {
  id: string;
  no: string;
  title: string;
  /** 模块识别色编号：映射 --sc-m1~m8，从前往后稳定取用 */
  block: number;
}

const MODULES: ScModule[] = [
  { id: 'materials', no: '01', title: '原材料市场', block: 1 },
  { id: 'inventory', no: '02', title: '供需与库存', block: 2 },
  { id: 'trade', no: '03', title: '贸易与物流', block: 3 },
];

/**
 * SC-001 指标迷你走势：弱化坐标轴（隐藏 Y 轴、仅显示首尾日期），
 * 线与渐变面积按涨跌语义着色，末端白描边圆点突出最新值。
 */
function sparklineOption(
  points: Array<{ date: string; value: number }>,
  unit: string,
  trendColor: string,
  chart: ChartPalette,
) {
  const lastIndex = points.length - 1;
  return {
    grid: { left: 10, right: 10, top: 14, bottom: 24 },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'line', lineStyle: { color: trendColor, opacity: 0.4 } },
      valueFormatter: (value: unknown) => `${value} ${unit}`,
    },
    xAxis: {
      type: 'category',
      data: points.map((p) => p.date.slice(5)),
      axisTick: { show: false },
      axisLine: { show: false },
      axisLabel: {
        fontSize: 11,
        color: chart.label,
        interval: (index: number) => index === 0 || index === lastIndex,
      },
    },
    yAxis: { type: 'value', scale: true, show: false },
    series: [
      {
        type: 'line',
        data: points.map((p) => p.value),
        smooth: 0.4,
        showSymbol: false,
        lineStyle: { width: 2, color: trendColor },
        areaStyle: {
          opacity: 0.12,
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: trendColor },
              { offset: 1, color: 'transparent' },
            ],
          },
        },
        markPoint: {
          symbol: 'circle',
          symbolSize: 7,
          itemStyle: { color: trendColor, borderColor: chart.surface, borderWidth: 2 },
          label: { show: false },
          data: lastIndex >= 0 ? [{ coord: [lastIndex, points[lastIndex].value] }] : [],
        },
      },
    ],
  };
}

function comboOption(block: InventoryBlock, chart: ChartPalette) {
  return {
    color: chart.colors,
    grid: { left: 48, right: 56, top: 48, bottom: 28 },
    legend: { top: 4, textStyle: { color: chart.label } },
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      data: block.months,
      axisLabel: { color: chart.label, fontSize: 12 },
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
      /* 数值直接标注：系列色 + 表面色描边光晕，避免与网格线/相邻标签糊在一起 */
      label: {
        show: true,
        fontSize: 12,
        fontWeight: 600,
        position: 'top',
        distance: s.type === 'line' ? 8 : 5,
        textBorderColor: chart.surface,
        textBorderWidth: 2,
      },
    })),
  };
}

/** SC-001：原材料市场指标卡片（指标名+来源标注 / 大数值+环比角标 / 涨跌着色迷你走势） */
function IndicatorCard({
  indicator,
  chart,
  trendColor,
}: {
  indicator: IndicatorSeries;
  chart: ChartPalette;
  trendColor: string;
}) {
  const up = indicator.momChange >= 0;
  return (
    <div className="indicator-card">
      <div className="ind-top">
        <span className="ind-name">{indicator.name}</span>
        <DataSourceBadge scope={indicator.dataScope} sourceName={indicator.sourceName} />
      </div>
      <div className="ind-value-row">
        <span className="ind-value">{indicator.latestValue}</span>
        <span className="ind-unit">{indicator.unit}</span>
        <span className={up ? 'ind-chip up' : 'ind-chip down'}>
          {up ? <ArrowUpOutlined /> : <ArrowDownOutlined />} {Math.abs(indicator.momChange)}%
          <em>环比</em>
        </span>
      </div>
      <div className="ind-chart">
        <LazyChart
          option={sparklineOption(indicator.points, indicator.unit, trendColor, chart)}
          height={150}
          fallback={
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              <DotChartOutlined /> 图表加载中…
            </Typography.Text>
          }
        />
      </div>
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
  const { theme } = useTheme();

  /* ---------- 三个 react-query 数据源（与 modules.id 一一对应） ---------- */
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

  /* ---------- 交互状态 ---------- */
  const [openIds, setOpenIds] = useState<ReadonlySet<string>>(
    () => new Set(MODULES.map((m) => m.id)),
  );
  const progressRef = useRef<HTMLDivElement>(null);
  const openIdsBeforePrint = useRef<ReadonlySet<string> | null>(null);

  const isModuleOpen = (m: ScModule) => openIds.has(m.id);

  const toggleModule = (id: string) =>
    setOpenIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });

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

  /* ---------- 各模块正文（数据与 modules.id 一一绑定） ---------- */
  const renderBody = (m: ScModule) => {
    const chart = moduleChartPalette(theme, m.block);
    switch (m.id) {
      case 'materials': {
        if (materials.isLoading) return <ListSkeleton rows={2} />;
        if (materials.isError)
          return <ErrorState error={materials.error} onRetry={() => materials.refetch()} />;
        const trend = getTrendColors(theme);
        return (
          <Row gutter={[14, 14]}>
            {materials.data?.indicators.map((ind) => (
              <Col xs={24} sm={12} lg={8} key={ind.code}>
                <IndicatorCard
                  indicator={ind}
                  chart={chart}
                  trendColor={ind.momChange >= 0 ? trend.up : trend.down}
                />
              </Col>
            ))}
          </Row>
        );
      }
      case 'inventory':
        if (inventory.isLoading) return <ListSkeleton rows={2} />;
        if (inventory.isError)
          return <ErrorState error={inventory.error} onRetry={() => inventory.refetch()} />;
        return inventory.data ? (
          <LazyChart option={comboOption(inventory.data, chart)} height={320} />
        ) : null;
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
          <span className="module-heading-row">
            <span className="module-number">{m.no}</span>
            <span className="module-heading">
              <span className="module-title">{m.title}</span>
            </span>
            <span className="chevron" aria-hidden="true" />
          </span>
        </button>
        <div className="module-body" id={`sc-${m.id}-body`}>
          <div className="module-body-inner">{renderBody(m)}</div>
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
            原材料市场 · 供需库存 · 贸易物流，外部市场数据与公司内部数据分区呈现
          </div>
        </div>
      </div>

      <AlertExternalData />

      {MODULES.map(renderModule)}

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
