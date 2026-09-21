import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { AdminRoute } from '../components/AdminRoute';
import { AuthProvider } from '../auth/AuthContext';

/** 受保护内容标记 */
function SecretPage() {
  return <div>ADMIN-ONLY-CONTENT</div>;
}

function renderWithRole(initialEntries: string[], roles: 'USER'[] | ['USER', 'ADMIN']) {
  // 渲染前先落好用户态，AuthProvider 初始化即读到正确角色
  localStorage.setItem(
    'ums-web.user',
    JSON.stringify({
      id: 'test-user',
      username: 'tester',
      displayName: '测试用户',
      email: null,
      roles,
      provider: 'LOCAL',
    }),
  );
  return render(
    <AuthProvider>
      <MemoryRouter initialEntries={initialEntries}>
        <Routes>
          <Route
            path="/admin"
            element={
              <AdminRoute>
                <SecretPage />
              </AdminRoute>
            }
          />
          <Route path="/403" element={<div>FORBIDDEN-PAGE</div>} />
        </Routes>
      </MemoryRouter>
    </AuthProvider>,
  );
}

describe('AdminRoute 路由守卫（指南 6.1 / AC-009）', () => {
  it('非管理员访问 /admin 重定向到 /403', () => {
    renderWithRole(['/admin'], ['USER']);
    expect(screen.getByText('FORBIDDEN-PAGE')).toBeInTheDocument();
    expect(screen.queryByText('ADMIN-ONLY-CONTENT')).not.toBeInTheDocument();
  });

  it('管理员可以访问受保护内容', async () => {
    renderWithRole(['/admin'], ['USER', 'ADMIN']);
    await waitFor(() => {
      expect(screen.getByText('ADMIN-ONLY-CONTENT')).toBeInTheDocument();
    });
  });
});
