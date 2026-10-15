package com.example.ums.dedup.util;

import com.example.ums.common.util.TextNormalizer;

import java.util.HashSet;
import java.util.Set;

/**
 * 标题相似度：归一化后 3-gram 字符集合的 Jaccard 系数。
 * 转载场景标题常保留主体仅微调（添加媒体名、更换标点），3-gram Jaccard 能捕捉这种相似性。
 */
public final class SimilarityMatcher {

    /** 归一化标题相似阈值：≥ 该值视为同一事件 */
    public static final double TITLE_THRESHOLD = 0.75;

    /** 标题相似还需满足的最大发布日期差（天），防止同名不同事（周期性同名报道） */
    public static final int MAX_DATE_GAP_DAYS = 7;

    private SimilarityMatcher() {}

    public static boolean similarTitle(String titleA, String titleB) {
        return jaccard(titleA, titleB) >= TITLE_THRESHOLD;
    }

    /** 0.0（不相似）~ 1.0（相同） */
    public static double jaccard(String rawA, String rawB) {
        Set<String> gramsA = grams(rawA);
        Set<String> gramsB = grams(rawB);
        if (gramsA.isEmpty() || gramsB.isEmpty()) {
            return 0.0;
        }
        Set<String> intersection = new HashSet<>(gramsA);
        intersection.retainAll(gramsB);
        Set<String> union = new HashSet<>(gramsA);
        union.addAll(gramsB);
        return (double) intersection.size() / union.size();
    }

    /** 归一化后按 3 个码点切分；长度不足 3 的整串作为一个 gram */
    private static Set<String> grams(String raw) {
        String s = TextNormalizer.normalize(raw);
        Set<String> result = new HashSet<>();
        if (s.isEmpty()) {
            return result;
        }
        int[] cps = s.codePoints().toArray();
        if (cps.length < 3) {
            result.add(s);
            return result;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i + 2 < cps.length; i++) {
            sb.setLength(0);
            sb.appendCodePoint(cps[i])
              .appendCodePoint(cps[i + 1])
              .appendCodePoint(cps[i + 2]);
            result.add(sb.toString());
        }
        return result;
    }
}
