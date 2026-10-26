import { useState } from 'react';
import { Form, message, type FormInstance } from 'antd';
import { useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../../auth/AuthContext';
import { useTheme } from '../../theme/ThemeProvider';

/**
 * 登录页
 *
 * 页面底部可在两种认证方式间随时切换（运行时状态，无需重新构建）：
 * - LOCAL 模式（开发）：仅输入用户名即可登录。
 * - IAM 模式：输入用户名 + 密码，凭据发送后端 IamAuthProvider 验证。
 *
 * 构建期默认值由环境变量 VITE_AUTH_PROVIDER=IAM 控制（默认 LOCAL）。
 *
 * 工业风双主题（暖白亮色 / 深绿暗色）由全局 theme.css 控制。
 */
const DEFAULT_PROVIDER =
  (import.meta.env.VITE_AUTH_PROVIDER ?? 'LOCAL') === 'IAM' ? 'IAM' : 'LOCAL';

type AuthMode = 'LOCAL' | 'IAM';

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
  const [authMode, setAuthMode] = useState<AuthMode>(DEFAULT_PROVIDER);
  const isIam = authMode === 'IAM';
  const [form] = Form.useForm<LoginForm>();

  /* 切换认证方式：清掉密码值与校验状态，避免残留报错 */
  const switchMode = (mode: AuthMode) => {
    if (mode === authMode) return;
    setAuthMode(mode);
    form.resetFields(['password']);
  };

  const onFinish = async (values: LoginForm) => {
    setSubmitting(true);
    try {
      const credential = isIam ? values.password ?? '' : '';
      await login(values.username.trim(), authMode, credential);
      const from = (location.state as { from?: string } | null)?.from ?? '/feed';
      navigate(from, { replace: true });
    } catch {
      message.error(isIam ? '用户名或密码错误' : '登录失败，请检查用户名');
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
          initialValues={{ username: DEFAULT_PROVIDER === 'IAM' ? '' : 'admin' }}
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

          {isIam && (
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
            {isIam ? (
              <>
                通过公司 IAM 系统认证 ·{' '}
                <button
                  type="button"
                  className="footnote-link"
                  onClick={() => switchMode('LOCAL')}
                >
                  本地账号登录
                </button>
              </>
            ) : (
              <>
                开发模式（LOCAL 认证，免密） ·{' '}
                <button
                  type="button"
                  className="footnote-link"
                  onClick={() => switchMode('IAM')}
                >
                  IAM 账号登录
                </button>
              </>
            )}
          </div>
        </Form>
      </div>
    </div>
  );
}
