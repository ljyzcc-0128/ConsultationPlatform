import type { Theme } from './ThemeProvider';

/**
 * 工业风 ECharts 图表色板 —— 运行时从 body 级 CSS 变量读取（theme.css 双主题令牌）。
 * - 读取结果按主题缓存；body class 变化（主题切换）时由 MutationObserver 清空缓存；
 * - SSR 或 CSS 未就绪时回退到与 theme.css 同步的静态兜底色；
 * - 模块识别色支持 block 1~8（--sc-m1 ~ --sc-m8），从前往后稳定取用。
 */

export interface ChartPalette {
  colors: string[];
  label: string;
  line: string;
}

/** 静态兜底色（与 theme.css 的 --chart-* / --sc-m* 令牌保持同步） */
const FALLBACK: Record<Theme, ChartPalette> = {
  minimal: {
    colors: ['#0f6758', '#ed7047', '#268070', '#3e789f', '#775eaa'],
    label: '#66716c',
    line: '#dce3de',
  },
  cool: {
    colors: ['#63bba7', '#ed7047', '#8fd4c4', '#7aa6c9', '#a593cf'],
    label: '#a6b3ad',
    line: '#2b3a34',
  },
};

const FALLBACK_MODULES: Record<Theme, string[]> = {
  minimal: ['#e86f48', '#268070', '#3e789f', '#775eaa', '#c88b27', '#3e8d80', '#65718d', '#b25b4c'],
  cool: ['#ef8260', '#52a892', '#7aa6c9', '#a593cf', '#d9a441', '#6fbfae', '#96a3bd', '#c97a6b'],
};

const SERIES_TOKENS = ['--chart-1', '--chart-2', '--chart-3', '--chart-4', '--chart-5'] as const;

/** 模块识别色令牌数量（对应 theme.css 的 --sc-m1 ~ --sc-m8） */
export const MAX_MODULE_BLOCK = 8;

const seriesCache = new Map<Theme, ChartPalette>();
const moduleCache = new Map<Theme, string[]>();
let themeObserverStarted = false;

function readToken(name: string, fallback: string): string {
  if (typeof window === 'undefined') return fallback;
  const value = getComputedStyle(document.body).getPropertyValue(name).trim();
  return value || fallback;
}

/** 监听 body class 变化（主题切换）→ 清空缓存，下次读取重新解析 CSS 变量 */
function startThemeObserver() {
  if (
    themeObserverStarted ||
    typeof window === 'undefined' ||
    typeof MutationObserver === 'undefined'
  ) {
    return;
  }
  const observer = new MutationObserver((mutations) => {
    if (mutations.some((m) => m.attributeName === 'class')) {
      resetChartPaletteCache();
    }
  });
  observer.observe(document.body, { attributes: true, attributeFilter: ['class'] });
  themeObserverStarted = true;
}

/** 清空色板缓存（主题切换后自动调用，也可由外部主动刷新） */
export function resetChartPaletteCache() {
  seriesCache.clear();
  moduleCache.clear();
}

/** 全局图表色板：系列色 / 坐标轴文字 / 分隔线，全部来自 CSS 变量 */
export function getChartTheme(theme: Theme): ChartPalette {
  startThemeObserver();
  const cached = seriesCache.get(theme);
  if (cached) return cached;
  const fb = FALLBACK[theme];
  const palette: ChartPalette = {
    colors: SERIES_TOKENS.map((token, i) => readToken(token, fb.colors[i])),
    label: readToken('--chart-label', fb.label),
    line: readToken('--chart-line', fb.line),
  };
  seriesCache.set(theme, palette);
  return palette;
}

/** 模块识别色列表（html-style.md：从前往后稳定取用，新增模块不改既有颜色） */
export function getModuleColors(theme: Theme): string[] {
  startThemeObserver();
  const cached = moduleCache.get(theme);
  if (cached) return cached;
  const fb = FALLBACK_MODULES[theme];
  const colors = fb.map((c, i) => readToken(`--sc-m${i + 1}`, c));
  moduleCache.set(theme, colors);
  return colors;
}

/** 区块内图表色板：模块识别色打头，其余沿用全局系列色（block 支持 1~8） */
export function moduleChartPalette(theme: Theme, block: number): ChartPalette {
  const modules = getModuleColors(theme);
  const index = (((Math.round(block) - 1) % MAX_MODULE_BLOCK) + MAX_MODULE_BLOCK) % MAX_MODULE_BLOCK;
  const moduleColor = modules[index];
  const base = getChartTheme(theme);
  return {
    colors: [moduleColor, ...base.colors.filter((c) => c.toLowerCase() !== moduleColor.toLowerCase())],
    label: base.label,
    line: base.line,
  };
}
