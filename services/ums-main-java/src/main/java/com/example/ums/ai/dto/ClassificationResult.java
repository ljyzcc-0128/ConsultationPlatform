package com.example.ums.ai.dto;

import java.util.List;

/**
 * 分类结果（主题标签，与 cp_source.category 同字典）。
 */
public record ClassificationResult(List<String> categories) {
}
