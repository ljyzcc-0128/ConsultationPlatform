import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App as AntdApp, ConfigProvider, theme as antdTheme } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import App from './App';
import { AuthProvider } from './auth/AuthContext';
import { ThemeProvider, useTheme } from './theme/ThemeProvider';
import './styles/theme.css';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
});

async function enableMocking() {
  if (!import.meta.env.DEV) return;
  const { worker } = await import('./mocks/browser');
  // 只拦截 mock handlers 中注册的接口，其余（真实后端接口）放行
  return worker.start({ onUnhandledRequest: 'bypass', serviceWorker: { url: '/mockServiceWorker.js' } });
}

/**
 * 内层 ConfigProvider：按工业风设计系统令牌配置 antd。
 * - minimal（暖白亮色，默认）：亮色算法 + 深绿主色
 * - cool（深绿暗色）：暗色算法 + 深绿系底色与青绿主色
 * 令牌值与 theme.css 的 CSS 变量保持同步。
 */
function ThemedAntdApp({ children }: { children: React.ReactNode }) {
  const { theme } = useTheme();
  return (
    <ConfigProvider
      locale={zhCN}
      theme={{
        algorithm: theme === 'cool' ? antdTheme.darkAlgorithm : antdTheme.defaultAlgorithm,
        token:
          theme === 'cool'
            ? {
              colorPrimary: '#63bba7',
              colorBgBase: '#101815',
              colorBgContainer: '#15201c',
              colorBgElevated: '#1a2823',
              colorBgSpotlight: '#1a2823',
              colorBorder: '#405149',
              colorBorderSecondary: '#2b3a34',
              colorText: '#edf4f0',
              colorTextSecondary: '#a6b3ad',
              colorTextTertiary: '#8fa098',
              colorTextQuaternary: '#6d7d74',
              borderRadius: 10,
            }
            : {
              colorPrimary: '#0f6758',
              borderRadius: 10,
            },
      }}
    >
      <AntdApp>{children}</AntdApp>
    </ConfigProvider>
  );
}

enableMocking().then(() => {
  createRoot(document.getElementById('root')!).render(
    <StrictMode>
      <ConfigProvider locale={zhCN}>
        <ThemeProvider>
          <ThemedAntdApp>
            <QueryClientProvider client={queryClient}>
              <AuthProvider>
                <BrowserRouter>
                  <App />
                </BrowserRouter>
              </AuthProvider>
            </QueryClientProvider>
          </ThemedAntdApp>
        </ThemeProvider>
      </ConfigProvider>
    </StrictMode>,
  );
});
