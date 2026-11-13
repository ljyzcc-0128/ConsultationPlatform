"""SMM 上海有色网采集（hq.smm.cn / price.smm.cn，登录墙后数据服务）。

登录协议（2026-11-12 逆向官方登录 SDK bundle 并实测验证，详见 settings.py 注释）：
- POST {SMM_LOGIN_URL}（platform.smm.cn/usercenter/4.0/auth）
  表单 user_name / password=md5(明文) / source=web / device=UUID / oa=01xwb /
  source_link / user_agent / ip → 成功直接返回 data.token（JWT）
- token 注入 Cookie: SMM_auth_token={token}；数据接口同时接受 ?token= 参数
- token 会被站点 refresh 轮换作废 → 每次采集现取现用，禁止跨进程缓存

数据接口：
- 现货结算价历史 GET {SMM_SETTLEMENT_URL}?token=..&type=..&start_time=..&end_time=..
- 业务码非 0 视为失败（10066=访问设备/权限受限，10004=账号密码错误）
- 会话凭据三路（优先级从高到低）：
  1) SMM_COOKIE：浏览器登录后导出的会话 cookie（主路径）
  2) SMM_USERNAME/SMM_PASSWORD：4.0/auth 一步认证
  3) 认证失败/未配置仅告警降级，不阻塞采集链路

指标清单（用户需求）：铜/铝现货均价；钢材 SPA-H/Q235B/镀锌板/冷轧板/热轧板 周/月均价；
电池级碳酸锂价格；SMM 社会面碳酸锂库存；碳酸锂盐厂排产。
metric 编码即 Java 侧 series code（cp_indicator_series.code），单一事实源在本文件。
"""
import hashlib
import logging
import os
import uuid
from dataclasses import dataclass
from datetime import date as date_cls, timedelta

import httpx

from settings import (SMM_BASE_URL, SMM_CLIENT_IP, SMM_CLIENT_UA, SMM_DEVICE_FILE,
                      SMM_DEVICE_ID, SMM_LOGIN_URL, SMM_OA, SMM_PASSWORD,
                      SMM_SETTLEMENT_URL, SMM_UA, SMM_USERNAME)

from supplychain.publisher import QuoteEntry

log = logging.getLogger(__name__)

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
    """登录态失效（cookie 过期/认证失败/权限受限）——调用方记为采集失败，不 crash"""


@dataclass
class SmmSession:
    """SMM 会话凭据与建立过程告警；token 由 4.0/auth 现取现用"""
    headers: dict
    warnings: list
    token: str = ''


def _parse_table_ids(raw: str) -> dict:
    """SMM_TABLE_IDS 解析："smm-cu-spot:101,smm-al-spot:102" → {code: tableID}"""
    mapping = {}
    for pair in (raw or '').split(','):
        if ':' in pair:
            code, tid = pair.split(':', 1)
            mapping[code.strip()] = tid.strip()
    return mapping


def _mask(v: str) -> str:
    """敏感字段脱敏：仅保留前 4 位，避免 token/凭据泄露到日志"""
    v = str(v or '')
    return v[:4] + '****' if len(v) > 4 else '****'


def _resolve_device_id() -> str:
    """设备 UUID：优先环境变量；否则读/写持久化文件，保证容器重启不变（设备绑定计数）"""
    if SMM_DEVICE_ID.strip():
        return SMM_DEVICE_ID.strip()
    try:
        if os.path.exists(SMM_DEVICE_FILE):
            return open(SMM_DEVICE_FILE).read().strip()
        dev = str(uuid.uuid4())
        with open(SMM_DEVICE_FILE, 'w') as f:
            f.write(dev)
        log.info('[smm-auth] 已生成设备 UUID 并持久化到 %s（建议固化为 SMM_DEVICE_ID '
                 '环境变量）', SMM_DEVICE_FILE)
        return dev
    except OSError as exc:
        log.warning('[smm-auth] 设备文件读写失败(%s)，本次用临时 UUID', exc)
        return str(uuid.uuid4())


def _log_http(resp: httpx.Response, sent_data: dict | None = None) -> None:
    """打印 SMM 网站请求/响应执行日志：完整 URL、请求头（脱敏）、请求体、响应体（截断）"""
    try:
        req = resp.request
        log.info('[smm-http] >>> %s %s', req.method, req.url)
        hdrs = {k: (_mask(v) if k.lower() in ('cookie', 'authorization') else v)
                for k, v in req.headers.items()}
        log.info('[smm-http] >>> 请求头: %s', hdrs)
        if sent_data is not None:
            log.info('[smm-http] >>> 请求体: %s', sent_data)
        body = resp.text[:500]
        log.info('[smm-http] <<< 响应 HTTP %s content-type=%s body=%s',
                 resp.status_code, resp.headers.get('content-type', ''), body)
    except Exception as exc:  # 日志打印自身失败不影响采集
        log.warning('[smm-http] 日志打印异常: %s', exc)


