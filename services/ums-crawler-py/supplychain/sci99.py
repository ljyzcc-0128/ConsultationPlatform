"""卓创资讯氮气行情采集（sci99.com「早间提示」栏目，每日更新）。

接口契约（2026-11 实测探测）：
- 早间提示正文为付费内容（ASP.NET 页 + 登录墙），登录态三路优先级：
  1) SCI99_COOKIE 环境变量注入（浏览器导出，"k1=v1; k2=v2"）；
  2) SCI99_USERNAME/SCI99_PASSWORD → Playwright 自动登录（抓取要求文档 4.3
     选型：付费墙 + 动态渲染；登录态 Storage State 持久化，避免频繁重登）；
  3) 均未配置时任务报错不 crash。
- 流程：GET 氮气产品页（ig.sci99.com/product/nitrogen）→ 匹配标题含「液氮/氮气」+
  「早间」的最新文章链接 → GET 正文 → 解析「华东」区块价格表（区域|市场|MM月DD日|
  日涨跌）→ 华东区块逐市场一条 QuoteEntry（subject=市场名，data_date=表头价格日期，
  即上一交易日收盘价）
- 抓不到列表/正文/价格时抛 Sci99Error（调用方记为采集失败，不 crash）

⚠ 栏目列表页 URL 因账号权限/栏目调整可能变化，可用 SCI99_MORNING_URL 覆盖；
页面结构需在拿到有效登录态后核对一次，解析为启发式正则，raw 原文摘要上送。
"""
import asyncio
import json
import logging
import re
from datetime import date as date_cls
from pathlib import Path

import httpx

from settings import SCI99_LOGIN_URL, SCI99_PASSWORD, SCI99_STORAGE_FILE, SCI99_USERNAME

from supplychain.publisher import QuoteEntry

log = logging.getLogger(__name__)

METRIC = 'sci99-n2-eastchina'
METRIC_NAME = '氮气华东区域价格（卓创早间提示）'
UNIT = '元/吨'
REGION = '华东'

# 标题匹配：氮气/液氮 + 早间（顺序不限，允许少量间隔；2026-11 实测标题为「液氮市场早间提示」）
TITLE_RE = re.compile(r'(?:氮气|液氮)[^早]{0,12}早间|早间[^氮]{0,12}(?:氮气|液氮)')
# 链接提取：<a href="...">标题</a>（相对/绝对路径均可；锚文本允许嵌套标签，随后剥壳）
LINK_RE = re.compile(r'<a[^>]+href=["\']([^"\']+)["\'][^>]*>([\s\S]{4,120}?)</a>')
# 华东区块价格行（『山东 470—540 0—20』格式）：市场名 + 区间价（≥3 位，天然排除
# 涨跌列『0—0』『20—0』小数字对）+ 可选涨跌区间
_MARKET_ROW_RE = re.compile(
    r'([\u4e00-\u9fa5]{2,6}?)\s*(\d{3,6}(?:\.\d+)?)\s*[—–~]\s*(\d{3,6}(?:\.\d+)?)'
    r'(?:\s*(-?\d{1,4})\s*[—–~]\s*(-?\d{1,4}))?')
# 表格价格日期表头（『08月05日』——早间提示发布上一交易日收盘价）
_HEADER_DATE_RE = re.compile(r'(\d{1,2})月(\d{1,2})日')
# 华东区块结束的大区标题（表格中紧随其后的区块头）
_REGION_SIBLINGS = ('华中', '华南', '华北', '西北', '东北', '西南')
# 正文可见文本（去 script/style/标签）
SCRIPT_RE = re.compile(r'<(script|style)[^>]*>.*?</\1>', re.IGNORECASE | re.DOTALL)
TAG_RE = re.compile(r'<[^>]+>')


class Sci99Error(Exception):
    """卓创抓取失败（登录墙/结构变化/未解析出价格）——调用方记为采集失败，不 crash"""


def _session_headers(cookie: str) -> dict:
    headers = {
        'User-Agent': 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36',
        'Referer': 'https://www.sci99.com/',
    }
    if cookie.strip():
        headers['Cookie'] = cookie.strip()
    return headers


def extract_text(html: str) -> str:
    """HTML → 可见文本（去 script/style 与标签）"""
    return TAG_RE.sub(' ', SCRIPT_RE.sub(' ', html))


