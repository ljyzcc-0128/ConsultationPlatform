"""供应链采集编排：广期所/SMM/卓创 采集 → 发布 MQ。

只采集与发布，不落库（数据加工在 Java 消费者：校验→upsert→异动事件→采集日志）。
"""
import logging
import uuid
from datetime import date, datetime, timedelta, timezone

import httpx

from settings import (GFEX_BACKFILL_DAYS, GFEX_BASE_URL, GFEX_VARIETIES,
                      MQ_ROUTING_KEY_SCI99, MQ_ROUTING_KEY_SMM,
                      SCI99_BASE_URL, SCI99_COOKIE, SCI99_MORNING_URL,
                      SCI99_PASSWORD, SCI99_USERNAME,
                      SMM_BASE_URL, SMM_COOKIE, SMM_PASSWORD, SMM_TABLE_IDS,
                      SMM_USERNAME)
from supplychain.gfex import fetch_wbill
from supplychain.publisher import (QuotePublisher, WbillPublisher, build_message,
                                   build_quote_message)
from supplychain.sci99 import Sci99Error, fetch_n2_morning, login_cookie
from supplychain.smm import SmmLoginError, build_session, fetch_metric, METRICS, _parse_table_ids

log = logging.getLogger(__name__)

SOURCE_NAME_SMM = 'SMM上海有色网'
SOURCE_NAME_SCI99 = '卓创资讯'


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
    """执行一次广期所仓单采集：从目标日向前回填 GFEX_BACKFILL_DAYS 自然日内
    全部交易日数据（非交易日接口无数据自动跳过），供前端日期-数量曲线展示。

    返回 {task_id, fetched, published, failed, errors, ...}（HTTP 触发响应口径）。
    """
    task_id = uuid.uuid4().hex
    started = datetime.now(timezone.utc)
    wanted = [v.strip().lower() for v in (varieties or GFEX_VARIETIES) if v.strip()]
    target = trade_date or parse_trade_date(None)

    entries, errors = [], []
    async with httpx.AsyncClient(timeout=20) as client:
        for variety in wanted:
            got_any = False
            for offset in range(GFEX_BACKFILL_DAYS):
                probe = target - timedelta(days=offset)
                try:
                    entry = await fetch_wbill(client, GFEX_BASE_URL, variety, probe)
                except Exception as exc:
                    errors.append({'variety': variety, 'trade_date': probe.isoformat(),
                                   'error': str(exc)[:300]})
                    log.warning('[gfex-%s] %s 抓取失败: %s', variety, probe, exc)
                    break  # 请求层异常不回溯（接口/网络故障，重试无意义）
                if entry is not None:
                    got_any = True
                    entries.append(entry)
                # entry is None → 非交易日/数据未生成，继续向前探
            else:
                if not got_any:
                    errors.append({'variety': variety, 'trade_date': target.isoformat(),
                                   'error': f'回填窗口 {GFEX_BACKFILL_DAYS} 天内无数据'})
                continue
            if not got_any:
                errors.append({'variety': variety, 'trade_date': target.isoformat(),
                               'error': f'抓取中断，回填窗口 {GFEX_BACKFILL_DAYS} 天内未获数据'})
    # 按日期升序整理（同品种），消费端按 (品种,日期) 幂等，顺序仅影响日志可读性
    entries.sort(key=lambda e: (e.variety, e.trade_date))

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


SMM_DAILY_MAX_ROLLBACK_DAYS = 3  # SMM 每日指标非交易日回溯天数（价格每日发布，少量回溯即可）


