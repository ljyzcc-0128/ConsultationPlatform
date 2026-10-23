import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { Select } from 'antd';
import { useAuth } from '../auth/AuthContext';
import { useTheme } from '../theme/ThemeProvider';
import { QaRobot } from '../components/QaRobot';

const NAV_ITEMS = [
  { key: '/feed', label: '我的情报' },
  { key: '/center', label: '情报中心' },
  { key: '/supply-chain', label: '供应链看板' },
] as const;

/**
 * 应用整体布局。自定义 navbar + .page 内容区，替代 Ant Design Layout/Header/Menu。
 * 工业风视觉（暖白亮色 / 深绿暗色双主题）由全局 theme.css 统一控制。
 */
export function AppLayout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const { theme, toggleTheme } = useTheme();
  const isAdmin = user?.roles.includes('ADMIN') ?? false;

  return (
    <>
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
          <button
            className="theme-switch"
            type="button"
            onClick={toggleTheme}
            aria-label="切换主题"
          >
            切换：{theme === 'cool' ? '亮色版' : '暗色版'}
          </button>
          <span>{user?.displayName ?? user?.username}</span>
          <Select
            size="small"
            variant="borderless"
            style={{ width: 130 }}
            defaultValue="zh-CN"
            options={[{ value: 'zh-CN', label: '简体中文' }]}
            aria-label="界面语言"
          />
          {/* 单点登录会话不提供退出入口（文档 5.3：晶彩容器内退出会导致会话异常/死循环），
              仅本地 LOCAL 开发模式保留 */}
          {user?.provider === 'LOCAL' && (
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
          )}
        </div>
      </nav>

      <div className="page">
        <Outlet />
      </div>

      <footer className="app-footer">
        储能资讯平台一期 · Jinko ESS · 数据仅供内部参考
      </footer>

      {/* AI 智能问答悬浮机器人（可拖动，登录用户可见） */}
      <QaRobot />
    </>
  );
}
