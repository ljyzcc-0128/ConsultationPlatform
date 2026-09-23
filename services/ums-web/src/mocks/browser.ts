import { setupWorker } from 'msw/browser';
import { handlers } from './handlers';

/** 浏览器端 MSW Worker：只拦截后端一期尚未提供的接口，其余请求放行（bypass） */
export const worker = setupWorker(...handlers);
