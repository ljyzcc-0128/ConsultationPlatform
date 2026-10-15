package com.example.ums.scheduler;

import com.example.ums.admin.model.CpSource;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 频率解析与到期判断单元测试。 */
class FrequencyParserTest {

    @Test
    void parse_commonFormats() {
        assertEquals(Duration.ofDays(1), FrequencyParser.parse("1/day"));
        assertEquals(Duration.ofHours(12), FrequencyParser.parse("2/day"));
        assertEquals(Duration.ofDays(7), FrequencyParser.parse("1/week"));
        assertEquals(Duration.ofHours(6), FrequencyParser.parse("4/day")); // 每天 4 次 = 每 6 小时
        assertEquals(Duration.ofMinutes(10), FrequencyParser.parse("6/h")); // 每小时 6 次
        assertEquals(Duration.ofMinutes(30), FrequencyParser.parse("2/hour"));
        // 非法格式回退 24h（种子数据只出现 1/day，宽松兜底）
        assertEquals(Duration.ofDays(1), FrequencyParser.parse("1/12h"));
    }

    @Test
    void parse_invalidFallsBackToDaily() {
        assertEquals(Duration.ofDays(1), FrequencyParser.parse(null));
        assertEquals(Duration.ofDays(1), FrequencyParser.parse(""));
        assertEquals(Duration.ofDays(1), FrequencyParser.parse("abc"));
        assertEquals(Duration.ofDays(1), FrequencyParser.parse("0/day"));
    }

    @Test
    void isValid_checks() {
        assertTrue(FrequencyParser.isValid("1/day"));
        assertTrue(FrequencyParser.isValid("2/WEEK"));
        assertFalse(FrequencyParser.isValid("daily"));
        assertFalse(FrequencyParser.isValid(null));
    }

    @Test
    void isDue_neverCrawled() {
        CpSource s = new CpSource();
        s.setCrawlFrequency("1/day");
        s.setLastCrawlTime(null);
        assertTrue(CrawlScheduler.isDue(s, LocalDateTime.now(ZoneOffset.UTC)));
    }

    @Test
    void isDue_withinInterval_notDue() {
        CpSource s = new CpSource();
        s.setCrawlFrequency("1/day");
        s.setLastCrawlTime(LocalDateTime.now(ZoneOffset.UTC).minusHours(2));
        assertFalse(CrawlScheduler.isDue(s, LocalDateTime.now(ZoneOffset.UTC)));
    }

    @Test
    void isDue_pastInterval_due() {
        CpSource s = new CpSource();
        s.setCrawlFrequency("1/day");
        s.setLastCrawlTime(LocalDateTime.now(ZoneOffset.UTC).minusHours(25));
        assertTrue(CrawlScheduler.isDue(s, LocalDateTime.now(ZoneOffset.UTC)));
    }

    @Test
    void isDue_boundary_due() {
        CpSource s = new CpSource();
        s.setCrawlFrequency("2/day"); // 12h
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        s.setLastCrawlTime(now.minusHours(12));
        // 严格等于间隔即到期（>= 语义：至少间隔 N 后即可再抓）
        assertTrue(CrawlScheduler.isDue(s, now));
    }
}
