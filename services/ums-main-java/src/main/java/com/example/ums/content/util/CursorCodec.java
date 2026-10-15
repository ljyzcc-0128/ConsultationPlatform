package com.example.ums.content.util;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;

/**
 * 时间线游标编解码：Base64("publishDate|articleId")。
 * 复合游标配合 (publish_date, article_id) 索引实现 keyset 分页，深翻页性能稳定（无 OFFSET 扫描）。
 */
public final class CursorCodec {

    private CursorCodec() {}

    public record Cursor(LocalDate publishDate, String articleId) {}

    public static String encode(LocalDate publishDate, String articleId) {
        String raw = publishDate.toEpochDay() + "|" + articleId;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** 解析失败抛 IllegalArgumentException（Controller 转 400） */
    public static Cursor decode(String cursor) {
        String raw;
        try {
            raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("游标格式非法");
        }
        int sep = raw.indexOf('|');
        if (sep <= 0 || sep == raw.length() - 1) {
            throw new IllegalArgumentException("游标内容非法");
        }
        try {
            LocalDate date = LocalDate.ofEpochDay(Long.parseLong(raw.substring(0, sep)));
            return new Cursor(date, raw.substring(sep + 1));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("游标内容非法");
        }
    }
}
