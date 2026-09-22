# UMS Main Java — 储能资讯平台主服务

平台核心后端：内容加工与检索、用户体系、订阅推送、供应链看板、管理后台 API，以及 RabbitMQ 消息消费与 AI 加工链路。

## 技术栈

- Java 17 / Spring Boot 3.3.5
- MyBatis-Plus 3.5.7 + MySQL 8（Flyway V1–V6 自动迁移）
- Spring Security + JWT（LOCAL / 公司 IAM 双认证模式）
- RabbitMQ（事件总线，exchange `ums`，含 DLQ）
- 智谱 GLM（AI 摘要/分类/翻译，`glm-4-flash`）

## 模块结构

```
com.example.ums
├── admin         # 管理后台：信源/采集任务/审核/DLQ/审计切面
├── ai            # AI 加工：三段式中文摘要、分类归一化、英文翻译
├── common        # 通用工具（文本归一化等）
├── config        # MQ 拓扑、调度等配置
├── content       # 资讯查询/全文搜索(ngram)/相关内容/事件时间线
├── dedup         # 内容去重（URL/标题指纹）
├── feed          # /api/me/feed 四 Tab 个人资讯流 + 已读状态
├── processing    # raw.item.fetched 消息消费与入库
├── push          # 订阅匹配引擎、频控去重、站内消息
├── scheduler     # 信源调度状态（可接 XXL-JOB）
├── security      # JWT 过滤器、SecurityConfig、IAM SPI
├── subscription  # 订阅规则 CRUD
├── supplychain   # 供应链看板（指标/库存/贸易/供应商）
└── user          # 用户管理（RBAC）
```

## 快速启动

```bash
mvn clean package -DskipTests
# 配置环境变量（模板见《管理员初始化指南》4.2 节）
source .env
java -jar target/ums-main-0.1.0-SNAPSHOT.jar
```

- 健康检查：`GET http://localhost:8080/actuator/health`
- RabbitMQ 未就绪时调试：追加 `--spring.rabbitmq.listener.simple.auto-startup=false`
- 首次启动 Flyway 自动建表（V1–V6）并写入种子数据（admin 用户、信源、供应链演示数据）

## 配置要点

全部通过环境变量注入（占位符在 `application.yml`）：

| 变量组 | 说明 |
|--------|------|
| `UMS_MYSQL_*` | 数据库连接（库名 cp_news） |
| `UMS_MQ_*` | RabbitMQ 连接 |
| `UMS_JWT_SECRET` / `UMS_JWT_TTL_MINUTES` | 签名密钥（≥32 字节）与有效期 |
| `UMS_AUTH_PROVIDER` / `UMS_IAM_*` | LOCAL / IAM 认证切换（IAM 变量示例见 `.env.iam.example`） |
| `UMS_AI_*` | 智谱 API（key 仅走环境变量 `UMS_AI_API_KEY`） |
| `UMS_MGMT_*` | RabbitMQ 管理台（DLQ 重放用） |
| `UMS_CRAWLER_URL` | Python 采集服务地址（触发抓取） |

## 文档

- [接口文档](../../储能资讯平台接口文档_v1.0.md) — 37 端点契约与设计理由
- [管理员初始化指南](../../储能资讯平台管理员初始化指南_v1.0.md) — 部署与环境变量全量说明
- [部署运维文档](../../储能资讯平台部署运维文档_v1.0.md) — 日常运维/故障排查
