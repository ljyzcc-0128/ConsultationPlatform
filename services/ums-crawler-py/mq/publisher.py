"""发布 raw.item.fetched 消息（ADR-11：Python 服务直接生产 RabbitMQ 消息，选定方案）"""
import json
import logging
from datetime import datetime, timezone

import aio_pika

from settings import MQ_EXCHANGE, MQ_ROUTING_KEY, RABBITMQ_URL

log = logging.getLogger(__name__)

SCHEMA_VERSION = '1.0'


def build_message(draft, task_id: str) -> dict:
    """构造 raw.item.fetched 消息体（字段口径对齐数据文档 News Object：
    publish_time 缺失为 null；时间为 UTC ISO 8601；original_url 已归一化）"""
    iso = lambda dt: dt.astimezone(timezone.utc).isoformat().replace('+00:00', 'Z') if dt else None
    return {
        'schema_version': SCHEMA_VERSION,
        'event': 'raw.item.fetched',
        'task_id': task_id,
        'fetched_at': iso(datetime.now(timezone.utc)),
        'item': {
            'source_code': draft.source_code,
            'title': draft.title,
            'body': draft.body,
            'author': draft.author,
            'publish_time': iso(draft.publish_time),
            'original_url': draft.original_url,
            'language': draft.language,
        },
    }


class RawItemPublisher:
    """MQ 发布器；RABBITMQ_URL 未配置时降级为 stdout 模式（本地调试用，消息打印 JSON 行）"""

    def __init__(self, mq_url: str = RABBITMQ_URL):
        self.mq_url = mq_url
        self._connection = None
        self._channel = None
        self._exchange = None

    async def connect(self) -> None:
        if self.mq_url:
            self._connection = await aio_pika.connect_robust(self.mq_url)
            self._channel = await self._connection.channel()
            # durable topic 交换器；下游按 routing key 订阅 raw.item.fetched
            self._exchange = await self._channel.declare_exchange(
                MQ_EXCHANGE, aio_pika.ExchangeType.TOPIC, durable=True)
            log.info('RabbitMQ 已连接 exchange=%s', MQ_EXCHANGE)

    async def publish(self, draft, task_id: str) -> None:
        message = build_message(draft, task_id)
        payload = json.dumps(message, ensure_ascii=False)
        if self._exchange is not None:
            await self._exchange.publish(
                aio_pika.Message(body=payload.encode('utf-8'),
                                 content_type='application/json',
                                 delivery_mode=aio_pika.DeliveryMode.PERSISTENT),
                routing_key=MQ_ROUTING_KEY,
            )
        else:
            # stdout 降级模式：未配置 MQ 时本地可跑通全流程（消息不丢失到无处可去）
            print(payload, flush=True)

    async def close(self) -> None:
        if self._connection:
            await self._connection.close()
            self._connection = self._channel = self._exchange = None
