import { useState } from 'react';
import { Form, message, type FormInstance } from 'antd';
import { useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../../auth/AuthContext';
import { useTheme } from '../../theme/ThemeProvider';

/**
 * 登录页
 *
 * LOCAL 模式（开发）：仅输入用户名即可登录。
 * IAM 模式：输入用户名 + 密码，凭据发送后端 IamAuthProvider 验证。
 *
 * 切换方式：前端环境变量 VITE_AUTH_PROVIDER=IAM（默认 LOCAL）
 *
 * 工业风双主题（暖白亮色 / 深绿暗色）由全局 theme.css 控制。
 */
const AUTH_PROVIDER = import.meta.env.VITE_AUTH_PROVIDER ?? 'LOCAL';
const IS_IAM = AUTH_PROVIDER === 'IAM';

type LoginForm = { username: string; password?: string };

function FieldError({
  name,
  form,
}: {
  name: 'username' | 'password';
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
  const [form] = Form.useForm<LoginForm>();

  const onFinish = async (values: LoginForm) => {
    setSubmitting(true);
    try {
      const credential = IS_IAM ? values.password ?? '' : '';
      await login(values.username.trim(), AUTH_PROVIDER, credential);
      const from = (location.state as { from?: string } | null)?.from ?? '/feed';
      navigate(from, { replace: true });
    } catch {
      message.error(IS_IAM ? '用户名或密码错误' : '登录失败，请检查用户名');
    } finally {
      setSubmitting(false);
    }
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
          initialValues={{ username: IS_IAM ? '' : 'admin' }}
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

          {IS_IAM && (
            <div className="field">
              <label htmlFor="login-password">
                <span className="req">*</span>密码
              </label>
              <div className="input-wrap">
                <Form.Item<LoginForm>
                  name="password"
                  rules={[{ required: true, message: '请输入密码' }]}
                  noStyle
                >
                  <input
                    id="login-password"
                    className="login-input"
                    type="password"
                    placeholder="请输入密码"
                    autoComplete="current-password"
                  />
                </Form.Item>
              </div>
              <FieldError name="password" form={form} />
            </div>
          )}

          <button
            className="btn-login"
            type="submit"
            disabled={submitting || loading}
          >
            登 录
          </button>

          <div className="footnote">
            {IS_IAM ? (
              <>
                通过公司 <span>IAM 系统认证</span>
              </>
            ) : (
              <>
                开发模式（LOCAL 认证）· <span>IAM 预留接口已就绪</span>
              </>
            )}
          </div>
        </Form>
      </div>
    </div>
  );
}
