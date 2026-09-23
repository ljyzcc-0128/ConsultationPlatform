"""WebPageConnector：列表页 → 详情页两段式抓取（手册4.2节），支持可选 Playwright 渲染"""
import asyncio
import logging
import re
from datetime import datetime
from urllib import robotparser
from urllib.parse import urlsplit

import httpx
from aiolimiter import AsyncLimiter
from parsel import Selector
from tenacity import retry, stop_after_attempt, wait_exponential

from parsing.date_parser import parse_publish_time
from parsing.fallback import extract_main_text
from parsing.url_utils import normalize_url
from .base import Connector, ItemRef, RawItemDraft

log = logging.getLogger(__name__)

_WHITESPACE_RE = re.compile(r'[ \t\r\f\v]+')


def _clean_text(text: str | None) -> str | None:
    """压缩空白但保留段落换行（数据文档：保留原文段落、数字、单位）"""
    if not text:
        return None
    lines = [_WHITESPACE_RE.sub(' ', line).strip() for line in text.splitlines()]
    lines = [line for line in lines if line]
    return '\n'.join(lines) or None


class RobotsChecker:
    """robots.txt 检查（手册第七节：不抓取明确禁止的路径）"""

    def __init__(self):
        self._cache: dict[str, robotparser.RobotFileParser | None] = {}

    async def allowed(self, client: httpx.AsyncClient, url: str) -> bool:
        parts = urlsplit(url)
        robots_url = f'{parts.scheme}://{parts.netloc}/robots.txt'
        if robots_url not in self._cache:
            try:
                resp = await client.get(robots_url, timeout=10)
                rp = robotparser.RobotFileParser()
                if resp.status_code == 200:
                    rp.parse(resp.text.splitlines())
                else:
                    rp = None  # 无 robots 或抓取失败：按允许处理，仅记录日志
                self._cache[robots_url] = rp
            except Exception as exc:
                log.warning('robots.txt 获取失败 %s: %s', robots_url, exc)
                self._cache[robots_url] = None
        rp = self._cache[robots_url]
        return True if rp is None else rp.can_fetch('*', url)


