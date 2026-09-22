import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  Col,
  Descriptions,
  List,
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
import type {
  IndicatorSeries,
  InventoryBlock,
  TradeRecord,
  SupplierUpdate,
} from '../../api/types';

const RISK_META: Record<SupplierUpdate['riskLevel'], { label: string }> = {
  high: { label: '风险高' },
  medium: { label: '风险中' },
  low: { label: '风险低' },
};

function lineOption(points: Array<{ date: string; value: number }>, unit: string) {
  return {
    grid: { left: 48, right: 16, top: 24, bottom: 28 },
    xAxis: {
      type: 'category',
      data: points.map((p) => p.date.slice(5)),
      axisLabel: { fontSize: 11 },
    },
    yAxis: { type: 'value', name: unit, scale: true },
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

function comboOption(block: InventoryBlock) {
  return {
    grid: { left: 48, right: 56, top: 40, bottom: 28 },
    legend: { top: 4 },
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: block.months },
    yAxis: [
      { type: 'value', name: block.series.find((s) => s.type === 'bar')?.unit, scale: true },
      { type: 'value', name: block.series.find((s) => s.type === 'line')?.unit, scale: true },
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
function IndicatorCard({ indicator }: { indicator: IndicatorSeries }) {
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
        <Typography.Text style={{ color: up ? '#cf1322' : '#3f8600', fontSize: 13 }}>
          {up ? <ArrowUpOutlined /> : <ArrowDownOutlined />} {Math.abs(indicator.momChange)}%
        </Typography.Text>
      </Space>
      {expanded ? (
        <LazyChart
          option={lineOption(indicator.points, indicator.unit)}
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

  return (
    <>
      <div className="page-head">
        <div>
          <h1>供应链看板</h1>
          <div className="sub">
            原材料市场 · 供需库存 · 贸易物流 · 供应商动态，外部市场数据与公司内部数据分区呈现
          </div>
        </div>
      </div>

      <AlertExternalData />

      {/* 区块一：原材料市场（SC-001） */}
      <div className="section-card">
        <div className="section-head">
          <div className="section-title">原材料市场</div>
          <div className="section-extra">点击指标卡展开走势</div>
        </div>
        {materials.isLoading ? (
          <ListSkeleton rows={2} />
        ) : materials.isError ? (
          <ErrorState error={materials.error} onRetry={() => materials.refetch()} />
        ) : (
          <Row gutter={[12, 12]}>
            {materials.data?.indicators.map((ind) => (
              <Col xs={24} sm={12} lg={8} key={ind.code}>
                <IndicatorCard indicator={ind} />
              </Col>
            ))}
          </Row>
        )}
      </div>

      {/* 区块二：供需与库存（SC-002，柱状+折线组合图） */}
      <div className="section-card">
        <div className="section-head">
          <div className="section-title">供需与库存</div>
          <div className="section-extra">
            {inventory.data ? (
              <Space size={4}>
                {inventory.data.series.map((s) => (
                  <DataSourceBadge key={s.name} scope={s.dataScope} sourceName={s.sourceName} />
                ))}
              </Space>
            ) : null}
          </div>
        </div>
        {inventory.isLoading ? (
          <ListSkeleton rows={2} />
        ) : inventory.isError ? (
          <ErrorState error={inventory.error} onRetry={() => inventory.refetch()} />
        ) : inventory.data ? (
          <LazyChart option={comboOption(inventory.data)} height={320} />
        ) : null}
      </div>

      {/* 区块三：贸易与物流（SC-003，列表筛选 + 提单运踪时间线） */}
      <div className="section-card">
        <div className="section-head">
          <div className="section-title">贸易与物流</div>
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
        )}
      </div>

      {/* 区块四：供应商动态（SC-004，卡片 + 风险醒目色块） */}
      <div className="section-card">
        <div className="section-head">
          <div className="section-title">供应商动态</div>
        </div>
        {suppliers.isLoading ? (
          <ListSkeleton rows={3} />
        ) : suppliers.isError ? (
          <ErrorState error={suppliers.error} onRetry={() => suppliers.refetch()} />
        ) : (
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
        )}
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
