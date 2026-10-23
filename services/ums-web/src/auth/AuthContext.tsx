import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import { api } from '../api/client';

export type Role = 'USER' | 'ADMIN';

export interface AuthUser {
  id: string;
  username: string;
  displayName: string;
  email: string | null;
  roles: Role[];
  provider: string;
}

interface LoginResponse {
  token: string;
  user: {
    userId: string;
    username: string;
    displayName: string;
    roles: Role[];
    provider: string;
    externalId: string | null;
  };
}

interface MeResponse {
  userId: string;
  username: string;
  displayName: string;
  roles: Role[];
  provider: string;
  externalId: string | null;
}

interface AuthContextValue {
  user: AuthUser | null;
  loading: boolean;
  login: (username: string, provider?: string, credential?: string) => Promise<void>;
  /** 晶彩 SSO 回调落地：凭后端签发的 token 激活本地登录态 */
  ssoLogin: (token: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

const TOKEN_KEY = 'ums-web.token';
const USER_KEY = 'ums-web.user';

function readStoredUser(): AuthUser | null {
  try {
    const raw = localStorage.getItem(USER_KEY);
    return raw ? (JSON.parse(raw) as AuthUser) : null;
  } catch {
    return null;
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(readStoredUser);
  const [loading, setLoading] = useState<boolean>(false);

  const login = useCallback(async (username: string, provider: string = 'LOCAL', credential: string = '') => {
    setLoading(true);
    try {
      const res = (await api.post<never, LoginResponse>('/auth/login', {
        provider,
        username,
        credential,
      })) as LoginResponse;
      localStorage.setItem(TOKEN_KEY, res.token);
      const authUser: AuthUser = {
        id: res.user.userId,
        username: res.user.username,
        displayName: res.user.displayName,
        email: null,
        roles: res.user.roles,
        provider: res.user.provider,
      };
      localStorage.setItem(USER_KEY, JSON.stringify(authUser));
      setUser(authUser);
    } finally {
      setLoading(false);
    }
  }, []);

  const ssoLogin = useCallback(async (token: string) => {
    setLoading(true);
    try {
      // 先存 token（axios 拦截器带上），再拉当前用户信息激活登录态
      localStorage.setItem(TOKEN_KEY, token);
      const me = (await api.get<never, MeResponse>('/auth/me')) as MeResponse;
      const authUser: AuthUser = {
        id: me.userId,
        username: me.username,
        displayName: me.displayName,
        email: null,
        roles: me.roles,
        provider: me.provider,
      };
      localStorage.setItem(USER_KEY, JSON.stringify(authUser));
      setUser(authUser);
    } catch (e) {
      // /auth/me 校验失败：token 无效，回滚并向上抛出
      localStorage.removeItem(TOKEN_KEY);
      throw e;
    } finally {
      setLoading(false);
    }
  }, []);

  const logout = useCallback(() => {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    setUser(null);
  }, []);

  // 全局 401 处理：token 失效时清登录态
  useEffect(() => {
    const onUnauthorized = () => logout();
    window.addEventListener('ums:unauthorized', onUnauthorized);
    return () => window.removeEventListener('ums:unauthorized', onUnauthorized);
  }, [logout]);

  const value = useMemo<AuthContextValue>(
    () => ({ user, loading, login, ssoLogin, logout }),
    [user, loading, login, ssoLogin, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth 必须在 AuthProvider 内使用');
  return ctx;
}

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}
