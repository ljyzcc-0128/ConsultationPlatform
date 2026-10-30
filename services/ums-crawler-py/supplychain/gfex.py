"""广期所仓单日报采集（官网公开 JSON 接口，无需鉴权）。

接口契约（2026-10 实测锁定，动态字段匹配防官方改名）：
- POST {base}/u/interfacesWebTdWbillWeeklyQuotes/loadList
- 表单：gen_date=yyyyMMdd（交易日）、variety=品种代码（lc/si/ps）
- 响应 data 行分三类：
  1) 总计行   variety='总计'    whAbbr=''        —— 今日/昨日仓单量与增减
  2) 小计行   variety='工业硅小计' whAbbr=''      —— 与总计等值，忽略
  3) 明细行   whAbbr 非空（含商标行 trademarkName）
- 有效性判定：总计行 wbillQty 非 null（非交易日/未生成时为 null）
- 一致性：wbillQty = lastWbillQty + diff；过滤空仓库行后 sum(明细)=总计
"""
import logging
from dataclasses import dataclass, field
from datetime import date as date_cls

import httpx

log = logging.getLogger(__name__)

# 响应行三类标记（按值匹配，非字段名匹配）
TOTAL_LABEL = '总计'

# 字段候选名（防官方改名：按特征逐个尝试）
FIELDS = {
    'today': ('wbillQty', 'regWbillQty'),
    'yesterday': ('lastWbillQty', 'preWbillQty'),
    'diff': ('diff', 'qtyDiff'),
    'warehouse': ('whAbbr', 'warehouseName', 'whName'),
    'trademark': ('trademarkName', 'brandName'),
    'variety_name': ('variety',),
}

# 品种中文名兜底（明细行缺失时用）
VARIETY_NAMES = {'lc': '碳酸锂', 'si': '工业硅', 'ps': '多晶硅'}

URL_PATH = '/u/interfacesWebTdWbillWeeklyQuotes/loadList'


@dataclass
class WbillEntry:
    """一个品种一个交易日的仓单数据（消息 entries 元素）"""
    variety: str                 # 品种代码（lc/si/ps）
    variety_name: str            # 品种中文名
    trade_date: str              # 交易日 yyyy-MM-dd
    last_qty: int                # 昨日仓单量（手）
    qty: int                     # 今日仓单量（手）
    diff: int                    # 增减（手）
    warehouses: list = field(default_factory=list)  # [{wh_name, trademark, last_qty, qty, diff}]


def _pick(row: dict, key: str):
    """动态字段取值：按候选名依次尝试，防官方字段改名"""
    for name in FIELDS[key]:
        if name in row:
            return row[name]
    return None


def _to_int(v) -> int:
    """容忍字符串数字/空串/None → int（官网返回值可能是字符串）"""
    if v is None or v == '':
        return 0
    return int(str(v).strip())


def parse_wbill_response(raw: dict, variety: str, trade_date: date_cls) -> WbillEntry | None:
    """解析接口响应 → WbillEntry；无数据（总计行 null）返回 None（触发回溯）。

    一致性校验失败抛 ValueError（调用方计为该品种失败，不中断其他品种）。
    """
    rows = raw.get('data') or []
    total_row = next((r for r in rows if _pick(r, 'warehouse') in ('', None)
                      and r.get('variety') == TOTAL_LABEL), None)
    if total_row is None:
        # 兜底：无 '总计' 标签时取首个空仓库行
        total_row = next((r for r in rows if _pick(r, 'warehouse') in ('', None)), None)
    if total_row is None:
        raise ValueError('响应缺少总计行')

    qty_raw, last_raw, diff_raw = (_pick(total_row, 'today'),
                                   _pick(total_row, 'yesterday'),
                                   _pick(total_row, 'diff'))
    if qty_raw is None:
        return None  # 非交易日/数据未生成（总计行字段为 null）

    qty, last_qty, diff = _to_int(qty_raw), _to_int(last_raw), _to_int(diff_raw)
    if qty != last_qty + diff:
        raise ValueError(f'一致性校验失败: 今日{qty} != 昨日{last_qty}+增减{diff}')

    detail_rows = [r for r in rows if _pick(r, 'warehouse') not in ('', None)]
    sum_qty = sum(_to_int(_pick(r, 'today')) for r in detail_rows)
    if detail_rows and sum_qty != qty:
        log.warning('[gfex-%s] 明细汇总(%d)与总计(%d)不一致，以总计为准', variety, sum_qty, qty)

    variety_name = next((r.get('variety') for r in detail_rows if r.get('variety')),
                        VARIETY_NAMES.get(variety, variety))
    warehouses = [{
        'wh_name': _pick(r, 'warehouse') or '',
        'trademark': _pick(r, 'trademark') or '',
        'last_qty': _to_int(_pick(r, 'yesterday')),
        'qty': _to_int(_pick(r, 'today')),
        'diff': _to_int(_pick(r, 'diff')),
    } for r in detail_rows]

    return WbillEntry(variety=variety, variety_name=variety_name,
                      trade_date=trade_date.isoformat(),
                      last_qty=last_qty, qty=qty, diff=diff, warehouses=warehouses)


async def fetch_wbill(client: httpx.AsyncClient, base_url: str,
                      variety: str, trade_date: date_cls) -> WbillEntry | None:
    """请求单品种单交易日仓单数据；HTTP/结构异常统一抛出，由调用方处理。

    UA 必须伪装浏览器：官网 WAF 拦截 python-httpx 默认 UA（返回 520）。
    """
    resp = await client.post(
        base_url.rstrip('/') + URL_PATH,
        data={'gen_date': trade_date.strftime('%Y%m%d'), 'variety': variety},
        headers={
            'User-Agent': 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36',
            'Referer': f'{base_url.rstrip("/")}/gfex/cdrb/hqsj_tjsj.shtml',
            'X-Requested-With': 'XMLHttpRequest',
        },
    )
    resp.raise_for_status()
    raw = resp.json()
    if str(raw.get('code')) != '0':
        raise ValueError(f'接口返回异常 code={raw.get("code")} msg={raw.get("msg")}')
    return parse_wbill_response(raw, variety, trade_date)
