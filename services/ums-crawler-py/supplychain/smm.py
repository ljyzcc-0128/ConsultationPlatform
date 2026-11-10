"""SMM 上海有色网采集（price.smm.cn / hq.smm.cn，登录墙后数据服务）。

接口契约（2026-11 实测探测）：
- 历史报价/库存表 GET {base}/history2/data_table?type={type}&start_time=yyyy-MM-dd&end_time=yyyy-MM-dd[&tableIDs=...]
- 未登录请求被重定向到登录 HTML 页（非 JSON）→ 以响应 Content-Type 判定登录态失效
- ⚠ 登录墙：账号自动登录大概率被验证码/前端加密拦截，主路径是用户在浏览器登录
  hq.smm.cn 后导出会话 cookie（F12 → Network → 复制 Cookie 请求头）填入环境变量
  SMM_COOKIE（格式 "k1=v1; k2=v2"）。响应结构需在拿到有效 cookie 后核对一次，
  本模块按候选字段名动态匹配解析（对齐 gfex.py 的 FIELDS 防改名范式），raw 全量上送。

指标清单（用户需求）：铜/铝现货均价；钢材 SPA-H/Q235B/镀锌板/冷轧板/热轧板 周/月均价；
电池级碳酸锂价格；SMM 社会面碳酸锂库存；碳酸锂盐厂排产。
metric 编码即 Java 侧 series code（cp_indicator_series.code），单一事实源在本文件。
"""
import logging
from dataclasses import dataclass
from datetime import date as date_cls, timedelta

import httpx

from supplychain.publisher import QuoteEntry

log = logging.getLogger(__name__)

DATA_TABLE_PATH = '/history2/data_table'

# 字段候选名（防官方改名：按特征逐个尝试；表头可能是中文）
FIELDS = {
    'date': ('date', 'time', 'data_date', 'dataDate', '日期'),
    'value': ('price', 'avg', 'value', 'avgPrice', 'price_avg', '均价', 'average'),
    'name': ('name', 'product', 'pdtName', 'productName', '品名', '产品'),
}

# 周期 → 查询区间起点回看天数（周均价回看 6 天、月均价回看当月 1 日）
PERIOD_LOOKBACK = {'daily': 0, 'weekly': 6, 'monthly': None}  # monthly 特殊处理月初

# 指标定义（code → 定义）；table_ids 依赖账号权限，运行时由 SMM_TABLE_IDS 覆盖
METRICS = {
    'smm-cu-spot': {'name': 'SMM 电解铜现货均价', 'unit': '元/吨', 'period': 'daily',
                    'type': 'settlement', 'keyword': '铜'},
    'smm-al-spot': {'name': 'SMM A00 铝现货均价', 'unit': '元/吨', 'period': 'daily',
                    'type': 'settlement', 'keyword': '铝'},
    'smm-spah-weekly': {'name': 'SPA-H 耐候钢周均价（SMM）', 'unit': '元/吨', 'period': 'weekly',
                        'type': 'settlement', 'keyword': 'SPA-H'},
    'smm-q235b-weekly': {'name': 'Q235B 钢材周均价（SMM）', 'unit': '元/吨', 'period': 'weekly',
                         'type': 'settlement', 'keyword': 'Q235B'},
    'smm-galvanized-weekly': {'name': '镀锌板周均价（SMM）', 'unit': '元/吨', 'period': 'weekly',
                              'type': 'settlement', 'keyword': '镀锌'},
    'smm-coldrolled-weekly': {'name': '冷轧板周均价（SMM）', 'unit': '元/吨', 'period': 'weekly',
                              'type': 'settlement', 'keyword': '冷轧'},
    'smm-hotrolled-weekly': {'name': '热轧板周均价（SMM）', 'unit': '元/吨', 'period': 'weekly',
                             'type': 'settlement', 'keyword': '热轧'},
    'smm-lc-price': {'name': '电池级碳酸锂现货价格（SMM）', 'unit': '元/吨', 'period': 'daily',
                     'type': 'settlement', 'keyword': '碳酸锂'},
    'smm-lc-inventory': {'name': 'SMM 社会面碳酸锂库存', 'unit': '万吨', 'period': 'weekly',
                         'type': 'kc-normal', 'keyword': '碳酸锂'},
    'smm-lc-output': {'name': '碳酸锂盐厂排产（SMM）', 'unit': '吨', 'period': 'monthly',
                      'type': 'settlement', 'keyword': '排产'},
}


class SmmLoginError(Exception):
    """登录态失效（cookie 过期/未配置）——调用方记为采集失败，不 crash"""


@dataclass
class SmmSession:
    """SMM 会话凭据与建立过程告警"""
    headers: dict
    warnings: list


def _parse_table_ids(raw: str) -> dict:
    """SMM_TABLE_IDS 解析："smm-cu-spot:101,smm-al-spot:102" → {code: tableID}"""
    mapping = {}
    for pair in (raw or '').split(','):
        if ':' in pair:
            code, tid = pair.split(':', 1)
            mapping[code.strip()] = tid.strip()
    return mapping


