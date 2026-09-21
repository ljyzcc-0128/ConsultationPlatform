import { Link, Outlet, useLocation } from 'react-router-dom';
import { Layout, Menu } from 'antd';
import {
  DatabaseOutlined,
  ScheduleOutlined,
  AuditOutlined,
  TeamOutlined,
  NotificationOutlined,
  FileSearchOutlined,
  InboxOutlined,
} from '@ant-design/icons';

const { Sider, Content } = Layout;

const MENU_ITEMS = [
  { key: 'sources', icon: <DatabaseOutlined />, label: <Link to="/admin/sources">信源管理</Link> },
  { key: 'tasks', icon: <ScheduleOutlined />, label: <Link to="/admin/tasks">采集任务</Link> },
  { key: 'content-review', icon: <AuditOutlined />, label: <Link to="/admin/content-review">内容质量处理</Link> },
  { key: 'users', icon: <TeamOutlined />, label: <Link to="/admin/users">用户与权限</Link> },
  { key: 'push', icon: <NotificationOutlined />, label: <Link to="/admin/push">推送管理</Link> },
  { key: 'audit-logs', icon: <FileSearchOutlined />, label: <Link to="/admin/audit-logs">审计日志</Link> },
  { key: 'dlq', icon: <InboxOutlined />, label: <Link to="/admin/dlq">死信队列</Link> },
];

export function AdminLayout() {
  const { pathname } = useLocation();
  const selected = pathname.split('/')[2] ?? 'sources';
  return (
    <Layout style={{ background: 'transparent' }}>
      <Sider width={200} theme="light" style={{ borderRight: '1px solid #f0f0f0' }}>
        <Menu mode="inline" selectedKeys={[selected]} items={MENU_ITEMS} style={{ borderInlineEnd: 'none' }} />
      </Sider>
      <Content style={{ paddingLeft: 24, minWidth: 0 }}>
        <Outlet />
      </Content>
    </Layout>
  );
}
