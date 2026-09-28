import { Button, Empty, Space, Typography } from 'antd';

/**
 * 7.3 空状态：不只说「暂无数据」，要告诉用户为什么是空的、能做什么。
 */
export function EmptyState({
  title = '暂无数据',
  description,
  actionText,
  onAction,
}: {
  title?: string;
  description: string;
  actionText?: string;
  onAction?: () => void;
}) {
  return (
    <Empty
      image={Empty.PRESENTED_IMAGE_SIMPLE}
      description={
        <Space direction="vertical" size={4}>
          <Typography.Text strong>{title}</Typography.Text>
          <Typography.Text type="secondary">{description}</Typography.Text>
        </Space>
      }
      style={{ padding: '48px 0' }}
    >
      {actionText && onAction ? (
        <Button type="primary" onClick={onAction}>
          {actionText}
        </Button>
      ) : null}
    </Empty>
  );
}
