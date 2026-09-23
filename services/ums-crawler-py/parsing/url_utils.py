"""URL 归一化（数据文档：original_url 去除 utm_* 等跟踪参数；同源规范化 URL 唯一）"""
from urllib.parse import urljoin, urlsplit, urlunsplit, parse_qsl, urlencode

# 常见跟踪参数前缀/名单（BESS 配置表备注：去重时可移除 utm_* 跟踪参数，保留业务参数）
_TRACKING_PREFIXES = ('utm_',)
_TRACKING_KEYS = {'fbclid', 'gclid', 'from', 'share_token', 'spm', 'scm'}


def normalize_url(url: str, base: str | None = None) -> str:
    """补全相对路径并移除跟踪参数，返回规范化 URL"""
    if not url:
        return ''
    if base:
        url = urljoin(base, url)
    parts = urlsplit(url)
    query = [(k, v) for k, v in parse_qsl(parts.query, keep_blank_values=True)
             if not k.lower().startswith(_TRACKING_PREFIXES) and k.lower() not in _TRACKING_KEYS]
    return urlunsplit((parts.scheme.lower(), parts.netloc.lower(),
                       parts.path or '/', urlencode(query), ''))