def parse_morning_links(html: str, base_url: str) -> list:
    """列表页 HTML → [(文章URL, 标题)]（标题含「氮气」+「早间」）"""
    text = extract_text(html)
    links = []
    for url, title in LINK_RE.findall(html):
        title = TAG_RE.sub('', title).strip()  # 锚文本可能嵌套 span/b 等标签
        if TITLE_RE.search(title):
            if url.startswith('/'):
                url = base_url.rstrip('/') + url
            links.append((url, title))
    # 去重保持顺序
    seen, unique = set(), []
    for url, title in links:
        if url not in seen:
            seen.add(url)
            unique.append((url, title))
    return unique


def parse_price_date(text: str, target: date_cls) -> date_cls | None:
    """表格价格日期：早间提示发布的是上一交易日收盘价，表头带「MM月DD日」
    （如『08月05日』）。年份取采集目标日所在年（跨年 1 月发布 12 月数据时
    以标题发布日月份小于表头月份判定回退一年）。"""
    m = _HEADER_DATE_RE.search(text)
    if not m:
        return None
    month, day = int(m.group(1)), int(m.group(2))
    year = target.year
    if month > target.month + 1:  # 表头月份远大于发布月 → 属于上一年 12 月数据
        year -= 1
    try:
        return date_cls(year, month, day)
    except ValueError:
        return None


def parse_markets(text: str) -> list[tuple[str, float, str]]:
    """解析「华东」区块逐市场价格行（2026-11 按用户样例图确认表结构：
    区域 | 市场 | MM月DD日 | 日涨跌，华东区块下每市场一行，如『山东 470—540 0—20』）。
    返回 [(市场名, 区间中值, 涨跌原文)]；价格区间需 ≥3 位数字，天然排除
    涨跌列的『0—0』『20—0』等小数字对。无市场行时返回空列表。"""
    idx = text.find(REGION)
    while idx != -1:
        section = text[idx + len(REGION):]
        ends = [section.find(h) for h in _REGION_SIBLINGS if section.find(h) != -1]
        end = min(ends) if ends else 400
        if end > 0:
            rows = []
            for m in _MARKET_ROW_RE.finditer(section[:end]):
                name = m.group(1).strip()
                low, high = float(m.group(2)), float(m.group(3))
                diff = (m.group(4) or '') + (f"~{m.group(5)}" if m.group(5) else '')
                rows.append((name, round((low + high) / 2, 2), diff))
            if rows:
                return rows
        idx = text.find(REGION, idx + 1)
    return []


def parse_article(html: str, url: str, target: date_cls) -> list[QuoteEntry]:
    """文章页 HTML → QuoteEntry 列表（华东区块逐市场一条，data_date 取表头价格日期）；
    市场行/价格未解析出时抛 Sci99Error（计为该指标失败）"""
    text = extract_text(html)
    rows = parse_markets(text)
    if not rows:
        raise Sci99Error(f'未能在早间提示正文中解析出{REGION}各市场价格行 url={url}')
    price_date = parse_price_date(text, target) or target
    return [QuoteEntry(metric=METRIC, metric_name=METRIC_NAME, period='daily',
                       data_date=price_date.isoformat(), value=value, unit=UNIT,
                       region=REGION, subject=market,
                       raw={'article_url': url, 'market': market, 'diff': diff})
            for market, value, diff in rows]


async def fetch_n2_morning(client: httpx.AsyncClient, cookie: str, base_url: str,
                           morning_url: str, target: date_cls) -> list[QuoteEntry]:
    """抓当日氮气早间提示 → QuoteEntry 列表（华东逐市场一条）；
    列表/正文/价格任一环节失败抛 Sci99Error"""
    resp = await client.get(morning_url, headers=_session_headers(cookie))
    resp.raise_for_status()
    content_type = resp.headers.get('content-type', '')
    if 'html' not in content_type.lower() and 'text' not in content_type.lower():
        raise Sci99Error(f'列表页返回非 HTML（登录态失效），请更新 SCI99_COOKIE '
                         f'content-type={content_type}')
    links = parse_morning_links(resp.text, base_url)
    if not links:
        raise Sci99Error(f'列表页未匹配到氮气早间提示文章 url={morning_url}（登录态或栏目路径需核对）')

    url, title = links[0]  # 栏目默认最新一篇在前
    article = await client.get(url, headers=_session_headers(cookie))
    article.raise_for_status()
    entries = parse_article(article.text, url, target)
    for e in entries:
        e.raw['article_title'] = title
    return entries


