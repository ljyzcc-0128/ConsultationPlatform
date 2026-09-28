import { Button, Result } from 'antd';
import { getErrorMessage } from '../api/client';

/**
 * 7.3 错误状态：展示错误码对应的中文提示，不把后端 error.message 原样展示；
 * 网络错误给「重试」入口。
 */
export function ErrorState({
  error,
  onRetry,
}: {
  error: unknown;
  onRetry?: () => void;
}) {
  const message = getErrorMessage(error);
  return (
    <Result
      status="warning"
      title="加载失败"
      subTitle={message}
      extra={
        onRetry ? (
          <Button type="primary" onClick={onRetry}>
            重试
          </Button>
        ) : undefined
      }
    />
  );
}
