import { Tag, Tooltip } from 'antd';
import { GlobalOutlined, WarningOutlined } from '@ant-design/icons';

/**
 * SC-007 红线组件：外部市场数据必须醒目标注，不得被误认为公司内部数据。
 * EXTERNAL_MARKET_DATA 使用与内部数据完全不同的视觉（橙色警示底 + 图标 + 大号字），
 * 不是小灰字角标。
 */
export function DataSourceBadge({
  scope,
  sourceName,
}: {
  scope: string;
  sourceName?: string;
}) {
  if (scope === 'EXTERNAL_MARKET_DATA') {
    return (
      <Tooltip title="该数据来自外部市场数据供应商，非公司内部数据，引用时请遵循供应商授权条款">
        <Tag
          className="external-data-badge"
          icon={<WarningOutlined />}
          color="warning"
        >
          外部市场数据{sourceName ? ` · 来源: ${sourceName}` : ''}
        </Tag>
      </Tooltip>
    );
  }
  return (
    <Tooltip title="公司内部数据">
      <Tag className="internal-data-badge" icon={<GlobalOutlined />} color="blue">
        内部数据{sourceName ? ` · ${sourceName}` : ''}
      </Tag>
    </Tooltip>
  );
}
