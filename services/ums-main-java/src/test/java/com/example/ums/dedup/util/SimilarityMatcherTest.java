package com.example.ums.dedup.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimilarityMatcherTest {

    @Test
    void 完全相同为1() {
        assertEquals(1.0, SimilarityMatcher.jaccard("储能新政落地", "储能新政落地"));
    }

    @Test
    void 转载标题添加媒体前缀仍相似() {
        // 典型转载：长标题 + 媒体名前缀（20+ 字标题 3-gram 重合度高）
        assertTrue(SimilarityMatcher.similarTitle(
                "江苏省新型储能电站并网运行管理实施细则正式发布",
                "【中国储能网】江苏省新型储能电站并网运行管理实施细则正式发布"));
    }

    @Test
    void 不同事件不相似() {
        assertFalse(SimilarityMatcher.similarTitle(
                "江苏省新型储能电站并网运行管理实施细则正式发布",
                "美国关税调整影响光伏出口供应链成本"));
    }

    @Test
    void 标点与大小写差异不影响相似度() {
        assertTrue(SimilarityMatcher.similarTitle(
                "Battery Storage Costs Fall in 2026",
                "battery storage costs fall in 2026!"));
    }

    @Test
    void 空串相似度为0() {
        assertEquals(0.0, SimilarityMatcher.jaccard("", "任意标题"));
        assertEquals(0.0, SimilarityMatcher.jaccard(null, "任意标题"));
    }

    @Test
    void 短标题整串比对() {
        // 长度<3 归一化后整串一个 gram：完全相等才相似
        assertTrue(SimilarityMatcher.similarTitle("储能", "储能"));
        assertFalse(SimilarityMatcher.similarTitle("储能", "光伏"));
    }
}
