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

interface AuthContextValue {
  user: AuthUser | null;
  loading: boolean;
  login: (username: string, provider?: string, credential?: string) => Promise<void>;
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
    () => ({ user, loading, login, logout }),
    [user, loading, login, logout],
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
