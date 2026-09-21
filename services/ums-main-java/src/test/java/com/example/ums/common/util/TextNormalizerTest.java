package com.example.ums.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextNormalizerTest {

    @Test
    void 中文标题去标点空白() {
        assertEquals("江苏储能新政发布",
                TextNormalizer.normalize("江苏储能新政发布！ "));
    }

    @Test
    void 全角转半角并小写() {
        // 全角字母数字 ＢＥＳＳ２０２６ → bass2026（NFKC 归一后小写）
        assertEquals("bess2026", TextNormalizer.normalize("ＢＥＳＳ２０２６"));
    }

    @Test
    void 中英混合去标点() {
        assertEquals("bess装机量创新高100gwh",
                TextNormalizer.normalize("BESS 装机量创新高：100 GWh！"));
    }

    @Test
    void 英文标题小写去标点空白() {
        // 比对场景空白也去掉（连写不影响后续指纹/gram 计算）
        assertEquals("batterystoragecostsfall",
                TextNormalizer.normalize("Battery-Storage Costs, Fall!"));
    }

    @Test
    void null与空白返回空串() {
        assertEquals("", TextNormalizer.normalize(null));
        assertEquals("", TextNormalizer.normalize("  "));
    }

    @Test
    void emoji丢弃与带圈数字NFKC归一() {
        // ①(U+2460) 经 NFKC 转为 ASCII "1"（数字保留），⚡ emoji 丢弃
        assertEquals("储能1", TextNormalizer.normalize("储⚡能①"));
    }
}