# ---- Playwright 自动登录（账号密码路径）----

# 登录成功判定：跳出登录页路由 或 拿到 auth 会话 cookie（SDK 中出现的键名）
_LOGIN_SUCCESS_COOKIE_RE = re.compile(r'auth', re.IGNORECASE)


def _cookies_to_header(cookies: list) -> str:
    """Playwright cookies → httpx Cookie 头（"k1=v1; k2=v2"）"""
    return '; '.join(f"{c['name']}={c['value']}" for c in cookies)


def _login_sync(username: str, password: str, login_url: str, storage_file: str) -> str:
    """同步执行 Playwright 登录（在线程池中跑）→ Cookie 头字符串；失败抛 Sci99Error"""
    try:
        from playwright.sync_api import sync_playwright
    except ImportError as exc:
        raise Sci99Error('未安装 playwright，无法账号密码登录（pip install playwright '
                         '&& playwright install chromium），或改用 SCI99_COOKIE') from exc

    storage_path = Path(storage_file)
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        try:
            # 已有登录态：直接复用（持久化 Storage State，避免频繁重登）
            if storage_path.exists():
                ctx = browser.new_context(storage_state=str(storage_path))
                cookies = ctx.cookies()
                if cookies and any(_LOGIN_SUCCESS_COOKIE_RE.search(c['name'])
                                   for c in cookies):
                    log.info('[sci99] 复用持久化登录态 %s（%d 个 cookie）',
                             storage_path, len(cookies))
                    return _cookies_to_header(cookies)
                ctx.close()  # 登录态失效，走重新登录

            ctx = browser.new_context(
                user_agent='Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) '
                           'AppleWebKit/537.36 (KHTML, like Gecko) '
                           'Chrome/146.0.0.0 Safari/537.36')
            page = ctx.new_page()
            page.goto(login_url, wait_until='domcontentloaded', timeout=40000)
            page.wait_for_timeout(3000)
            # 默认 tab 是手机验证码登录，切换到账号密码登录
            page.get_by_text('账号密码登录', exact=True).first.click()
            page.wait_for_timeout(1000)
            page.get_by_placeholder('请输入账号').fill(username)
            page.get_by_placeholder('请输入密码').fill(password)
            # 用户协议勾选框（存在则勾选）
            checkbox = page.locator('.el-checkbox__original')
            if checkbox.count() > 0 and not checkbox.first.is_checked():
                page.locator('.el-checkbox__inner').first.click()
                page.wait_for_timeout(300)
            page.get_by_role('button', name='登录').click()

            # 等待登录成功：跳出登录页或出现 auth cookie（最长 15 秒）
            logged_in = False
            for _ in range(30):
                page.wait_for_timeout(500)
                if '#/login' not in page.url:
                    logged_in = True
                    break
                cookies = ctx.cookies()
                if any(_LOGIN_SUCCESS_COOKIE_RE.search(c['name']) for c in cookies):
                    logged_in = True
                    break
            if not logged_in:
                tips = page.eval_on_selector_all(
                    '.el-message, .el-form-item__error, [class*=tips]',
                    "els => els.map(e => (e.textContent||'').trim()).filter(t => t)")
                raise Sci99Error(f'卓创账号密码登录未成功（可能有滑块验证码/账号密码错误）'
                                 f' 提示={tips[:3]}')
            storage_path.parent.mkdir(parents=True, exist_ok=True)
            ctx.storage_state(path=str(storage_path))
            cookies = ctx.cookies()
            log.info('[sci99] 登录成功，登录态已持久化到 %s（%d 个 cookie）',
                     storage_path, len(cookies))
            return _cookies_to_header(cookies)
        finally:
            browser.close()


async def login_cookie(username: str, password: str,
                       login_url: str = SCI99_LOGIN_URL,
                       storage_file: str = SCI99_STORAGE_FILE) -> str:
    """账号密码 Playwright 登录 → Cookie 头字符串（登录态持久化复用）；失败抛 Sci99Error"""
    return await asyncio.to_thread(_login_sync, username, password,
                                   login_url, storage_file)
