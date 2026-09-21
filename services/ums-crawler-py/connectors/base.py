"""连接器抽象基类与数据结构（依据《信源连接器开发与长期维护手册_Python版_v1.0》4.1节）"""
from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from datetime import datetime


@dataclass
class ItemRef:
    """列表页解析出的待抓取条目引用"""
    source_code: str
    detail_url: str
    list_title: str | None = None
    list_publish_time: datetime | None = None


@dataclass
class RawItemDraft:
    """详情页解析出的标准化原始条目，对应 raw.item.fetched 消息中的 item"""
    source_code: str
    title: str
    body: str | None
    author: str | None
    publish_time: datetime | None
    original_url: str
    language: str
    task_id: str | None = None
    extra: dict = field(default_factory=dict)


class Connector(ABC):
    """统一连接器接口（对应需求 SRC-003 / ADR-07）"""

    @abstractmethod
    async def fetch_list(self, config: dict, since: datetime | None = None) -> list[ItemRef]:
        """返回待抓取的条目引用列表"""

    @abstractmethod
    async def fetch_detail(self, ref: ItemRef) -> RawItemDraft:
        """抓取并解析单条详情，返回标准化草稿"""
