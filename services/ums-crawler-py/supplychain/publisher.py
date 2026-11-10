"""发布 supplychain.*.fetched 消息（ADR-03：采集与加工经 MQ 解耦）。

与资讯链路共用 ums exchange（durable topic），独立 routing key；
RABBITMQ_URL 未配置时降级 stdout 模式（消息打印 JSON 行，不丢失）。
"""
import json
import logging
from dataclasses import dataclass, field

import aio_pika

from settings import (MQ_EXCHANGE, MQ_ROUTING_KEY_SCI99, MQ_ROUTING_KEY_SMM,
                      MQ_ROUTING_KEY_WBILL, RABBITMQ_URL)

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


@dataclass
class QuoteEntry:
    """一条外部行情/库存/排产指标点（SMM/卓创消息 entries 元素，对齐 Java 侧 QuoteMessage.Entry）"""
    metric: str            # 指标编码（即 Java 侧 series code，如 smm-cu-spot / sci99-n2-eastchina）
    metric_name: str       # 指标中文名（SMM 电解铜现货均价）
    period: str            # 发布周期 daily/weekly/monthly
    data_date: str         # 数据日期 yyyy-MM-dd（周期指标取区间截止日）
    value: float | None    # 指标值（抓取失败/未解析出为 None）
    unit: str              # 单位（元/吨、万吨、吨）
    region: str = ''       # 适用区域（华东等，全国性指标留空）
    subject: str = ''      # 明细主体（同指标细分市场，如卓创华东各省市行；无细分留空）
    raw: dict = field(default_factory=dict)  # 接口原始行（Java 侧归档排查用）


def _quote_entry_dict(e: QuoteEntry) -> dict:
    """QuoteEntry → 消息 entries 元素（对齐 Java 侧 QuoteMessage.Entry）"""
    return {
        'metric': e.metric,
        'metric_name': e.metric_name,
        'period': e.period,
        'data_date': e.data_date,
        'subject': e.subject,
        'value': e.value,
        'unit': e.unit,
        'region': e.region,
        'raw': e.raw,
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


def build_quote_message(event: str, source_name: str, task_id: str, entries: list,
                        errors: list, trigger_type: str, started_at: str, finished_at: str) -> dict:
    """构造 SMM/卓创任务级消息体：一次触发一条消息，entries 含全部指标点；
    全指标失败也发布（entries 可为空），保证 Java 侧能落采集任务日志。"""
    return {
        'schema_version': SCHEMA_VERSION,
        'event': event,
        'task_id': task_id,
        'fetched_at': finished_at,
        'source_name': source_name,
        'trigger_type': trigger_type,
        'started_at': started_at,
        'finished_at': finished_at,
        'fetched': len(entries),
        'failed': len(errors),
        'errors': errors,
        'entries': [_quote_entry_dict(e) for e in entries],
    }


class QuotePublisher:
    """报价类消息发布器（SMM/卓创共用）；未配置 MQ 时降级 stdout（本地调试）"""

    def __init__(self, mq_url: str = RABBITMQ_URL, routing_key: str = MQ_ROUTING_KEY_SMM):
        self.mq_url = mq_url
        self.routing_key = routing_key
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
                routing_key=self.routing_key,
            )
        else:
            print(payload, flush=True)

    async def close(self) -> None:
        if self._connection:
            await self._connection.close()
            self._connection = self._channel = self._exchange = None


class WbillPublisher(QuotePublisher):
    """仓单消息发布器（routing key 固定 supplychain.wbill.fetched），保持 gfex 链路兼容"""

    def __init__(self, mq_url: str = RABBITMQ_URL):
        super().__init__(mq_url=mq_url, routing_key=MQ_ROUTING_KEY_WBILL)
