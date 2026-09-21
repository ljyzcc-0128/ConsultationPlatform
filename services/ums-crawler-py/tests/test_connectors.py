"""黄金样本回归测试（手册6.2节）：用历史保存的真实页面做纯解析断言，不发请求。
样本来源：2026-09-18 从各信源真实抓取保存于 tests/fixtures/。"""
import pytest

from connectors.web_page import WebPageConnector
from tests.conftest import load_fixture, load_selector_config


def make_connector(source_code):
    return WebPageConnector(load_selector_config(source_code))


# ---------- 列表页解析 ----------

def test_cn01_list_parses_items():
    conn = make_connector('CN-01')
    refs = conn._parse_list_html(load_fixture('CN-01_list.html'),
                                 load_selector_config('CN-01')['entry_url'])
    assert len(refs) >= 10, 'CN-01 列表解析条目异常'
    assert all(ref.detail_url.startswith('https://www.escn.com.cn/news/show-') for ref in refs)
    assert refs[0].list_title, 'CN-01 标题提取为空'


def test_cn02_list_parses_items_with_date():
    conn = make_connector('CN-02')
    entry = load_selector_config('CN-02')['entry_url']
    refs = conn._parse_list_html(load_fixture('CN-02_list.html'), entry)
    assert len(refs) >= 10, 'CN-02 列表解析条目异常'
    assert all('/article/' in ref.detail_url for ref in refs)
    dated = [r for r in refs if r.list_publish_time]
    assert dated, 'CN-02 列表日期未解析（div.time16）'


def test_int01_list_excludes_trendline():
    conn = make_connector('INT-01')
    entry = load_selector_config('INT-01')['entry_url']
    refs = conn._parse_list_html(load_fixture('INT-01_list.html'), entry)
    assert refs, 'INT-01 列表未解析出条目'
    assert all('/news/' in ref.detail_url for ref in refs)
    assert not any('trendline' in ref.detail_url for ref in refs), '排除规则未生效'


# ---------- 详情页解析（标题/正文/发布时间，手册6.2断言口径） ----------

@pytest.mark.parametrize('source_code,fixture', [
    ('CN-01', 'CN-01_detail.html'),
    ('CN-02', 'CN-02_detail.html'),
    ('INT-02', 'INT-02_detail.html'),
])
def test_golden_sample_detail_still_parses(source_code, fixture):
    conn = make_connector(source_code)
    result = conn._parse_detail_html(load_fixture(fixture), 'https://example.com/detail')
    assert result.title, f'{source_code} 标题提取为空,选择器可能已失效'
    assert result.body and len(result.body) > 100, f'{source_code} 正文提取异常'
    assert result.original_url == 'https://example.com/detail'


def test_cn02_detail_publish_time():
    conn = make_connector('CN-02')
    result = conn._parse_detail_html(load_fixture('CN-02_detail.html'), 'https://www.gdshe.org/article/28309.html')
    assert result.publish_time is not None, 'CN-02 发布时间解析失败'
    assert result.publish_time.year == 2026


def test_int02_detail_publish_time_utc():
    conn = make_connector('INT-02')
    result = conn._parse_detail_html(
        load_fixture('INT-02_detail.html'), 'https://www.energy-storage.news/x/')
    assert result.publish_time is not None and result.publish_time.tzinfo is not None
    assert result.publish_time.utcoffset().total_seconds() == 0, '发布时间应为 UTC'
