package com.example.ums.scheduler;

import java.time.Duration;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 信源抓取频率解析：cp_source.crawl_frequency（如 "1/day"、"2/day"、"1/week"、"6/h"）。
 * 语义：N/unit = 每 unit 时间抓 N 次 → 间隔 = unit / N（2/day = 每 12 小时）。
 * 调度时区 Asia/Shanghai（字段口径）；间隔比较与本地时钟无关。
 * 解析失败回退 24h（保守默认，不中断调度）。
 */
public final class FrequencyParser {

    private static final Pattern PATTERN = Pattern.compile("^(\\d+)\\s*/\\s*(day|week|hour|d|w|h)$",
            Pattern.CASE_INSENSITIVE);

    private static final Map<String, Duration> UNIT = Map.of(
            "day", Duration.ofDays(1),
            "d", Duration.ofDays(1),
            "week", Duration.ofDays(7),
            "w", Duration.ofDays(7),
            "hour", Duration.ofHours(1),
            "h", Duration.ofHours(1));

    public static final Duration DEFAULT = Duration.ofDays(1);

    private FrequencyParser() {
    }

    /** 解析频率为时间间隔；非法格式返回默认 24h。 */
    public static Duration parse(String frequency) {
        if (frequency == null || frequency.isBlank()) {
            return DEFAULT;
        }
        Matcher m = PATTERN.matcher(frequency.trim());
        if (!m.matches()) {
            return DEFAULT;
        }
        long n = Long.parseLong(m.group(1));
        Duration unit = UNIT.get(m.group(2).toLowerCase());
        if (n <= 0 || unit == null) {
            return DEFAULT;
        }
        // N/unit = 每 unit N 次 → 间隔 = unit / N（2/day → 12h）
        return unit.dividedBy(n);
    }

    /** 是否为可识别的合法格式（用于状态接口展示）。 */
    public static boolean isValid(String frequency) {
        return frequency != null && PATTERN.matcher(frequency.trim()).matches();
    }
}
