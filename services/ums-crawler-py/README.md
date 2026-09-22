# UMS Crawler — 储能资讯平台采集服务（Python）

多信源采集服务：抓取国内外储能行业资讯，产出 `raw.item.fetched` 消息发布到 RabbitMQ（**不直接落库**）。

## 技术栈

- Python 3.10+ / FastAPI + Uvicorn
- httpx（HTTP 采集）/ Playwright（JS 渲染站点）
- pika（RabbitMQ 发布）
- PyYAML（选择器配置）/ pytest（测试）

## 目录结构

```
├── api/         # FastAPI 服务（/healthz、/internal/trigger/{source_code}）
├── connectors/  # 连接器抽象基类（ItemRef / RawItemDraft / Connector）
├── parsing/     # 解析与字段清洗
├── mq/          # RabbitMQ 发布（不可用时降级输出 stdout）
├── selectors/   # 信源选择器配置（*.yaml，与代码解耦）
├── settings.py  # 配置
├── runner.py    # 采集执行入口
└── tests/       # 测试
```

## 快速启动

```bash
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
python -m uvicorn api.app:app --host 0.0.0.0 --port 8100
```

验证与触发：

```bash
curl http://127.0.0.1:8100/healthz
curl -X POST http://127.0.0.1:8100/internal/trigger/<source_code>
```

## 数据口径约定

- `publish_time` 缺失保留 `null`；纯日期按 UTC 午夜
- `original_url` 去除 `utm_*` 追踪参数
- 时间统一 UTC ISO 8601
- 消息字段严格遵循数据文档口径

## 行为约定

- RabbitMQ 未启动时，数据降级输出到 stdout 日志（不中断采集）
- WAF 按 TLS 指纹拦截的信源不做对抗性绕过（属预期行为）
- JS 渲染站点（Playwright）超时与占位等待放宽至 60s
- 调度可接 XXL-JOB（`UMS_XXL_JOB_*`，默认关闭），或由 Java 主服务经 `/internal/trigger` 调用

## 文档

- [管理员初始化指南](../../储能资讯平台管理员初始化指南_v1.0.md) — 第 6 章采集服务初始化
- [部署运维文档](../../储能资讯平台部署运维文档_v1.0.md) — 采集失败排查
