import { Link, NavLink, Outlet, useLocation } from 'react-router-dom';
import {
  DatabaseOutlined,
  ScheduleOutlined,
  AuditOutlined,
  TeamOutlined,
  NotificationOutlined,
  FileSearchOutlined,
  InboxOutlined,
  ArrowLeftOutlined,
} from '@ant-design/icons';

const MENU_ITEMS = [
  { key: 'sources', icon: <DatabaseOutlined />, label: '信源管理' },
  { key: 'tasks', icon: <ScheduleOutlined />, label: '采集任务' },
  { key: 'content-review', icon: <AuditOutlined />, label: '内容质量处理' },
  { key: 'users', icon: <TeamOutlined />, label: '用户与权限' },
  { key: 'push', icon: <NotificationOutlined />, label: '推送管理' },
  { key: 'audit-logs', icon: <FileSearchOutlined />, label: '审计日志' },
  { key: 'dlq', icon: <InboxOutlined />, label: '死信队列' },
];

/**
 * 管理后台布局（与整体双主题风格统一）。
 * 自定义玻璃态侧栏 + 内容区，替代 Ant Design Sider/Menu；
 * 背景继承 AppLayout 的极光/星空装饰层，视觉由全局 theme.css 控制。
 */
export function AdminLayout() {
  const { pathname } = useLocation();
  const selected = pathname.split('/')[2] ?? 'sources';

  return (
    <div className="admin-shell">
      <aside className="admin-sider">
        <div className="admin-brand">
          <span className="admin-brand-mark" />
          <span>管理后台</span>
        </div>

        <nav className="admin-nav">
          {MENU_ITEMS.map((item) => (
            <NavLink
              key={item.key}
              to={`/admin/${item.key}`}
              className={selected === item.key ? 'active' : ''}
            >
              <span className="admin-nav-icon">{item.icon}</span>
              {item.label}
            </NavLink>
          ))}
        </nav>

        <Link to="/feed" className="admin-back">
          <ArrowLeftOutlined />
          返回前台
        </Link>
      </aside>

      <div className="admin-content">
        <Outlet />
      </div>
    </div>
  );
}
