import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { DataSourceBadge } from '../components/DataSourceBadge';

describe('DataSourceBadge（SC-007 合规红线）', () => {
  it('外部市场数据：显示醒目标注与来源名，不是小灰字角标', () => {
    render(<DataSourceBadge scope="EXTERNAL_MARKET_DATA" sourceName="SMM" />);
    const badge = screen.getByText(/外部市场数据/);
    expect(badge).toBeInTheDocument();
    expect(screen.getByText(/SMM/)).toBeInTheDocument();
    // 视觉红线：外部数据使用警示色 tag（与内部数据完全不同的底色）
    expect(badge.closest('.ant-tag-warning')).not.toBeNull();
    expect(badge.closest('.ant-tag')).not.toHaveClass('ant-tag-hidden');
  });

  it('内部数据：显示内部数据标识', () => {
    render(<DataSourceBadge scope="INTERNAL" sourceName="内部采购系统" />);
    expect(screen.getByText(/内部数据/)).toBeInTheDocument();
    expect(screen.queryByText(/外部市场数据/)).not.toBeInTheDocument();
  });

  it('外部与内部徽标同时渲染时可被区分', () => {
    render(
      <div>
        <DataSourceBadge scope="EXTERNAL_MARKET_DATA" sourceName="SMM" />
        <DataSourceBadge scope="INTERNAL" sourceName="内部采购系统" />
      </div>,
    );
    const external = screen.getByText(/外部市场数据/).closest('.ant-tag');
    const internal = screen.getByText(/内部数据/).closest('.ant-tag');
    expect(external?.className).not.toEqual(internal?.className);
  });
});
