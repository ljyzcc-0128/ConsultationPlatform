"""XXL-JOB 回调触发接口（手册第十节：POST /internal/trigger/{source_code}，
保持全平台只有一套调度系统，不在 Python 侧引入独立调度器）"""
import logging
from datetime import datetime

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

from parsing.date_parser import parse_publish_time
from runner import run_source
from supplychain.service import parse_trade_date, run_gfex_collection, run_sci99_collection, run_smm_collection

logging.basicConfig(level=logging.INFO,
                    format='%(asctime)s %(levelname)s %(name)s %(message)s')

app = FastAPI(title='ums-crawler', version='1.0.0')


class TriggerBody(BaseModel):
    since: str | None = None   # 增量起始时间（ISO 8601），可空
    limit: int | None = None   # 限制条数（联调用），可空


class SupplyChainTriggerBody(BaseModel):
    trade_date: str | None = None     # 交易日 yyyy-MM-dd / yyyyMMdd，缺省=今天（非交易日自动回溯）
    varieties: list[str] | None = None  # 品种代码，缺省=配置全部（lc,si,ps）
    trigger_type: str | None = None   # SCHEDULED/MANUAL，缺省 MANUAL


@app.get('/healthz')
async def healthz():
    return {'status': 'healthy', 'service': 'ums-crawler'}


@app.post('/internal/trigger/{source_code}')
async def trigger(source_code: str, body: TriggerBody | None = None):
    body = body or TriggerBody()
    since = parse_publish_time(body.since) if body.since else None
    try:
        summary = await run_source(source_code, since=since, limit=body.limit)
    except FileNotFoundError as exc:
        # 错误码风格对齐技术设计文档第五节（SRC 模块）
        raise HTTPException(status_code=404, detail={'code': 'SRC_4041', 'message': str(exc)})
    except ValueError as exc:
        raise HTTPException(status_code=422, detail={'code': 'SRC_4221', 'message': str(exc)})
    return summary


@app.post('/internal/trigger/supplychain/gfex')
async def trigger_gfex(body: SupplyChainTriggerBody | None = None):
    """广期所仓单采集：采集+发 MQ，数据由 Java 消费者加工落库"""
    body = body or SupplyChainTriggerBody()
    try:
        trade_date = parse_trade_date(body.trade_date)
    except ValueError as exc:
        raise HTTPException(status_code=422, detail={'code': 'SC_4221', 'message': str(exc)})
    return await run_gfex_collection(trade_date=trade_date,
                                     varieties=body.varieties,
                                     trigger_type=body.trigger_type or 'MANUAL')


@app.post('/internal/trigger/supplychain/smm')
async def trigger_smm(body: SupplyChainTriggerBody | None = None):
    """SMM 报价采集（cookie/账号登录后抓取）：采集+发 MQ，数据由 Java 消费者加工落库"""
    body = body or SupplyChainTriggerBody()
    try:
        trade_date = parse_trade_date(body.trade_date)
    except ValueError as exc:
        raise HTTPException(status_code=422, detail={'code': 'SC_4221', 'message': str(exc)})
    return await run_smm_collection(trade_date=trade_date,
                                    metrics=body.varieties,
                                    trigger_type=body.trigger_type or 'MANUAL')


@app.post('/internal/trigger/supplychain/sci99')
async def trigger_sci99(body: SupplyChainTriggerBody | None = None):
    """卓创氮气早间提示采集（cookie 注入后抓取）：采集+发 MQ，数据由 Java 消费者加工落库"""
    body = body or SupplyChainTriggerBody()
    try:
        trade_date = parse_trade_date(body.trade_date)
    except ValueError as exc:
        raise HTTPException(status_code=422, detail={'code': 'SC_4221', 'message': str(exc)})
    return await run_sci99_collection(trade_date=trade_date,
                                      trigger_type=body.trigger_type or 'MANUAL')
