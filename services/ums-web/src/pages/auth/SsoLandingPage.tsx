import { useEffect, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Spin } from 'antd';
import { useAuth } from '../../auth/AuthContext';

/**
 * 晶彩单点登录落地页（公共路由，无需登录）。
 *
 * 后端 /api/auth/sso/callback 完成授权码换取后 302 到本页：
 *   /sso/landing?token=xxx   → 存 token、拉取用户信息、进入首页
 *   /sso/landing?error=xxx   → 展示错误并引导回登录页
 *
 * 必须在晶彩容器内打开（IAM 依赖晶彩 Cookie 静默授权），
 * 详见《IAM-晶彩内单点认证集成开发文档》5.2 节。
 */
export function SsoLandingPage() {
  const { ssoLogin } = useAuth();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [error, setError] = useState<string | null>(null);
  const started = useRef(false);

  useEffect(() => {
    if (started.current) return;
    started.current = true;

    const token = params.get('token');
    const err = params.get('error');
    // 卡片/待办深链：登录后直落指定页面（仅允许站内路径，防外跳）
    const target = params.get('target');
    const safeTarget = target && /^\/[a-zA-Z0-9\-_/.]*$/.test(target) ? target : '/feed';

    if (token) {
      ssoLogin(token)
        .then(() => {
          // 清除地址栏中的 token 参数（避免留在浏览器历史），再进目标页
          window.history.replaceState(null, '', '/sso/landing');
          navigate(safeTarget, { replace: true });
        })
        .catch(() => {
          setError('单点登录失败，请重新从晶彩工作台进入');
        });
    } else if (err) {
      setError(err);
    } else {
      navigate('/login', { replace: true });
    }
  }, [params, ssoLogin, navigate]);

  return (
    <div className="login-page">
      <div className="card" style={{ textAlign: 'center', padding: '48px 32px' }}>
        {error ? (
          <>
            <div className="title" style={{ marginBottom: 8 }}>
              单点登录失败
            </div>
            <p style={{ color: 'var(--muted)', fontSize: 13.5, lineHeight: 1.7 }}>{error}</p>
            <button
              className="btn-login"
              type="button"
              onClick={() => navigate('/login', { replace: true })}
            >
              返回登录
            </button>
          </>
        ) : (
          <Spin size="large" tip="正在完成单点登录…">
            <div style={{ minHeight: 80, minWidth: 200 }} />
          </Spin>
        )}
      </div>
    </div>
  );
}
