import { Skeleton } from 'antd';

/** 7.3 加载状态：列表用骨架屏，减少布局跳动 */
export function ListSkeleton({ rows = 5 }: { rows?: number }) {
  return (
    <div style={{ padding: '8px 0' }} aria-busy="true" aria-label="加载中">
      {Array.from({ length: rows }).map((_, i) => (
        <Skeleton
          key={i}
          active
          title={{ width: '60%' }}
          paragraph={{ rows: 1, width: '90%' }}
          style={{ marginBottom: 24 }}
        />
      ))}
    </div>
  );
}
