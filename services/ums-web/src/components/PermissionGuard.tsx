import type { ReactNode } from 'react';
import { useAuth } from '../auth/AuthContext';

/** 权限判断收敛在一处（指南 6.1）：按钮级权限守卫 */
export function PermissionGuard({
  action,
  children,
}: {
  action: string;
  children: ReactNode;
}) {
  const { user } = useAuth();
  // 一期仅区分 ADMIN；后续细粒度权限在此统一扩展
  const allowed = user?.roles.includes('ADMIN') ?? false;
  if (!allowed) return null;
  void action; // 预留：action → 权限点映射
  return <>{children}</>;
}
