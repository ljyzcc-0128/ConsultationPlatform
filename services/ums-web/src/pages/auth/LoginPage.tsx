import { useState } from 'react';
import { Button, Card, Form, Input, Typography, message } from 'antd';
import { useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../../auth/AuthContext';

/**
 * 登录页
 *
 * LOCAL 模式（开发）：仅输入用户名即可登录。
 * IAM 模式：输入用户名 + 密码，凭据发送后端 IamAuthProvider 验证。
 *
 * 切换方式：前端环境变量 VITE_AUTH_PROVIDER=IAM（默认 LOCAL）
 */
const AUTH_PROVIDER = import.meta.env.VITE_AUTH_PROVIDER ?? 'LOCAL';
const IS_IAM = AUTH_PROVIDER === 'IAM';

export function LoginPage() {
  const { login, loading } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [submitting, setSubmitting] = useState(false);

  const onFinish = async (values: { username: string; password?: string }) => {
    setSubmitting(true);
    try {
      const credential = IS_IAM ? values.password ?? '' : '';
      await login(values.username.trim(), AUTH_PROVIDER, credential);
      const from = (location.state as { from?: string } | null)?.from ?? '/feed';
      navigate(from, { replace: true });
    } catch (err) {
      message.error(IS_IAM ? '用户名或密码错误' : '登录失败，请检查用户名');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: '#f0f2f5',
      }}
    >
      <Card style={{ width: 380 }}>
        <Typography.Title level={3} style={{ textAlign: 'center', marginBottom: 32 }}>
          储能资讯平台
        </Typography.Title>

        <Form
          onFinish={onFinish}
          layout="vertical"
          initialValues={{ username: IS_IAM ? '' : 'admin' }}
        >
          <Form.Item
            name="username"
            label="用户名"
            rules={[{ required: true, message: '请输入用户名' }]}
          >
            <Input placeholder="请输入用户名" size="large" autoComplete="username" />
          </Form.Item>

          {IS_IAM && (
            <Form.Item
              name="password"
              label="密码"
              rules={[{ required: true, message: '请输入密码' }]}
            >
              <Input.Password
                placeholder="请输入密码"
                size="large"
                autoComplete="current-password"
              />
            </Form.Item>
          )}

          <Button
            type="primary"
            htmlType="submit"
            size="large"
            block
            loading={submitting || loading}
          >
            登录
          </Button>
        </Form>

        <Typography.Paragraph
          type="secondary"
          style={{ marginTop: 16, textAlign: 'center', fontSize: 12 }}
        >
          {IS_IAM
            ? '通过公司 IAM 系统认证'
            : '开发模式（LOCAL 认证），IAM 预留接口已就绪'}
        </Typography.Paragraph>
      </Card>
    </div>
  );
}
