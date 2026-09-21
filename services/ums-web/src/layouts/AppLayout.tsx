import { Outlet, NavLink, useNavigate } from 'react-router-dom';
import { Button, Layout, Menu, Select, Space, Typography } from 'antd';
import { LogoutOutlined } from '@ant-design/icons';
import { useAuth } from '../auth/AuthContext';

const { Header, Content, Footer } = Layout;

const NAV_ITEMS = [
  { key: '/feed', label: '我的情报' },
  { key: '/center', label: '情报中心' },
  { key: '/supply-chain', label: '供应链看板' },
];

export function AppLayout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const isAdmin = user?.roles.includes('ADMIN') ?? false;

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Header
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          background: '#001529',
        }}
      >
        <Space size={32}>
          <Typography.Title level={4} style={{ color: '#fff', margin: 0 }}>
            储能资讯平台
          </Typography.Title>
          <Menu
            theme="dark"
            mode="horizontal"
            selectedKeys={[]}
            style={{ background: 'transparent', minWidth: 360 }}
            items={[
              ...NAV_ITEMS.map((item) => ({
                key: item.key,
                label: <NavLink to={item.key}>{item.label}</NavLink>,
              })),
              ...(isAdmin
                ? [{ key: '/admin', label: <NavLink to="/admin">管理后台</NavLink> }]
                : []),
            ]}
            onClick={({ key }) => navigate(key)}
          />
        </Space>
        <Space size={12} align="center">
          <Typography.Text style={{ color: 'rgba(255,255,255,0.85)', fontSize: 13 }}>
            {user?.displayName ?? user?.username}
          </Typography.Text>
          <Select
            size="small"
            variant="borderless"
            style={{ width: 130, color: 'rgba(255,255,255,0.85)' }}
            defaultValue="zh-CN"
            options={[{ value: 'zh-CN', label: '简体中文' }]}
            aria-label="界面语言"
          />
          <Button
            type="text"
            icon={<LogoutOutlined />}
            style={{ color: 'rgba(255,255,255,0.85)' }}
            onClick={() => {
              logout();
              navigate('/login');
            }}
          >
            退出
          </Button>
        </Space>
      </Header>
      <Content style={{ padding: '24px 32px', maxWidth: 1280, width: '100%', margin: '0 auto' }}>
        <Outlet />
      </Content>
      <Footer style={{ textAlign: 'center', color: 'rgba(0,0,0,0.45)' }}>
        储能资讯平台一期 · Jinko ESS · 数据仅供内部参考
      </Footer>
    </Layout>
  );
}