class WebPageConnector(Connector):
    def __init__(self, selector_config: dict):
        self.cfg = selector_config
        self.limiter = AsyncLimiter(1, self.cfg.get('request_interval_sec', 2))
        self.robots = RobotsChecker()
        self._playwright_sem = asyncio.Semaphore(2)  # 手册第七节：控制浏览器并发

    # ---------- HTTP ----------
    @retry(stop=stop_after_attempt(3), wait=wait_exponential(multiplier=1, min=2, max=10))
    async def _get(self, client: httpx.AsyncClient, url: str) -> str:
        async with self.limiter:
            if not await self.robots.allowed(client, url):
                raise PermissionError(f'robots.txt 禁止抓取: {url}')
            headers = self.cfg.get('headers') or {}
            if self.cfg.get('js_render'):
                return await self._get_rendered(url, headers)
            resp = await client.get(url, headers=headers, timeout=self.cfg.get('request_timeout_sec', 20),
                                    follow_redirects=True)
            resp.raise_for_status()
            return resp.text

    async def _get_rendered(self, url: str, headers: dict) -> str:
        """JS 渲染页面：Playwright 获取渲染后 HTML（一期仅明确需要时启用）"""
        try:
            from playwright.async_api import async_playwright
        except ImportError as exc:
            raise RuntimeError(
                f'{self.cfg.get("source_code")} 配置了 js_render=true，需安装 playwright 并执行 '
                f'"playwright install chromium"（参考手册第五节勘察结论）') from exc
        async with self._playwright_sem:
            async with async_playwright() as p:
                browser = await p.chromium.launch(headless=True)
                try:
                    page = await browser.new_page(user_agent=headers.get('User-Agent'))
                    await page.goto(url, wait_until='domcontentloaded',
                                    timeout=self.cfg.get('request_timeout_sec', 20) * 1000)
                    return await page.content()
                finally:
                    await browser.close()

    # ---------- 列表页 ----------
    async def fetch_list(self, config: dict, since: datetime | None = None) -> list[ItemRef]:
        refs: list[ItemRef] = []
        seen: set[str] = set()
        max_items = self.cfg.get('max_items', 200)
        async with httpx.AsyncClient() as client:
            page_url: str | None = self.cfg['entry_url']
            for _page in range(self.cfg.get('max_pages', 1)):
                if not page_url:
                    break
                html = await self._get(client, page_url)
                new_refs = self._parse_list_html(html, page_url)
                fresh = [r for r in new_refs if r.detail_url not in seen]
                refs.extend(fresh)
                seen.update(r.detail_url for r in fresh)
                page_url = self._next_page_url(Selector(text=html), page_url) \
                    if self.cfg.get('pagination') else None
                if len(refs) >= max_items:
                    break
        return refs[:max_items]

    def _parse_list_html(self, html: str, page_url: str) -> list[ItemRef]:
        """纯解析逻辑（不发请求），供黄金样本回归测试使用（手册6.2节）"""
        sel = Selector(text=html)
        excludes = [re.compile(p) for p in self.cfg.get('exclude_link_patterns', [])]
        refs = []
        items = sel.css(self.cfg['list_item_selector']) if self.cfg.get('list_item_selector') else [sel.root]
        for item in items:
            item_sel = item if isinstance(item, Selector) else Selector(element=item)
            href = self._first_attr(item_sel, self.cfg['detail_link_selector']) \
                if self.cfg.get('detail_link_selector') else None
            if not href:
                continue
            url = normalize_url(href, page_url)
            if any(p.search(url) for p in excludes):
                continue
            title = _clean_text(item_sel.css(self.cfg['title_selector']).get()) \
                if self.cfg.get('title_selector') else None
            list_time = parse_publish_time(item_sel.css(self.cfg['list_date_selector']).get()) \
                if self.cfg.get('list_date_selector') else None
            refs.append(ItemRef(source_code=self.cfg['source_code'], detail_url=url,
                                list_title=title, list_publish_time=list_time))
        return refs

    @staticmethod
    def _first_attr(item_sel: Selector, selector: str) -> str | None:
        """支持 'a::attr(href)' / '::attr(href)'（元素自身属性）两种写法"""
        m = re.match(r'^(.*?)::attr\(([\w-]+)\)$', selector)
        if m:
            element, attr = m.group(1), m.group(2)
            if not element:
                return item_sel.attrib.get(attr)
            found = item_sel.css(element)
            return found.attrib.get(attr) if found else None
        found = item_sel.css(selector)
        return found.attrib.get('href') if found else None

    def _next_page_url(self, sel: Selector, page_url: str) -> str | None:
        for css_expr in self.cfg.get('next_page_selectors', []):
            href = sel.css(css_expr).get()
            if href:
                return normalize_url(href, page_url)
        return None

    # ---------- 详情页 ----------
    async def fetch_detail(self, ref: ItemRef) -> RawItemDraft:
        async with httpx.AsyncClient() as client:
            html = await self._get(client, ref.detail_url)
        draft = self._parse_detail_html(html, ref.detail_url)
        if draft.title is None:
            draft.title = ref.list_title or ''
        return draft

    def _parse_detail_html(self, html: str, url: str) -> RawItemDraft:
        """纯解析逻辑（不发请求），供黄金样本回归测试使用（手册6.2节）"""
        sel = Selector(text=html)
        title = _clean_text(self._extract_with_fallback(sel, self.cfg.get('title_selectors', [])))
        body = _clean_text(self._extract_with_fallback(sel, self.cfg.get('body_selectors', []), join_all=True))
        if not body:
            body = _clean_text(extract_main_text(html))  # 选择器全部失效时的兜底（手册第六节）
        publish_raw = self._extract_with_fallback(sel, self.cfg.get('date_selectors', []))
        if not publish_raw and self.cfg.get('date_fallback_regex'):
            publish_raw = self._regex_first(html, self.cfg['date_fallback_regex'])
        author = self._extract_with_fallback(sel, self.cfg.get('author_selectors', []))
        language = self.cfg.get('language', 'en')
        if self.cfg.get('detect_language'):  # INT-03 手册备注：语言不写死，按正文检测
            language = self._detect_language(body or title or '')
        return RawItemDraft(
            source_code=self.cfg['source_code'],
            title=title or '',
            body=body,
            author=_clean_text(author),
            publish_time=parse_publish_time(publish_raw) if publish_raw else None,
            original_url=normalize_url(url),
            language=language,
        )

    # ---------- 工具 ----------
    def _extract_with_fallback(self, sel: Selector, selector_list: list[str],
                               join_all: bool = False) -> str | None:
        """关键字段配多重 fallback 选择器，主选择器失败时依次尝试（手册4.1节）"""
        for css_expr in selector_list:
            try:
                if join_all:
                    parts = [t.strip() for t in sel.css(css_expr).getall() if t and t.strip()]
                    result = '\n'.join(parts) if parts else None
                else:
                    result = sel.css(css_expr).get()
            except Exception as exc:
                log.debug('选择器执行失败 %s: %s', css_expr, exc)
                continue
            if result and result.strip():
                return result.strip()
        return None

    @staticmethod
    def _regex_first(html: str, pattern: str) -> str | None:
        m = re.search(pattern, html)
        return m.group(1).strip() if m else None

    @staticmethod
    def _detect_language(text: str) -> str:
        try:
            from langdetect import detect
            code = detect(text[:1000])
            return 'zh-CN' if code == 'zh-cn' else code
        except Exception:
            return 'en'