async def run_smm_collection(trade_date: date | None = None,
                             metrics: list[str] | None = None,
                             period: str | None = None,
                             trigger_type: str = 'MANUAL',
                             publisher: QuotePublisher | None = None) -> dict:
    """执行一次 SMM 采集：登录态构建（cookie 注入优先）→ 逐指标抓取 → 发布 MQ。

    period（daily/weekly/monthly）按指标官网更新周期过滤，配合调度侧拆分的
    日/周/月任务实现「跟随官网更新频率」；缺省采集全部指标。
    登录失败不 crash：task 摘要标记 failed 并写明原因，同时发布空 entries 消息
    （Java 侧落 FAILED 采集任务日志，与 gfex「全失败也发布」口径一致）。
    返回 {task_id, fetched, published, failed, errors, ...}（HTTP 触发响应口径）。
    """
    task_id = uuid.uuid4().hex
    started = datetime.now(timezone.utc)
    wanted = [m.strip() for m in (metrics or list(METRICS)) if m.strip() in METRICS]
    if period:
        wanted = [m for m in wanted if METRICS[m].get('period') == period]
    target = trade_date or parse_trade_date(None)

    entries, errors = [], []
    async with httpx.AsyncClient(timeout=20, follow_redirects=True) as client:
        session = await build_session(client, SMM_USERNAME, SMM_PASSWORD, SMM_COOKIE)
        errors.extend({'metric': '', 'error': w} for w in session.warnings)

        for code in wanted:
            metric = METRICS[code]
            got = None
            for offset in range(1 if metric['period'] != 'daily' else SMM_DAILY_MAX_ROLLBACK_DAYS):
                probe = target - timedelta(days=offset)
                try:
                    got = await fetch_metric(client, session, SMM_BASE_URL, code, metric,
                                             _parse_table_ids(SMM_TABLE_IDS), probe)
                except SmmLoginError as exc:
                    # 登录墙影响全部指标，直接终止本轮
                    errors.append({'metric': code, 'error': str(exc)[:300]})
                    got = None
                    break
                except Exception as exc:
                    errors.append({'metric': code, 'error': str(exc)[:300]})
                    log.warning('[smm-%s] %s 抓取失败: %s', code, probe, exc)
                    break
                if got:
                    break
            else:
                errors.append({'metric': code, 'error': '回溯后仍无数据'})
            if got:
                if got[0].data_date != target.isoformat():
                    log.info('[smm-%s] %s 无当日数据，回溯至 %s', code, target, got[0].data_date)
                entries.extend(got)
            if any('登录态失效' in str(e.get('error', '')) for e in errors[-1:]):
                break  # cookie 失效后无需继续逐指标请求

    finished = datetime.now(timezone.utc)
    message = build_quote_message('supplychain.smm.fetched', SOURCE_NAME_SMM, task_id,
                                  entries, errors, trigger_type, _iso(started), _iso(finished))
    publisher = publisher or QuotePublisher(routing_key=MQ_ROUTING_KEY_SMM)
    if publisher._exchange is None and publisher.mq_url:
        await publisher.connect()
    mode = 'mq' if publisher._exchange is not None else 'stdout'
    try:
        await publisher.publish(message)
        published = 1
    except Exception as exc:
        published = 0
        log.exception('SMM 消息发布失败 task_id=%s', task_id)
    finally:
        await publisher.close()

    summary = {
        'task_id': task_id,
        'source_code': 'supplychain/smm',
        'source_name': SOURCE_NAME_SMM,
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
    log.info('[smm] 采集完成: %s', {k: summary[k] for k in
                                    ('task_id', 'fetched', 'failed', 'published', 'mode')})
    return summary


async def run_sci99_collection(trade_date: date | None = None,
                               trigger_type: str = 'MANUAL',
                               publisher: QuotePublisher | None = None) -> dict:
    """执行一次卓创氮气早间提示采集：cookie 注入 → 列表页 → 正文解析华东价格 → 发布 MQ。

    未配置/失效 SCI99_COOKIE 或解析失败均不 crash：task 摘要标记 failed 并写明原因，
    同时发布空 entries 消息保证 Java 侧落 FAILED 采集任务日志。
    """
    task_id = uuid.uuid4().hex
    started = datetime.now(timezone.utc)
    target = trade_date or parse_trade_date(None)

    entries, errors = [], []
    cookie = SCI99_COOKIE
    try:
        if not cookie.strip():
            # cookie 未配置 → 账号密码 Playwright 自动登录（登录态持久化复用）
            cookie = await login_cookie(SCI99_USERNAME, SCI99_PASSWORD)
    except Sci99Error as exc:
        errors.append({'metric': 'sci99-login', 'error': str(exc)[:300]})
        log.warning('[sci99] 自动登录失败: %s', exc)
    except Exception as exc:
        errors.append({'metric': 'sci99-login', 'error': str(exc)[:300]})
        log.warning('[sci99] 自动登录异常: %s', exc)

    if not cookie.strip():
        if not errors:
            errors.append({'metric': '', 'error': '未配置 SCI99_COOKIE/SCI99_USERNAME，'
                                                  '无法建立卓创登录态'})
    else:
        try:
            async with httpx.AsyncClient(timeout=20, follow_redirects=True) as client:
                # 华东区块逐市场一条（subject=市场名），单篇文章产出多条指标点
                market_entries = await fetch_n2_morning(client, cookie, SCI99_BASE_URL,
                                                        SCI99_MORNING_URL, target)
                entries.extend(market_entries)
        except Sci99Error as exc:
            errors.append({'metric': 'sci99-n2-eastchina', 'error': str(exc)[:300]})
            log.warning('[sci99] 抓取失败: %s', exc)
        except Exception as exc:
            errors.append({'metric': 'sci99-n2-eastchina', 'error': str(exc)[:300]})
            log.warning('[sci99] 抓取异常: %s', exc)

    finished = datetime.now(timezone.utc)
    message = build_quote_message('supplychain.sci99.fetched', SOURCE_NAME_SCI99, task_id,
                                  entries, errors, trigger_type, _iso(started), _iso(finished))
    publisher = publisher or QuotePublisher(routing_key=MQ_ROUTING_KEY_SCI99)
    if publisher._exchange is None and publisher.mq_url:
        await publisher.connect()
    mode = 'mq' if publisher._exchange is not None else 'stdout'
    try:
        await publisher.publish(message)
        published = 1
    except Exception as exc:
        published = 0
        log.exception('卓创消息发布失败 task_id=%s', task_id)
    finally:
        await publisher.close()

    summary = {
        'task_id': task_id,
        'source_code': 'supplychain/sci99',
        'source_name': SOURCE_NAME_SCI99,
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
    log.info('[sci99] 采集完成: %s', {k: summary[k] for k in
                                      ('task_id', 'fetched', 'failed', 'published', 'mode')})
    return summary
