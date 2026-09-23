import axios, { AxiosError } from 'axios';
import { getToken } from '../auth/AuthContext';

/**
 * 错误码 → 中文文案映射表（指南 7.3：不把后端 error.message 原样展示给用户）
 */
export const ERROR_MESSAGES: Record<number, string> = {
  400: '请求参数有误，请检查后重试',
  401: '登录状态已失效，请重新登录',
  403: '没有操作权限，请联系管理员',
  404: '请求的内容不存在',
  409: '操作冲突，数据可能已被其他人修改，请刷新后重试',
  500: '服务器开小差了，请稍后重试',
  502: '后端服务暂不可用，请稍后重试',
  503: '服务维护中，请稍后重试',
  504: '请求超时，请稍后重试',
};

export function getErrorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const axiosErr = error as AxiosError;
    if (!axiosErr.response) {
      return '网络连接失败，请检查网络后重试';
    }
    const { status } = axiosErr.response;
    return ERROR_MESSAGES[status] ?? `请求失败（HTTP ${status}），请稍后重试`;
  }
  return '发生未知错误，请重试';
}

export const api = axios.create({
  baseURL: '/api',
  timeout: 30_000,
});

// 请求拦截器：自动附加 JWT
api.interceptors.request.use((config) => {
  const token = getToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// 响应拦截器：统一脱壳 + 401 跳登录
api.interceptors.response.use(
  (res) => res.data,
  (error: AxiosError) => {
    if (error.response?.status === 401) {
      // 通知 AuthProvider 清除登录态，由路由守卫跳转 /login
      window.dispatchEvent(new CustomEvent('ums:unauthorized'));
    }
    return Promise.reject(error);
  },
);
