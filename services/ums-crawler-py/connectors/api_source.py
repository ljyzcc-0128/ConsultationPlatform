"""ApiConnector：对接有开放 API 的数据源（ADR-07 四类实现之一）"""
import logging
from datetime import datetime
from urllib.parse import urlencode

import httpx
from aiolimiter import AsyncLimiter
from tenacity import retry, stop_after_attempt, wait_exponential

from parsing.url_utils import normalize_url
from .base import Connector, ItemRef, RawItemDraft

log = logging.getLogger(__name__)


def _dig(obj, dotted_path: str):
    """按 a.b.c 点路径取嵌套 JSON 字段"""
    for key in dotted_path.split('.'):
        if not isinstance(obj, dict) or key not in obj:
            return None
        obj = obj[key]
    return obj


class ApiConnector(Connector):
    """通用 JSON API 连接器：列表接口 + 字段映射；详情若为独立接口则复用 detail_url_template"""

    def __init__(self, selector_config: dict):
        self.cfg = selector_config
        self.limiter = AsyncLimiter(1, self.cfg.get('request_interval_sec', 2))

    @retry(stop=stop_after_attempt(3), wait=wait_exponential(multiplier=1, min=2, max=10))
    async def _get_json(self, client: httpx.AsyncClient, url: str) -> dict:
        async with self.limiter:
            resp = await client.get(url, headers=self.cfg.get('headers') or {},
                                    timeout=self.cfg.get('request_timeout_sec', 20))
            resp.raise_for_status()
            return resp.json()

    async def fetch_list(self, config: dict, since: datetime | None = None) -> list[ItemRef]:
        refs: list[ItemRef] = []
        async with httpx.AsyncClient() as client:
            for page in range(1, self.cfg.get('max_pages', 1) + 1):
                params = dict(self.cfg.get('list_query', {}) or {})
                if self.cfg.get('page_param'):
                    params[self.cfg['page_param']] = page
                query = ('?' + urlencode(params)) if params else ''
                data = await self._get_json(client, self.cfg['api_url'] + query)
                items = _dig(data, self.cfg.get('items_path', ''))
                if not items:
                    break
                for raw in items:
                    url = normalize_url(str(_pick(raw, self.cfg.get('url_field', 'url'))),
                                        base=self.cfg.get('base_url'))
                    if not url:
                        continue
                    refs.append(ItemRef(
                        source_code=self.cfg['source_code'],
                        detail_url=url,
                        list_title=_pick(raw, self.cfg.get('title_field', 'title')),
                    ))
                if len(refs) >= self.cfg.get('max_items', 200):
                    break
        return refs

    async def fetch_detail(self, ref: ItemRef) -> RawItemDraft:
        # 无独立详情接口时，列表条目即完整数据（entry 字段映射可空）
        return RawItemDraft(
            source_code=ref.source_code,
            title=ref.list_title or '',
            body=None,
            author=None,
            publish_time=ref.list_publish_time,
            original_url=ref.detail_url,
            language=self.cfg.get('language', 'en'),
        )


def _pick(obj: dict, dotted_path: str | None):
    if not dotted_path:
        return None
    return _dig(obj, dotted_path) if obj else None