async def _api_login(client: httpx.AsyncClient, username: str, password: str,
                     warnings: list) -> str | None:
    """SMM 4.0/auth 一步认证：md5 密码换取 JWT token（现取现用）。
    失败仅追加告警返回 None（不阻塞采集链路）。"""
    device_id = _resolve_device_id()
    login_data = {
        'user_name': username,
        'password': hashlib.md5(password.encode('utf-8')).hexdigest(),
        'source': 'web',
        'device': device_id,
        'oa': SMM_OA,
        'source_link': SMM_BASE_URL,
        'user_agent': SMM_CLIENT_UA,
        'ip': SMM_CLIENT_IP,
    }
    headers = {'User-Agent': SMM_UA,
               'Accept': 'application/json, text/plain, */*',
               'Origin': 'https://user.smm.cn',
               'Referer': 'https://user.smm.cn/login',
               'Cookie': f'SMM_device_id={device_id}'}
    try:
        log.info('[smm-auth] POST %s（user=%s）', SMM_LOGIN_URL, _mask(username))
        resp = await client.post(SMM_LOGIN_URL, data=login_data, headers=headers)
        _log_http(resp, sent_data={**login_data, 'password': _mask(login_data['password']),
                                   'user_name': _mask(username)})
        resp.raise_for_status()
        payload = resp.json()
        if payload.get('code') != 0 or not (payload.get('data') or {}).get('token'):
            msg = (f'SMM 登录失败 code={payload.get("code")} msg={payload.get("msg")} '
                   f'（10004=账号密码错误；每小时错误上限 10 次，勿连续重试）')
            log.warning('[smm-auth] %s', msg)
            warnings.append(msg)
            return None
        token = payload['data']['token']
        log.info('[smm-auth] 登录成功 token=%s', _mask(token))
        return token
    except Exception as exc:
        msg = f'SMM 登录异常: {str(exc)[:200]}'
        log.warning('[smm-auth] %s', msg)
        warnings.append(msg)
        return None


async def build_session(client: httpx.AsyncClient,
                        username: str, password: str, cookie: str) -> SmmSession:
    """构建 SMM 会话：cookie 注入优先；否则账号密码走 4.0/auth 现取 token；失败仅告警降级"""
    warnings: list = []
    if cookie.strip():
        log.info('[smm-auth] 使用 SMM_COOKIE 注入会话（cookie=%s）', _mask(cookie))
        return SmmSession(headers={'Cookie': cookie.strip()}, warnings=warnings)

    if username and password:
        token = await _api_login(client, username, password, warnings)
        if token:
            # Cookie: SMM_auth_token 为站点真实会话键（逆向登录 SDK 确认）
            return SmmSession(headers={'Cookie': f'SMM_auth_token={token}'},
                              warnings=warnings, token=token)
    else:
        msg = ('未配置 SMM_COOKIE/SMM_USERNAME，请在环境变量中提供 SMM 账号凭据')
        log.warning('[smm-auth] %s', msg)
        warnings.append(msg)
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
    """请求单指标现货结算价历史 → QuoteEntry 列表；HTTP 异常/登录墙统一抛出。

    真实数据端点（2026-11-12 浏览器嗅探）：platform.smm.cn/spotcenter/v31/
    settlement/history/prices，鉴权走 ?token=（SmmSession.token 由 4.0/auth 现取）。
    若 token 缺失（SMM_COOKIE 主路径），回退 hq.smm.cn 旧 data_table 路径（可能已下线，
    由响应判别）。业务码非 0（10066=设备/权限受限）抛 SmmLoginError。
    """
    start, end = _query_range(target, metric.get('period', 'daily'))
    params = {
        'type': metric.get('type', 'settlement'),
        'start_time': start.isoformat(),
        'end_time': end.isoformat(),
    }
    if metric_code in table_ids:
        params['tableIDs'] = table_ids[metric_code]

    if session.token:
        # 主路径：spotcenter 数据接口 + token query 参数（与站点页面请求一致）
        headers = {**session.headers,
                   'User-Agent': SMM_UA,
                   'Referer': 'https://price.smm.cn/'}
        resp = await client.get(SMM_SETTLEMENT_URL,
                                params={**params, 'token': session.token},
                                headers=headers)
        _log_http(resp, sent_data={'query_params': {**params, 'token': _mask(session.token)}})
        content_type = resp.headers.get('content-type', '')
        if resp.status_code != 200 or 'json' not in content_type.lower():
            # 404/非 JSON：直连缺页面侧签名参数（AfbF5fe 由站点反爬 SDK 注入浏览器请求），
            # 端点参数需在数据权限开通后于浏览器内抓包补齐
            raise SmmLoginError(
                f'spotcenter 返回 HTTP {resp.status_code}（非 JSON）：直连请求缺少页面侧'
                '签名参数，需在数据权限开通后抓包确认完整参数后更新 SMM_SETTLEMENT_URL 配置')
        payload = resp.json()
        code = payload.get('code')
        if code not in (0, '0', None):
            raise SmmLoginError(f'spotcenter 业务失败 code={code} '
                                f'msg={payload.get("msg")}（10066=访问设备/权限受限，'
                                '需联系 SMM 客户经理确认账号数据权限/解绑设备）')
        return parse_data_table(payload, metric_code, metric, target)

    # 回退路径：SMM_COOKIE 主路径下走 hq.smm.cn 旧契约端点
    resp = await client.get(base_url.rstrip('/') + '/history2/data_table', params=params,
                            headers={**session.headers,
                                     'User-Agent': SMM_UA,
                                     'Referer': base_url.rstrip('/') + '/history2',
                                     'X-Requested-With': 'XMLHttpRequest'})
    _log_http(resp, sent_data={'query_params': params})
    resp.raise_for_status()
    content_type = resp.headers.get('content-type', '')
    if 'json' not in content_type.lower():
        # 未登录/cookie 失效时站点返回登录页 HTML（302 跟随后 200）
        raise SmmLoginError('接口返回非 JSON（登录态失效），请更新 SMM_COOKIE 或配置 '
                            'SMM_USERNAME/SMM_PASSWORD')
    return parse_data_table(resp.json(), metric_code, metric, target)
