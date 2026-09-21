import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { EmptyState } from '../components/EmptyState';
import { JsonDiff } from '../components/JsonDiff';

describe('EmptyState（7.3 空状态引导）', () => {
  it('展示说明文案与行动引导按钮', async () => {
    const onAction = vi.fn();
    render(
      <EmptyState
        description="您还没有设置任何关注规则"
        actionText="去关注管理页设置"
        onAction={onAction}
      />,
    );
    expect(screen.getByText('您还没有设置任何关注规则')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: '去关注管理页设置' }));
    expect(onAction).toHaveBeenCalledOnce();
  });

  it('无行动回调时不渲染按钮', () => {
    render(<EmptyState description="暂无内容" />);
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });
});

describe('JsonDiff（6.3 审计日志 JSON diff）', () => {
  it('高亮变化字段：变更 / 新增 / 移除', () => {
    render(
      <JsonDiff
        before={{ crawlEnabled: true, priority: 3, remark: '旧备注' }}
        after={{ crawlEnabled: false, priority: 3, sourceName: '新名称' }}
      />,
    );
    expect(screen.getByText('crawlEnabled')).toBeInTheDocument();
    // 变更前值带删除线，变更后值加粗
    expect(screen.getByText('true')).toBeInTheDocument();
    expect(screen.getByText('false')).toBeInTheDocument();
    expect(screen.getByText('新增')).toBeInTheDocument();
    expect(screen.getByText('移除')).toBeInTheDocument();
    expect(screen.getByText('变更')).toBeInTheDocument();
  });

  it('before 缺省时全部展示为新增（审计日志仅有写入快照）', () => {
    render(<JsonDiff after={{ args: ['id-1', ['BESS', 'Policy']] }} />);
    expect(screen.getByText('args[0]')).toBeInTheDocument();
    expect(screen.getByText('args[1][0]')).toBeInTheDocument();
    expect(screen.getByText('BESS')).toBeInTheDocument();
  });

  it('接受 JSON 字符串输入', () => {
    render(
      <JsonDiff
        before={'{"priority": 3}'}
        after={'{"priority": 5}'}
      />,
    );
    expect(screen.getByText('priority')).toBeInTheDocument();
    expect(screen.getByText('5')).toBeInTheDocument();
  });
});
