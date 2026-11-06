import { Avatar, Card, Descriptions, Tag } from 'antd';
import { useAuth } from '../../auth/AuthContext';

/**
 * 个人中心：展示当前登录用户的基础信息（只读）。
 * 入口在右上角头像下拉菜单（AppLayout）。
 */
export function ProfilePage() {
  const { user } = useAuth();
  if (!user) return null;

  const isSso = user.provider !== 'LOCAL';

  return (
    <div style={{ maxWidth: 640, margin: '0 auto' }}>
      <h1>个人中心</h1>
      <Card>
        <div style={{ display: 'flex', alignItems: 'center', gap: 16, marginBottom: 24 }}>
          <Avatar size={64} style={{ backgroundColor: '#2e7d32', fontSize: 28 }}>
            {(user.displayName || user.username || '用').slice(0, 1)}
          </Avatar>
          <div>
            <div style={{ fontSize: 20, fontWeight: 600 }}>{user.displayName || user.username}</div>
            <div style={{ color: 'var(--text-secondary, #888)' }}>{user.username}</div>
          </div>
        </div>
        <Descriptions column={1} bordered size="middle">
          <Descriptions.Item label="登录账号">{user.username}</Descriptions.Item>
          <Descriptions.Item label="邮箱">{user.email || '—'}</Descriptions.Item>
          <Descriptions.Item label="角色">
            {user.roles.map((r) => (
              <Tag key={r} color={r === 'ADMIN' ? 'green' : 'blue'}>
                {r}
              </Tag>
            ))}
          </Descriptions.Item>
          <Descriptions.Item label="登录方式">
            {isSso ? '晶彩单点登录（IAM）' : '本地账号'}
          </Descriptions.Item>
          <Descriptions.Item label="外部身份标识">
            {isSso ? '见管理后台用户详情' : '—'}
          </Descriptions.Item>
        </Descriptions>
      </Card>
    </div>
  );
}
