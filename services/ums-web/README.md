# UMS Web — 储能资讯平台前端

三入口 Web UI：资讯 Feed（时间线/搜索/订阅推送）、供应链看板（四大区块图表）、管理后台（信源/任务/审核/用户/审计等）。

## 技术栈

- React 18 + TypeScript + Vite
- Ant Design 5（UI 组件）
- TanStack Query（服务端状态与缓存）
- ECharts（按需懒加载 `LazyChart`）
- MSW（Mock Service Worker，接口未就绪时的兜底 mock）
- Vitest + Testing Library（单元测试）

## 目录结构

```
src/
├── api/        # axios client 与类型契约（types.ts）
├── auth/       # 认证上下文
├── components/ # 通用组件（JsonDiff、DataSourceBadge、LazyChart…）
├── layouts/    # 布局
├── mocks/      # MSW handlers（仅保留后端不可用时的兜底数据）
├── pages/
│   ├── feed/          # 资讯时间线（四 Tab）+ 订阅管理
│   ├── center/        # 内容中心 + 详情页（相关内容/时间线）
│   ├── supplychain/   # 供应链看板
│   └── admin/         # 管理后台（8 个页面）
└── tests/      # 测试用例
```

## 快速启动

```bash
npm install
npm run dev        # http://localhost:5173（代理已指向后端 8080）
```

常用命令：

| 用途 | 命令 |
|------|------|
| 开发启动 | `npm run dev` |
| 类型检查 | `npx tsc --noEmit` |
| 单元测试 | `npx vitest run` |
| 生产构建 | `npm run build`（产物 dist/，交 nginx 托管） |

## 配置

- `VITE_AUTH_PROVIDER=LOCAL | IAM`：登录框模式（与后端认证模式保持一致，IAM 时显示密码框）
- 开发代理：`vite.config.ts` 中 `/api` → `http://localhost:8080`

## 页面路由

| 路径 | 页面 | 权限 |
|------|------|------|
| `/login` | 登录 | 公开 |
| `/feed` | 资讯时间线（今日/未读/订阅/热门） | 登录 |
| `/feed/subscriptions` | 我的订阅 | 登录 |
| `/center`、`/center/:id` | 内容中心、详情 | 登录 |
| `/supply-chain` | 供应链看板 | 登录 |
| `/admin/*` | 管理后台（sources/tasks/content-review/users/push/audit-logs/dlq） | ADMIN（403 守卫） |

## 文档

- [操作手册](../../储能资讯平台操作手册_v1.0.md) — 各页面功能与交互说明
- [接口文档](../../储能资讯平台接口文档_v1.0.md) — 对接的后端契约
