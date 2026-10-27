import { useState } from 'react';
import { Form, message, type FormInstance } from 'antd';
import { useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../../auth/AuthContext';
import { useTheme } from '../../theme/ThemeProvider';

/**
 * JIP 网关 IAM4_OAuth_getCode：晶彩账号密码认证入口（公开地址，无密钥）。
 * 跳转后晶彩完成账号密码认证，IAM 按登记的回调地址 302 回
 * /api/auth/sso/callback?code=...，由后端 SSO 主流程签发 JWT。
 * 可用环境变量 VITE_IAM_AUTHORIZE_URL / VITE_IAM_CLIENT_ID 覆盖。
 */
const IAM_AUTHORIZE_URL =
  (import.meta.env.VITE_IAM_AUTHORIZE_URL as string | undefined)
    ?? 'https://jiptest.jinkosolar.com/JIP/IAM/IAM4_OAuth_getCode/1.0.0';
const IAM_CLIENT_ID =
  (import.meta.env.VITE_IAM_CLIENT_ID as string | undefined) ?? 'APP189';

/**
 * 登录页
 *
 * 两种登录方式：
 * - 本地模式（开发）：仅输入用户名即可登录（LOCAL 认证，免密）。
 * - IAM（晶彩）账号登录：整页跳转 JIP 网关 IAM4_OAuth_getCode 授权端点，
 *   在晶彩账号密码认证页完成登录后，IAM 302 回 /api/auth/sso/callback 携带 code，
 *   复用单点登录主流程换 token 并签发 JWT（/sso/landing 落地）。
 *
 * 工业风双主题（暖白亮色 / 深绿暗色）由全局 theme.css 控制。
 */
type LoginForm = { username: string };

function FieldError({
  name,
  form,
}: {
  name: 'username';
  form: FormInstance<LoginForm>;
}) {
  return (
    <Form.Item<LoginForm> noStyle shouldUpdate>
      {() => {
        const err = form.getFieldError(name)[0];
        return err ? (
          <div
            style={{
              color: '#e05252',
              fontSize: 11.5,
              marginTop: 6,
              position: 'relative',
              zIndex: 2,
            }}
            role="alert"
          >
            {err}
          </div>
        ) : null;
      }}
    </Form.Item>
  );
}

export function LoginPage() {
  const { login, loading } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const { theme, toggleTheme } = useTheme();
  const [submitting, setSubmitting] = useState(false);
  const [redirecting, setRedirecting] = useState(false);
  const [form] = Form.useForm<LoginForm>();

  const onFinish = async (values: LoginForm) => {
    setSubmitting(true);
    try {
      await login(values.username.trim(), 'LOCAL', '');
      const from = (location.state as { from?: string } | null)?.from ?? '/feed';
      navigate(from, { replace: true });
    } catch {
      message.error('登录失败，请检查用户名');
    } finally {
      setSubmitting(false);
    }
  };

  /* IAM 登录入口：整页跳转 JIP 网关 getCode，回调地址按当前站点自动拼接 */
  const startIamLogin = () => {
    setRedirecting(true);
    const callback = `${window.location.origin}/api/auth/sso/callback`;
    window.location.href =
      `${IAM_AUTHORIZE_URL}?client_id=${encodeURIComponent(IAM_CLIENT_ID)}` +
      `&response_type=code&redirect_uri=${encodeURIComponent(callback)}`;
  };

  return (
    <div className="login-page">
      {/* 主题切换 */}
      <button
        className="theme-switch"
        type="button"
        onClick={toggleTheme}
        aria-label="切换主题"
      >
        切换：{theme === 'cool' ? '亮色版' : '暗色版'}
      </button>

      {/* 登录卡片 */}
      <div className="card">
        <Form<LoginForm>
          form={form}
          onFinish={onFinish}
          initialValues={{ username: 'admin' }}
          layout="vertical"
        >
          <div className="brand">
            <div className="title">储能资讯平台</div>
            <div className="subtitle">Energy Storage Intelligence</div>
          </div>

          <div className="field">
            <label htmlFor="login-username">
              <span className="req">*</span>用户名
            </label>
            <div className="input-wrap">
              <Form.Item<LoginForm>
                name="username"
                rules={[{ required: true, message: '请输入用户名' }]}
                noStyle
              >
                <input
                  id="login-username"
                  className="login-input"
                  type="text"
                  placeholder="请输入用户名"
                  autoComplete="username"
                />
              </Form.Item>
            </div>
            <FieldError name="username" form={form} />
          </div>

          <button
            className="btn-login"
            type="submit"
            disabled={submitting || loading}
          >
            登 录
          </button>

          <div className="footnote">
            企业用户（晶彩账号） ·{' '}
            <button
              type="button"
              className="footnote-link"
              onClick={startIamLogin}
              disabled={redirecting}
            >
              {redirecting ? '跳转中…' : 'IAM 账号登录'}
            </button>
          </div>
        </Form>
      </div>
    </div>
  );
}
