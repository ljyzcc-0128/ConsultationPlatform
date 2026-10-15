package com.example.ums.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * URL 哈希工具：规范化 URL 的 SHA-256（hex 小写，64 字符）。
 * 消息中的 original_url 已由爬虫侧归一化（去 utm_* 跟踪参数），
 * 本服务直接对消息里的 URL 取哈希，与 cp_news.url_hash 口径一致。
 */
public final class UrlHashCalculator {

    private UrlHashCalculator() {}

    public static String sha256Hex(String url) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(url.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // JDK 标准算法，理论上不会缺失
            throw new IllegalStateException("SHA-256 算法不可用", e);
        }
    }
}
