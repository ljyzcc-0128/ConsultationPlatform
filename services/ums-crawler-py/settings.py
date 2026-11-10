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
# 广期所仓单采集品种清单（2026-11-10 按客户要求只采集碳酸锂 lc）。
# 后续如需恢复其他品种，把对应代码加回环境变量 GFEX_VARIETIES 即可（逗号分隔）：
#   si=工业硅  ps=多晶硅  lc=碳酸锂
# GFEX_VARIETIES = 'lc,si,ps'
GFEX_VARIETIES = [v.strip() for v in os.environ.get('GFEX_VARIETIES', 'lc').split(',') if v.strip()]
# 非交易日回溯最近交易日的最大天数（长假兜底）
GFEX_MAX_ROLLBACK_DAYS = int(os.environ.get('GFEX_MAX_ROLLBACK_DAYS', '10'))
# 单次采集回填窗口（自然日）：向前抓 N 天全部交易日数据，非交易日接口无数据自动跳过，
# 供前端「横轴日期-纵轴仓单量」曲线展示；Java 侧按 (品种, 日期) 幂等去重，可重复触发
GFEX_BACKFILL_DAYS = int(os.environ.get('GFEX_BACKFILL_DAYS', '30'))
# 供应链消息独立 routing key（与资讯 raw.item.fetched 共用 ums exchange）
MQ_ROUTING_KEY_WBILL = os.environ.get('MQ_ROUTING_KEY_WBILL', 'supplychain.wbill.fetched')
MQ_ROUTING_KEY_SMM = os.environ.get('MQ_ROUTING_KEY_SMM', 'supplychain.smm.fetched')
MQ_ROUTING_KEY_SCI99 = os.environ.get('MQ_ROUTING_KEY_SCI99', 'supplychain.sci99.fetched')

# SMM 上海有色网（price.smm.cn/hq.smm.cn）：登录墙后数据服务。
# 账号密码走自动登录（站点大概率有验证码/前端加密，成功率低），cookie 注入是兜底主路径：
# 用户在浏览器登录后导出会话 cookie（含 smm_token 等键）填入 SMM_COOKIE，格式 "k1=v1; k2=v2"
SMM_BASE_URL = os.environ.get('SMM_BASE_URL', 'https://hq.smm.cn')
SMM_USERNAME = os.environ.get('SMM_USERNAME', '')
SMM_PASSWORD = os.environ.get('SMM_PASSWORD', '')
SMM_COOKIE = os.environ.get('SMM_COOKIE', '')
# SMM 结算表 tableID 映射（metric_code:tableID 逗号分隔，如 smm-cu-spot:101,smm-al-spot:102）。
# tableIDs 因账号权限而异，首次运行前需登录 hq.smm.cn/history2 抓包确认后配置
SMM_TABLE_IDS = os.environ.get('SMM_TABLE_IDS', '')

# 卓创资讯（sci99.com）：早间提示正文需登录。凭据三路（优先级从高到低）：
# 1) SCI99_COOKIE：浏览器登录后导出的会话 cookie（格式 "k1=v1; k2=v2"）
# 2) SCI99_USERNAME/SCI99_PASSWORD：Playwright 自动登录（抓取要求文档 4.3 选型）
# 3) 均未配置时任务报错不 crash
SCI99_BASE_URL = os.environ.get('SCI99_BASE_URL', 'https://news.sci99.com')
SCI99_COOKIE = os.environ.get('SCI99_COOKIE', '')
SCI99_USERNAME = os.environ.get('SCI99_USERNAME', '')
SCI99_PASSWORD = os.environ.get('SCI99_PASSWORD', '')
# 氮气早间提示来源页（工业气体分站氮气产品页，含每日「液氮市场早间提示」文章链接）
SCI99_MORNING_URL = os.environ.get(
    'SCI99_MORNING_URL', 'https://ig.sci99.com/product/nitrogen')
# 用户中心登录页（账号密码 tab）
SCI99_LOGIN_URL = os.environ.get(
    'SCI99_LOGIN_URL', 'https://ucenter.sci99.com/#/login/?backurl=https%3A%2F%2Fwww.sci99.com%2F')
# 登录态持久化（Playwright Storage State），避免每次采集都重新登录
SCI99_STORAGE_FILE = os.environ.get(
    'SCI99_STORAGE_FILE', str(Path.home() / '.ums-crawler' / 'sci99_storage.json'))

# 触发接口监听（XXL-JOB HTTP 任务类型调用 POST /internal/trigger/{source_code}）
HOST = os.environ.get('CRAWLER_HOST', '0.0.0.0')
PORT = int(os.environ.get('CRAWLER_PORT', '8100'))

REQUEST_TIMEOUT_SEC = int(os.environ.get('REQUEST_TIMEOUT_SEC', '20'))
DEFAULT_REQUEST_INTERVAL_SEC = float(os.environ.get('DEFAULT_REQUEST_INTERVAL_SEC', '2'))
