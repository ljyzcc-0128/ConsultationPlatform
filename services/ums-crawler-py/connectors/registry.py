"""按 source_code / fetch_method 映射到具体 Connector 实例（手册第三节 registry.py）"""
from connectors.api_source import ApiConnector
from connectors.base import Connector
from connectors.web_page import WebPageConnector


def build_connector(config: dict) -> Connector:
    """依据信源配置构造连接器；fetch_method: WEB/FILE/API/MANUAL（ADR-07）"""
    method = (config.get('fetch_method') or 'WEB').upper()
    if method == 'WEB':
        return WebPageConnector(config)
    if method == 'API':
        return ApiConnector(config)
    # FILE（人工上传 Excel/CSV）与 MANUAL（后台人工录入）在一期由 Java 主服务处理，
    # Python 采集服务不负责这两类，收到时明确报错而不是静默忽略
    raise ValueError(f'fetch_method={method} 不由 Python 采集服务处理（仅 WEB/API）')
