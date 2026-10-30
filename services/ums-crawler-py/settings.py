"""环境变量配置（手册第十节：敏感配置通过环境变量注入，不写入代码仓库）"""
import os
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent

# RabbitMQ 连接串；留空则进入 stdout 降级模式（本地调试无 MQ 时消息打印到日志）
RABBITMQ_URL = os.environ.get('RABBITMQ_URL', '')
MQ_EXCHANGE = os.environ.get('MQ_EXCHANGE', 'ums')
MQ_ROUTING_KEY = os.environ.get('MQ_ROUTING_KEY', 'raw.item.fetched')

# Java 主服务内部接口地址（手册第一节：sources 配置存在 Java 侧 MySQL，
# 通过 GET /internal/sources/{source_code} 获取；未配置时使用本地 selectors YAML）
INTERNAL_API_BASE_URL = os.environ.get('INTERNAL_API_BASE_URL', '')

SELECTORS_DIR = Path(os.environ.get('SELECTORS_DIR', BASE_DIR / 'selectors'))

# 供应链采集（广期所仓单日报）：公开 JSON 接口，POST 表单按品种+交易日查询
GFEX_BASE_URL = os.environ.get('GFEX_BASE_URL', 'http://www.gfex.com.cn')
GFEX_VARIETIES = [v.strip() for v in os.environ.get('GFEX_VARIETIES', 'lc,si,ps').split(',') if v.strip()]
# 非交易日回溯最近交易日的最大天数（长假兜底）
GFEX_MAX_ROLLBACK_DAYS = int(os.environ.get('GFEX_MAX_ROLLBACK_DAYS', '10'))
# 供应链消息独立 routing key（与资讯 raw.item.fetched 共用 ums exchange）
MQ_ROUTING_KEY_WBILL = os.environ.get('MQ_ROUTING_KEY_WBILL', 'supplychain.wbill.fetched')

# 触发接口监听（XXL-JOB HTTP 任务类型调用 POST /internal/trigger/{source_code}）
HOST = os.environ.get('CRAWLER_HOST', '0.0.0.0')
PORT = int(os.environ.get('CRAWLER_PORT', '8100'))

REQUEST_TIMEOUT_SEC = int(os.environ.get('REQUEST_TIMEOUT_SEC', '20'))
DEFAULT_REQUEST_INTERVAL_SEC = float(os.environ.get('DEFAULT_REQUEST_INTERVAL_SEC', '2'))
