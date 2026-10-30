"""供应链采集编排：广期所仓单采集 → 非交易日回溯 → 发布 MQ。

只采集与发布，不落库（数据加工在 Java 消费者：校验→upsert→异动事件→采集日志）。
"""
import logging
import uuid
from datetime import date, datetime, timedelta, timezone

import httpx

from settings import GFEX_BASE_URL, GFEX_MAX_ROLLBACK_DAYS, GFEX_VARIETIES
from supplychain.gfex import fetch_wbill
from supplychain.publisher import WbillPublisher, build_message

log = logging.getLogger(__name__)


def parse_trade_date(value: str | None) -> date:
    """交易日解析：支持 yyyy-MM-dd 与 yyyyMMdd；缺省=今天（北京时间口径）"""
    if not value:
        return datetime.now(timezone.utc).astimezone(
            timezone(timedelta(hours=8))).date()
    for fmt in ('%Y-%m-%d', '%Y%m%d'):
        try:
            return datetime.strptime(value, fmt).date()
        except ValueError:
            continue
    raise ValueError(f'交易日格式不合法: {value}（支持 yyyy-MM-dd / yyyyMMdd）')


def _iso(dt: datetime) -> str:
    return dt.astimezone(timezone.utc).isoformat().replace('+00:00', 'Z')


async def run_gfex_collection(trade_date: date | None = None,
                              varieties: list[str] | None = None,
                              trigger_type: str = 'MANUAL',
                              publisher: WbillPublisher | None = None) -> dict:
    """执行一次广期所仓单采集：非交易日自动回溯最近交易日。

    返回 {task_id, fetched, published, failed, errors, ...}（HTTP 触发响应口径）。
    """
    task_id = uuid.uuid4().hex
    started = datetime.now(timezone.utc)
    wanted = [v.strip().lower() for v in (varieties or GFEX_VARIETIES) if v.strip()]
    target = trade_date or parse_trade_date(None)

    entries, errors = [], []
    async with httpx.AsyncClient(timeout=20) as client:
        for variety in wanted:
            entry = None
            for offset in range(GFEX_MAX_ROLLBACK_DAYS):
                probe = target - timedelta(days=offset)
                try:
                    entry = await fetch_wbill(client, GFEX_BASE_URL, variety, probe)
                except Exception as exc:
                    errors.append({'variety': variety, 'trade_date': probe.isoformat(),
                                   'error': str(exc)[:300]})
                    log.warning('[gfex-%s] %s 抓取失败: %s', variety, probe, exc)
                    break  # 请求层异常不回溯（接口/网络故障，重试无意义）
                if entry is not None:
                    if offset > 0:
                        log.info('[gfex-%s] %s 非交易日，回溯至最近交易日 %s',
                                 variety, target, probe)
                    break
                # 无数据 → 前一天再试
            else:
                errors.append({'variety': variety, 'trade_date': target.isoformat(),
                               'error': f'连续 {GFEX_MAX_ROLLBACK_DAYS} 天无数据'})
            if entry is not None:
                entries.append(entry)

    finished = datetime.now(timezone.utc)
    message = build_message(task_id, entries, errors, trigger_type,
                            _iso(started), _iso(finished))
    publisher = publisher or WbillPublisher()
    if publisher._exchange is None and publisher.mq_url:
        await publisher.connect()
    mode = 'mq' if publisher._exchange is not None else 'stdout'
    try:
        await publisher.publish(message)
        published = 1
    except Exception as exc:
        published = 0
        log.exception('仓单消息发布失败 task_id=%s', task_id)
    finally:
        await publisher.close()

    summary = {
        'task_id': task_id,
        'source_code': 'supplychain/gfex',
        'source_name': '广期所',
        'trigger_type': trigger_type,
        'started_at': _iso(started),
        'finished_at': _iso(finished),
        'trade_date': target.isoformat(),
        'fetched': len(entries),
        'published': published,
        'failed': len(errors),
        'errors': errors[:20],
        'mode': mode,
    }
    log.info('[gfex] 采集完成: %s', {k: summary[k] for k in
                                    ('task_id', 'fetched', 'failed', 'published', 'mode')})
    return summary
