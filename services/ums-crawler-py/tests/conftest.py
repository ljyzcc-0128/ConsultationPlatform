"""测试夹具：加载黄金样本与选择器配置（手册第八节：单元测试用保存的 HTML 样本，不发真实请求）"""
import sys
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))  # 使 `connectors` / `parsing` 等可作为顶级包导入

FIXTURES_DIR = Path(__file__).parent / 'fixtures'
SELECTORS_DIR = ROOT / 'selectors'


def load_fixture(name: str) -> str:
    return (FIXTURES_DIR / name).read_text(encoding='utf-8', errors='ignore')


def load_selector_config(source_code: str) -> dict:
    base = source_code.lower().replace('-', '_')
    path = SELECTORS_DIR / f'{base}.yaml'
    if not path.exists():
        matches = sorted(SELECTORS_DIR.glob(f'{base}_*.yaml'))
        path = matches[0]
    with open(path, encoding='utf-8') as f:
        return yaml.safe_load(f)
