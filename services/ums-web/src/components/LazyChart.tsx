import { Suspense, lazy, useEffect, useRef, useState, type ReactNode } from 'react';
import { Skeleton } from 'antd';

/**
 * 性能优化（指南第八节）：
 * 1. ECharts 通过 React.lazy 动态 import，独立 chunk，未进入供应链看板不加载；
 * 2. IntersectionObserver：滚动到可视区才渲染图表。
 */
const EChartsLazy = lazy(() => import('echarts-for-react'));

export function LazyChart({
  option,
  height = 300,
  fallback,
}: {
  option: Record<string, unknown>;
  height?: number;
  fallback?: ReactNode;
}) {
  const containerRef = useRef<HTMLDivElement>(null);
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    const el = containerRef.current;
    if (!el) return;
    if (typeof IntersectionObserver === 'undefined') {
      setVisible(true);
      return;
    }
    const observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting) {
            setVisible(true);
            observer.disconnect();
          }
        }
      },
      { rootMargin: '100px' },
    );
    observer.observe(el);
    return () => observer.disconnect();
  }, []);

  return (
    <div ref={containerRef} style={{ minHeight: height }}>
      {visible ? (
        <Suspense fallback={<Skeleton.Node active style={{ width: '100%', height }} />}>
          <EChartsLazy
            option={option}
            notMerge
            lazyUpdate
            style={{ height, width: '100%' }}
            opts={{ renderer: 'svg' }}
          />
        </Suspense>
      ) : (
        (fallback ?? <Skeleton.Node active style={{ width: '100%', height }} />)
      )}
    </div>
  );
}
