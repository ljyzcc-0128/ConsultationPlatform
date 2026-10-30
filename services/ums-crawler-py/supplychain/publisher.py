"""发布 supplychain.wbill.fetched 消息（ADR-03：采集与加工经 MQ 解耦）。

与资讯链路共用 ums exchange（durable topic），独立 routing key；
RABBITMQ_URL 未配置时降级 stdout 模式（消息打印 JSON 行，不丢失）。
"""
import json
import logging

import aio_pika

from settings import MQ_EXCHANGE, MQ_ROUTING_KEY_WBILL, RABBITMQ_URL

log = logging.getLogger(__name__)

SCHEMA_VERSION = '1.0'


def _entry_dict(e) -> dict:
    """WbillEntry → 消息 entries 元素（总量嵌套 total，对齐 Java 侧 GfexWbillMessage.Entry）"""
    return {
        'variety': e.variety,
        'variety_name': e.variety_name,
        'trade_date': e.trade_date,
        'total': {'last_qty': e.last_qty, 'qty': e.qty, 'diff': e.diff},
        'warehouses': e.warehouses,
    }


def build_message(task_id: str, entries: list, errors: list,
                  trigger_type: str, started_at: str, finished_at: str) -> dict:
    """构造任务级消息体：一次触发一条消息，entries 含全部品种数据；
    全品种失败也发布（entries 可为空），保证 Java 侧能落采集任务日志。"""
    return {
        'schema_version': SCHEMA_VERSION,
        'event': 'supplychain.wbill.fetched',
        'task_id': task_id,
        'fetched_at': finished_at,
        'source_name': '广期所',
        'trigger_type': trigger_type,
        'started_at': started_at,
        'finished_at': finished_at,
        'fetched': len(entries),
        'failed': len(errors),
        'errors': errors,
        'entries': [_entry_dict(e) for e in entries],
    }


class WbillPublisher:
    """仓单消息发布器；未配置 MQ 时降级 stdout（本地调试）"""

    def __init__(self, mq_url: str = RABBITMQ_URL):
        self.mq_url = mq_url
        self._connection = None
        self._channel = None
        self._exchange = None

    async def connect(self) -> None:
        if self.mq_url:
            self._connection = await aio_pika.connect_robust(self.mq_url)
            self._channel = await self._connection.channel()
            self._exchange = await self._channel.declare_exchange(
                MQ_EXCHANGE, aio_pika.ExchangeType.TOPIC, durable=True)
            log.info('RabbitMQ 已连接 exchange=%s', MQ_EXCHANGE)

    async def publish(self, message: dict) -> None:
        payload = json.dumps(message, ensure_ascii=False)
        if self._exchange is not None:
            await self._exchange.publish(
                aio_pika.Message(body=payload.encode('utf-8'),
                                 content_type='application/json',
                                 delivery_mode=aio_pika.DeliveryMode.PERSISTENT),
                routing_key=MQ_ROUTING_KEY_WBILL,
            )
        else:
            print(payload, flush=True)

    async def close(self) -> None:
        if self._connection:
            await self._connection.close()
            self._connection = self._channel = self._exchange = None
