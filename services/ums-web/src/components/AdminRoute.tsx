import type { ReactNode } from 'react';
import { Navigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

/**
 * 路由级守卫（指南 6.1）。
 * 前端权限控制只是体验优化（隐藏入口），不能替代后端校验（AC-009）。
 */
export function AdminRoute({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  if (!user?.roles.includes('ADMIN')) {
    return <Navigate to="/403" replace />;
  }
  return <>{children}</>;
}
