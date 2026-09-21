import { Suspense, lazy } from 'react';
import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { Spin } from 'antd';
import { AppLayout } from './layouts/AppLayout';
import { FeedPage } from './pages/feed/FeedPage';
import { SubscriptionsPage } from './pages/feed/SubscriptionsPage';
import { CenterPage } from './pages/center/CenterPage';
import { ContentDetailPage } from './pages/center/ContentDetailPage';
import { SupplyChainPage } from './pages/supplychain/SupplyChainPage';
import { LoginPage } from './pages/auth/LoginPage';
import { EmptyState } from './components/EmptyState';
import { AdminRoute } from './components/AdminRoute';
import { useAuth } from './auth/AuthContext';

/** 管理后台独立 chunk：普通用户不加载 admin 代码（指南第八节） */
const AdminApp = lazy(() => import('./pages/admin/AdminApp'));

function PageLoading() {
  return (
    <div style={{ display: 'flex', justifyContent: 'center', padding: 96 }}>
      <Spin size="large" tip="加载中…">
        <div style={{ minHeight: 80, minWidth: 200 }} />
      </Spin>
    </div>
  );
}

function ForbiddenPage() {
  return (
    <EmptyState
      title="没有访问权限"
      description="该页面需要管理员权限，如需开通请联系平台管理员。"
    />
  );
}

/** 受保护路由：未登录跳 /login */
function RequireAuth({ children }: { children: React.ReactNode }) {
  const { user } = useAuth();
  const location = useLocation();
  if (!user) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }
  return <>{children}</>;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route
        element={
          <RequireAuth>
            <AppLayout />
          </RequireAuth>
        }
      >
        <Route path="/" element={<Navigate to="/feed" replace />} />
        <Route path="/feed" element={<FeedPage />} />
        <Route path="/feed/subscriptions" element={<SubscriptionsPage />} />
        <Route path="/center" element={<CenterPage />} />
        <Route path="/center/:contentId" element={<ContentDetailPage />} />
        <Route path="/supply-chain" element={<SupplyChainPage />} />
        <Route path="/403" element={<ForbiddenPage />} />
        <Route
          path="/admin/*"
          element={
            <AdminRoute>
              <Suspense fallback={<PageLoading />}>
                <AdminApp />
              </Suspense>
            </AdminRoute>
          }
        />
        <Route path="*" element={<Navigate to="/feed" replace />} />
      </Route>
    </Routes>
  );
}
