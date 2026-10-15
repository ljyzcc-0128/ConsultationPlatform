import React, { createContext, useContext, useEffect, useState } from 'react';

/**
 * 双主题：沿用历史键名（localStorage 兼容），语义已切换为工业风设计系统。
 * - 'minimal'：暖白亮色（深绿品牌 + 暖白底），平台默认
 * - 'cool'  ：深绿暗色（深绿底 + 青绿品牌）
 */
export type Theme = 'cool' | 'minimal';

interface ThemeContextValue {
  theme: Theme;
  setTheme: (t: Theme) => void;
  toggleTheme: () => void;
}

const ThemeContext = createContext<ThemeContextValue | null>(null);

const STORAGE_KEY = 'ums-web.theme';

function getInitialTheme(): Theme {
  try {
    const saved = localStorage.getItem(STORAGE_KEY);
    if (saved === 'cool' || saved === 'minimal') return saved;
  } catch { }
  return 'minimal';
}

/**
 * 同步应用主题 class 与持久化：在 setState 触发重渲染之前完成，
 * 保证渲染期间从 CSS 变量读取图表色板（chartTheme.ts）时已是新主题的令牌值。
 */
function applyTheme(t: Theme) {
  document.body.classList.remove('theme-cool', 'theme-minimal');
  document.body.classList.add(`theme-${t}`);
  try { localStorage.setItem(STORAGE_KEY, t); } catch { }
}

export function ThemeProvider({ children }: { children: React.ReactNode }) {
  const [theme, setThemeState] = useState<Theme>(getInitialTheme);

  useEffect(() => {
    applyTheme(theme);
  }, [theme]);

  const setTheme = (t: Theme) => {
    applyTheme(t);
    setThemeState(t);
  };
  const toggleTheme = () => setTheme(theme === 'cool' ? 'minimal' : 'cool');

  return (
    <ThemeContext.Provider value={{ theme, setTheme, toggleTheme }}>
      {children}
    </ThemeContext.Provider>
  );
}

export function useTheme() {
  const ctx = useContext(ThemeContext);
  if (!ctx) throw new Error('useTheme must be inside ThemeProvider');
  return ctx;
}
