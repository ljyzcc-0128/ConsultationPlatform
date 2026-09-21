"""触发执行编排：加载信源配置 → 连接器抓取 → 逐条发布 → 汇总结果"""
import logging
import uuid
from datetime import datetime, timezone
from pathlib import Path

import httpx
import yaml

from connectors.registry import build_connector
from mq.publisher import RawItemPublisher
from settings import DEFAULT_REQUEST_INTERVAL_SEC, INTERNAL_API_BASE_URL, SELECTORS_DIR

log = logging.getLogger(__name__)


def load_selector_config(source_code: str) -> dict:
    """本地 YAML 选择器配置（手册第三节：选择器配置用 YAML 独立文件，不写死在代码里）。
    文件名形如 cn_01_escn.yaml，按 source_code 前缀匹配。"""
    base = source_code.lower().replace('-', '_')
    path = Path(SELECTORS_DIR) / f'{base}.yaml'
    if not path.exists():
        matches = sorted(Path(SELECTORS_DIR).glob(f'{base}_*.yaml'))
        if not matches:
            raise FileNotFoundError(f'未找到信源选择器配置: {path}')
        path = matches[0]
    with open(path, encoding='utf-8') as f:
        cfg = yaml.safe_load(f)
    cfg.setdefault('source_code', source_code)
    return cfg


async def fetch_source_config(source_code: str) -> dict:
    """信源配置获取：优先 Java 主服务内部接口（手册第一节），失败回退本地 YAML"""
    if INTERNAL_API_BASE_URL:
        try:
            async with httpx.AsyncClient(timeout=10) as client:
                resp = await client.get(f'{INTERNAL_API_BASE_URL}/internal/sources/{source_code}')
                resp.raise_for_status()
                remote = resp.json()
                cfg = load_selector_config(source_code)  # 选择器仍在本地 YAML，远端配置合并覆盖
                cfg.update({k: v for k, v in remote.items() if v is not None})
                return cfg
        except Exception as exc:
            log.warning('内部接口获取信源配置失败(%s)，回退本地 YAML: %s', INTERNAL_API_BASE_URL, exc)
    return load_selector_config(source_code)


async def run_source(source_code: str, since: datetime | None = None,
                     limit: int | None = None, publisher: RawItemPublisher | None = None) -> dict:
    """执行一次采集：返回 {fetched, published, failed, errors} 汇总（对应 SRC-004 字段口径）"""
    task_id = uuid.uuid4().hex
    started = datetime.now(timezone.utc)
    cfg = await fetch_source_config(source_code)
    cfg.setdefault('request_interval_sec', DEFAULT_REQUEST_INTERVAL_SEC)
    connector = build_connector(cfg)
    publisher = publisher or RawItemPublisher()
    if publisher._exchange is None and publisher.mq_url:
        await publisher.connect()

    errors: list[dict] = []
    published = failed = 0
    refs: list = []
    try:
        refs = await connector.fetch_list(cfg, since)
        if limit:
            refs = refs[:limit]
        for ref in refs:
            try:
                draft = await connector.fetch_detail(ref)
                draft.task_id = task_id
                await publisher.publish(draft, task_id)
                published += 1
            except Exception as exc:
                failed += 1
                errors.append({'url': ref.detail_url, 'error': str(exc)[:300]})
                log.warning('[%s] 详情抓取/发布失败 %s: %s', source_code, ref.detail_url, exc)
    except Exception as exc:
        errors.append({'stage': 'fetch_list', 'error': str(exc)[:300]})
        log.exception('[%s] 列表抓取失败', source_code)

    summary = {
        'task_id': task_id,
        'source_code': source_code,
        'trigger_type': 'MANUAL',
        'started_at': started.isoformat(),
        'finished_at': datetime.now(timezone.utc).isoformat(),
        'fetched': len(refs),
        'published': published,
        'failed': failed,
        'errors': errors[:20],
        'mode': 'mq' if publisher._exchange is not None else 'stdout',
    }
    log.info('[%s] 采集完成: %s', source_code, summary)
    return summary
