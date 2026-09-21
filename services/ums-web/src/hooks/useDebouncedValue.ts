import { useEffect, useState } from 'react';

/** 指南 4.1 / 第八节：筛选与搜索输入防抖（默认 300ms） */
export function useDebouncedValue<T>(value: T, delay = 300): T {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delay);
    return () => clearTimeout(timer);
  }, [value, delay]);
  return debounced;
}
