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
 * 内层 ConfigProvider：根据当前主题设置 antd 的配色算法。
 * - cool 主题：暗色算法
 * - minimal 主题：默认（亮色）算法
 */
function ThemedAntdApp({ children }: { children: React.ReactNode }) {
  const { theme } = useTheme();
  return (
    <ConfigProvider
      locale={zhCN}
      theme={{
        algorithm: theme === 'cool' ? antdTheme.darkAlgorithm : antdTheme.defaultAlgorithm,
        // cool 主题下整体提亮：antd 暗色算法默认容器偏黑，这里统一上调底色明度
        ...(theme === 'cool'
          ? {
            token: {
              colorBgBase: '#1C2547',
              colorBgContainer: '#232E55',
              colorBgElevated: '#2A3764',
              colorBgSpotlight: '#2A3764',
              colorBorder: '#3D4B7D',
              colorBorderSecondary: '#313D6B',
              colorText: '#F1F4FF',
              colorTextSecondary: '#C6CFEC',
              colorTextTertiary: '#9AA8D2',
              colorTextQuaternary: '#7C8AB5',
            },
          }
          : {}),
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
