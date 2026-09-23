"""统一日期解析（手册第二节：dateparser 处理各信源五花八门的日期格式）"""
import re
from datetime import datetime, timezone, timedelta

import dateparser

# 中国信源的服务器时间多为北京时间；naive 时间按此时区归一化为 UTC
CN_TZ = timezone(timedelta(hours=8))

_DATE_CANDIDATE_RE = re.compile(
    r'\d{4}-\d{1,2}-\d{1,2}[T ]\d{1,2}:\d{2}:\d{2}(?:[+-]\d{2}:?\d{2}|Z)?'
    r'|\d{4}[-/年.]\d{1,2}[-/月.]\d{1,2}日?(?:[ T]\d{1,2}:\d{2}(?::\d{2})?)?'
    r'|\d{1,2}:\d{2}[:, ]*\d{2}'
)
# 纯日期（无时间成分）：按 UTC 午夜处理，保持业务发生地日历日期稳定（数据文档第六节口径）
_DATE_ONLY_RE = re.compile(r'^\d{4}[-/年.]\d{1,2}[-/月.]\d{1,2}日?$')


def _clean(raw: str) -> str:
    text = (raw or '').strip()
    # 提取第一个像日期的子串，避免"发布时间：2026-09-14"这类前缀干扰
    m = _DATE_CANDIDATE_RE.search(text)
    return m.group(0) if m else text


def parse_publish_time(raw: str | None) -> datetime | None:
    """把信源页面上五花八门的日期文本解析为 UTC aware datetime；解析失败返回 None。

    数据文档口径：发布时间缺失必须保留 None，不得用抓取时间替代。
    """
    if not raw or not (raw := _clean(str(raw))).strip():
        return None
    if _DATE_ONLY_RE.match(raw):
        # 纯日期：原文仅精确到日，按 UTC 午夜保存以保持日历日期
        parsed = dateparser.parse(raw, languages=['zh', 'en'],
                                  settings={'PREFER_DATES_FROM': 'past'})
        if parsed is None:
            return None
        return parsed.replace(tzinfo=timezone.utc)
    parsed = dateparser.parse(
        raw,
        languages=['zh', 'en'],
        settings={
            'RETURN_AS_TIMEZONE_AWARE': True,
            'PREFER_DATES_FROM': 'past',
            'RELATIVE_BASE': datetime.now(CN_TZ),
        },
    )
    if parsed is None:
        return None
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=CN_TZ)
    return parsed.astimezone(timezone.utc)
