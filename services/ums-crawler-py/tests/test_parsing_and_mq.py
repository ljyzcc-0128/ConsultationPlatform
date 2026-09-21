"""日期解析与 URL 归一化单元测试；MQ stdout 降级模式测试"""
from datetime import datetime, timezone

from parsing.date_parser import parse_publish_time
from parsing.url_utils import normalize_url


# ---------- date_parser ----------

def test_plain_iso_date():
    assert parse_publish_time('2026-09-14') == datetime(2026, 9, 14, tzinfo=timezone.utc)


def test_chinese_date_with_prefix():
    # CN-02 详情页真实格式："发布时间：2026年09月11日"
    dt = parse_publish_time('发布时间：2026年09月11日')
    assert dt == datetime(2026, 9, 11, tzinfo=timezone.utc)


def test_iso8601_with_offset_converts_to_utc():
    dt = parse_publish_time('2026-09-17T13:25:53+00:00')
    assert dt.hour == 13 and dt.utcoffset().total_seconds() == 0


def test_relative_time():
    assert parse_publish_time('3 days ago') is not None


def test_garbage_returns_none():
    assert parse_publish_time('不知道') is None
    assert parse_publish_time('') is None
    assert parse_publish_time(None) is None


# ---------- url_utils ----------

def test_strips_utm_and_tracking_params():
    url = normalize_url('https://ESCN.com.cn/news/564.html?utm_source=chatgpt.com&id=7')
    assert url == 'https://escn.com.cn/news/564.html?id=7'


def test_joins_relative_url():
    assert normalize_url('/article/28309.html', 'https://www.gdshe.org/list/7.html') \
        == 'https://www.gdshe.org/article/28309.html'


def test_keeps_business_params():
    url = normalize_url('https://a.com/p?page=2&fbclid=x')
    assert 'page=2' in url and 'fbclid' not in url


# ---------- mq publisher（stdout 降级模式） ----------

async def test_publisher_stdout_mode(capsys):
    from mq.publisher import RawItemPublisher
    from connectors.base import RawItemDraft

    draft = RawItemDraft(source_code='CN-01', title='标题', body='正文', author=None,
                         publish_time=None, original_url='https://www.escn.com.cn/news/show-1.html',
                         language='zh-CN')
    pub = RawItemPublisher(mq_url='')  # 未配置 MQ → stdout
    await pub.publish(draft, task_id='t1')
    await pub.close()
    import json
    out = json.loads(capsys.readouterr().out.strip().splitlines()[-1])
    assert out['event'] == 'raw.item.fetched' and out['item']['source_code'] == 'CN-01'
    assert out['item']['publish_time'] is None  # 数据文档：缺失保留 null，不得用抓取时间替代
