"""正文提取兜底（手册6.1节：选择器全部失效时用 trafilatura 最大文本块算法兜底）"""
import trafilatura


def extract_main_text(html: str) -> str | None:
    """对未知/变化后的页面结构做正文兜底提取，失败返回 None（正文 null 且需人工复核）"""
    if not html:
        return None
    try:
        return trafilatura.extract(html, include_comments=False, favor_recall=True)
    except Exception:
        return None