async def build_session(client: httpx.AsyncClient,
                        username: str, password: str, cookie: str) -> SmmSession:
    """构建 SMM 会话：cookie 注入优先；否则账号密码自动登录（大概率被验证码拦截，失败仅告警）"""
    warnings = []
    if cookie.strip():
        return SmmSession(headers={'Cookie': cookie.strip()}, warnings=warnings)

    if username and password:
        try:
            # SMM 账号中心登录（user.smm.cn）；若站点启用验证码/JS 加密则此路不通
            resp = await client.post(
                'https://user.smm.cn/login',
                data={'username': username, 'password': password},
                headers={'User-Agent': 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) '
                                       'AppleWebKit/537.36', 'Referer': 'https://user.smm.cn/'},
            )
            resp.raise_for_status()
            session_cookie = resp.headers.get('set-cookie', '')
            if session_cookie:
                return SmmSession(headers={'Cookie': session_cookie}, warnings=warnings)
            warnings.append('SMM 自动登录未返回会话 cookie（站点可能有验证码），'
                            '请改用 SMM_COOKIE 注入')
        except Exception as exc:
            warnings.append(f'SMM 自动登录失败: {str(exc)[:200]}（站点可能有验证码/前端加密），'
                            '请改用 SMM_COOKIE 注入')
    else:
        warnings.append('未配置 SMM_COOKIE/SMM_USERNAME，请在浏览器登录 hq.smm.cn 后'
                        '导出 cookie 填入 SMM_COOKIE 环境变量')
    return SmmSession(headers={}, warnings=warnings)


def _pick(row: dict, key: str):
    """动态字段取值：按候选名依次尝试，防官方字段改名"""
    for name in FIELDS[key]:
        if name in row:
            return row[name]
    return None


def _to_float(v) -> float | None:
    """容忍字符串数字/区间（"78950-79050" 取中值）/千分位逗号 → float"""
    if v is None or v == '':
        return None
    text = str(v).replace(',', '').strip()
    if '-' in text and text.replace('-', '').replace('.', '').isdigit():
        parts = [float(p) for p in text.split('-') if p]
        return sum(parts) / len(parts) if parts else None
    try:
        return float(text)
    except ValueError:
        return None


def _query_range(target: date_cls, period: str) -> tuple[date_cls, date_cls]:
    """周期 → 查询区间 [start, end]（end=数据日期）"""
    lookback = PERIOD_LOOKBACK.get(period, 0)
    if lookback is None:  # monthly：当月 1 日起
        start = target.replace(day=1)
    else:
        start = target - timedelta(days=lookback)
    return start, target


def parse_data_table(raw: dict, metric_code: str, metric: dict,
                     target: date_cls) -> list:
    """解析 data_table 响应行 → QuoteEntry 列表（0 或 1 条）。

    区间返回多行日值时自行聚合为周期值（周/月均价 = 算术平均）；
    品种行按 keyword 过滤，全部行名不含关键词时兜底用全部行（防改名/整表口径）。
    区间内无有效数值行返回 []（非交易日/数据未生成 → 调用方回溯）。
    """
    rows = raw.get('data') or raw.get('rows') or raw.get('list') or []
    if not isinstance(rows, list):
        raise ValueError('响应缺少数据行列表（结构需核对）')

    valid = []  # [(行名, 值, 原始行)]
    for row in rows:
        if not isinstance(row, dict):
            continue
        value = _to_float(_pick(row, 'value'))
        if value is None:
            continue
        valid.append((str(_pick(row, 'name') or ''), value, row))

    if not valid:
        return []

    keyword = metric.get('keyword')
    if keyword:
        matched = [v for v in valid if keyword in v[0] or keyword in str(v[2])]
        if matched:
            valid = matched
        else:
            log.warning('[smm-%s] 行名未匹配关键词 %s，兜底用全部 %d 行',
                        metric_code, keyword, len(valid))

    values = [v[1] for v in valid]
    agg_value = values[0] if len(values) == 1 else sum(values) / len(values)
    return [QuoteEntry(metric=metric_code, metric_name=metric['name'],
                       period=metric.get('period', 'daily'),
                       data_date=target.isoformat(),
                       value=round(agg_value, 2), unit=metric['unit'], region='',
                       raw={'rows_returned': len(values), 'last_row': valid[-1][2]})]


async def fetch_metric(client: httpx.AsyncClient, session: SmmSession, base_url: str,
                       metric_code: str, metric: dict, table_ids: dict,
                       target: date_cls) -> list:
    """请求单指标数据表 → QuoteEntry 列表；HTTP 异常/登录墙统一抛出"""
    start, end = _query_range(target, metric.get('period', 'daily'))
    params = {
        'type': metric.get('type', 'settlement'),
        'start_time': start.isoformat(),
        'end_time': end.isoformat(),
    }
    if metric_code in table_ids:
        params['tableIDs'] = table_ids[metric_code]

    resp = await client.get(base_url.rstrip('/') + DATA_TABLE_PATH, params=params,
                            headers={**session.headers,
                                     'User-Agent': 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) '
                                                   'AppleWebKit/537.36',
                                     'Referer': base_url.rstrip('/') + '/history2',
                                     'X-Requested-With': 'XMLHttpRequest'})
    resp.raise_for_status()
    content_type = resp.headers.get('content-type', '')
    if 'json' not in content_type.lower():
        # 未登录/cookie 失效时站点返回登录页 HTML（302 跟随后 200）
        raise SmmLoginError('接口返回非 JSON（登录态失效），请更新 SMM_COOKIE')
    return parse_data_table(resp.json(), metric_code, metric, target)
