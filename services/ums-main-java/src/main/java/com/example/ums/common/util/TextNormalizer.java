package com.example.ums.common.util;

import java.text.Normalizer;

/**
 * 文本归一化（去重聚用）：NFKC 全角转半角 + 小写 + 仅保留汉字/字母/数字（去标点空白）。
 * 中文与英文标题/正文经此处理后可直接比对或计算指纹。
 */
public final class TextNormalizer {

    private TextNormalizer() {}

    public static String normalize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String nfkc = Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase();
        StringBuilder sb = new StringBuilder(nfkc.length());
        nfkc.codePoints().forEach(cp -> {
            if (isKept(cp)) {
                sb.appendCodePoint(cp);
            }
        });
        return sb.toString();
    }

    /** 汉字（含扩展A）/ 英文字母 / 数字 保留，其余（标点、空白、符号、emoji）丢弃 */
    private static boolean isKept(int cp) {
        return (cp >= 0x4E00 && cp <= 0x9FFF)      // CJK 统一汉字
                || (cp >= 0x3400 && cp <= 0x4DBF)  // 扩展A
                || (cp >= 'a' && cp <= 'z')
                || (cp >= '0' && cp <= '9');
    }
}
