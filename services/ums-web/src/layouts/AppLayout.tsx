import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { Select } from 'antd';
import { useAuth } from '../auth/AuthContext';
import { useTheme } from '../theme/ThemeProvider';

const NAV_ITEMS = [
  { key: '/feed', label: '我的情报' },
  { key: '/center', label: '情报中心' },
  { key: '/supply-chain', label: '供应链看板' },
] as const;

/**
 * 应用整体布局（参照设计稿 deepseek_html_20260922_264734.html）。
 * 自定义 navbar + 背景装饰层 + .page 内容区，替代 Ant Design Layout/Header/Menu。
 */
export function AppLayout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const { theme, toggleTheme } = useTheme();
  const isAdmin = user?.roles.includes('ADMIN') ?? false;

  return (
    <>
      {/* 背景装饰层（炫酷版显示） */}
      <div className="aurora" />
      <div className="stars" />
      <div className="scanlines" />

      {/* 主题切换 */}
      <button
        className="theme-switch"
        type="button"
        onClick={toggleTheme}
        aria-label="切换主题"
      >
        切换：{theme === 'cool' ? '极简版' : '炫酷版'}
      </button>

      {/* 顶栏 */}
      <nav className="navbar">
        <div className="logo">储能资讯平台</div>
        <div className="nav-links">
          {NAV_ITEMS.map((item) => (
            <NavLink key={item.key} to={item.key}>
              {item.label}
            </NavLink>
          ))}
          {isAdmin && (
            <NavLink to="/admin">管理后台</NavLink>
          )}
        </div>
        <div className="nav-right">
          <span>{user?.displayName ?? user?.username}</span>
          <Select
            size="small"
            variant="borderless"
            style={{ width: 130 }}
            defaultValue="zh-CN"
            options={[{ value: 'zh-CN', label: '简体中文' }]}
            aria-label="界面语言"
          />
          <span
            className="logout"
            role="button"
            tabIndex={0}
            onClick={() => {
              logout();
              navigate('/login');
            }}
            onKeyDown={(e) => {
              if (e.key === 'Enter' || e.key === ' ') {
                e.preventDefault();
                logout();
                navigate('/login');
              }
            }}
          >
            ⏻ 退出
          </span>
        </div>
      </nav>

      <div className="page">
        <Outlet />
      </div>

      <footer className="app-footer">
        储能资讯平台一期 · Jinko ESS · 数据仅供内部参考
      </footer>
    </>
  );
}
