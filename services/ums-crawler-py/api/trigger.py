"""XXL-JOB 回调触发接口（手册第十节：POST /internal/trigger/{source_code}，
保持全平台只有一套调度系统，不在 Python 侧引入独立调度器）"""
import logging
from datetime import datetime

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

from parsing.date_parser import parse_publish_time
from runner import run_source

logging.basicConfig(level=logging.INFO,
                    format='%(asctime)s %(levelname)s %(name)s %(message)s')

app = FastAPI(title='ums-crawler', version='1.0.0')


class TriggerBody(BaseModel):
    since: str | None = None   # 增量起始时间（ISO 8601），可空
    limit: int | None = None   # 限制条数（联调用），可空


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
