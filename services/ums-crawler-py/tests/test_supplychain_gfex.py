"""广期所仓单采集单元测试（黄金样本：官网真实响应，不发真实请求）"""
import asyncio
import json
from datetime import date
from pathlib import Path
from unittest.mock import AsyncMock, MagicMock

import pytest

from supplychain.gfex import WbillEntry, fetch_wbill, parse_wbill_response
from supplychain.publisher import build_message
from supplychain.service import parse_trade_date, run_gfex_collection

FIXTURES = Path(__file__).parent / 'fixtures'


def _load(name: str) -> dict:
    return json.loads((FIXTURES / name).read_text(encoding='utf-8'))


LC_FIXTURE = _load('gfex_wbill_lc_20261028.json')
SI_FIXTURE = _load('gfex_wbill_si_20261028.json')
PS_FIXTURE = _load('gfex_wbill_ps_20261028.json')
EMPTY_FIXTURE = _load('gfex_wbill_empty.json')


def _resp(payload: dict):
    m = MagicMock()
    m.raise_for_status = MagicMock()
    m.json = MagicMock(return_value=payload)
    return m


class TestParse:
    def test_lc_total_and_consistency(self):
        entry = parse_wbill_response(LC_FIXTURE, 'lc', date(2026, 10, 28))
        assert entry is not None
        # 黄金样本实测值（2026-10-28 碳酸锂）
        assert entry.qty == 17759
        assert entry.last_qty == 19202
        assert entry.diff == -1443
        assert entry.qty == entry.last_qty + entry.diff
        assert entry.variety_name == '碳酸锂'
        assert entry.trade_date == '2026-10-28'
        # 明细行过滤：小计/总计行（whAbbr=''）不进 warehouses
        assert all(w['wh_name'] for w in entry.warehouses)
        assert sum(w['qty'] for w in entry.warehouses) == entry.qty

    def test_si_trademark_rows(self):
        entry = parse_wbill_response(SI_FIXTURE, 'si', date(2026, 10, 28))
        assert entry.qty == 32364
        assert entry.diff == -142

    def test_ps_trademark_rows(self):
        """多晶硅明细带商标（品牌）维度"""
        entry = parse_wbill_response(PS_FIXTURE, 'ps', date(2026, 10, 28))
        assert entry.qty == 21450
        assert entry.diff == 0
        assert any(w['trademark'] for w in entry.warehouses)

    def test_empty_returns_none(self):
        assert parse_wbill_response(EMPTY_FIXTURE, 'lc', date(2026, 10, 25)) is None

    def test_inconsistent_total_raises(self):
        bad = {'data': [{'variety': '总计', 'whAbbr': '', 'lastWbillQty': 100,
                         'wbillQty': 90, 'diff': -5, 'trademarkName': ''}]}
        with pytest.raises(ValueError, match='一致性校验失败'):
            parse_wbill_response(bad, 'lc', date(2026, 10, 28))

    def test_field_rename_fallback(self):
        """动态字段匹配：官方改名 wbillQty→regWbillQty 时仍可解析"""
        renamed = {'data': [{'variety': '总计', 'whAbbr': '', 'lastWbillQty': 100,
                             'regWbillQty': 120, 'diff': 20, 'trademarkName': ''}]}
        entry = parse_wbill_response(renamed, 'lc', date(2026, 10, 28))
        assert entry.qty == 120


class TestFetch:
    def test_fetch_parses(self):
        client = MagicMock()
        client.post = AsyncMock(return_value=_resp(LC_FIXTURE))
        entry = asyncio.run(fetch_wbill(client, 'http://test', 'lc', date(2026, 10, 28)))
        assert entry.qty == 17759
        args, kwargs = client.post.call_args
        assert kwargs['data'] == {'gen_date': '20261028', 'variety': 'lc'}


class TestService:
    def test_parse_trade_date(self):
        assert parse_trade_date('2026-10-28') == date(2026, 10, 28)
        assert parse_trade_date('20261028') == date(2026, 10, 28)
        with pytest.raises(ValueError):
            parse_trade_date('28/10/2026')

    def test_rollback_to_previous_trading_day(self, monkeypatch):
        """非交易日（周日）→ 回溯到有数据的最近交易日"""
        import supplychain.service as svc
        calls = []

        async def fake_fetch(client, base_url, variety, trade_date):
            calls.append(trade_date)
            if trade_date == date(2026, 10, 28):
                return parse_wbill_response(LC_FIXTURE, 'lc', trade_date)
            return None  # 周六/周日无数据

        monkeypatch.setattr(svc, 'fetch_wbill', fake_fetch)
        publisher = MagicMock()
        publisher._exchange = None
        publisher.mq_url = ''
        publisher.publish = AsyncMock()
        publisher.close = AsyncMock()

        summary = asyncio.run(svc.run_gfex_collection(
            trade_date=date(2026, 10, 30), varieties=['lc'],  # 周五回溯到周三
            trigger_type='MANUAL', publisher=publisher))

        assert summary['fetched'] == 1
        assert summary['failed'] == 0
        assert calls == [date(2026, 10, 30), date(2026, 10, 29), date(2026, 10, 28)]
        assert publisher.publish.await_count == 1
        message = publisher.publish.await_args.args[0]
        assert message['event'] == 'supplychain.wbill.fetched'
        assert message['entries'][0]['trade_date'] == '2026-10-28'
        assert message['fetched'] == 1 and message['failed'] == 0

    def test_all_failed_still_publishes(self, monkeypatch):
        """全品种失败也发消息（entries 为空），Java 侧落 FAILED 采集日志"""
        import supplychain.service as svc

        async def fake_fetch(client, base_url, variety, trade_date):
            raise RuntimeError('接口超时')

        monkeypatch.setattr(svc, 'fetch_wbill', fake_fetch)
        publisher = MagicMock()
        publisher._exchange = None
        publisher.mq_url = ''
        publisher.publish = AsyncMock()
        publisher.close = AsyncMock()

        summary = asyncio.run(svc.run_gfex_collection(
            trade_date=date(2026, 10, 28), varieties=['lc'],
            trigger_type='SCHEDULED', publisher=publisher))

        assert summary['fetched'] == 0 and summary['failed'] == 1
        message = publisher.publish.await_args.args[0]
        assert message['entries'] == []
        assert message['failed'] == 1
        assert message['trigger_type'] == 'SCHEDULED'


def test_build_message_structure():
    entry = WbillEntry(variety='lc', variety_name='碳酸锂', trade_date='2026-10-28',
                       last_qty=100, qty=90, diff=-10,
                       warehouses=[{'wh_name': '甲库', 'trademark': '',
                                    'last_qty': 100, 'qty': 90, 'diff': -10}])
    msg = build_message('task1', [entry], [], 'SCHEDULED',
                        '2026-10-29T00:00:00Z', '2026-10-29T00:00:05Z')
    assert msg['schema_version'] == '1.0'
    assert msg['event'] == 'supplychain.wbill.fetched'
    assert msg['source_name'] == '广期所'
    assert msg['fetched'] == 1
    assert msg['entries'][0]['variety'] == 'lc'
    assert msg['entries'][0]['total'] == {'last_qty': 100, 'qty': 90, 'diff': -10}
    # entries 可 JSON 序列化（发 MQ 前提）
    json.dumps(msg, ensure_ascii=False)
