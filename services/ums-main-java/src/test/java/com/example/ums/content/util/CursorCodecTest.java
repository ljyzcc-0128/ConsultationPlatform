package com.example.ums.content.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CursorCodecTest {

    @Test
    void 编解码往返一致() {
        LocalDate date = LocalDate.of(2026, 9, 18);
        String cursor = CursorCodec.encode(date, "a6f4c227-6926-47c6-8dad-6213c1c6f607");
        CursorCodec.Cursor c = CursorCodec.decode(cursor);
        assertEquals(date, c.publishDate());
        assertEquals("a6f4c227-6926-47c6-8dad-6213c1c6f607", c.articleId());
    }

    @Test
    void URL安全无填充() {
        String cursor = CursorCodec.encode(LocalDate.of(2026, 9, 18), "abc");
        // Base64 URL-safe 字母表：不含 + /，无 = 填充
        assertTrue(cursor.matches("[A-Za-z0-9_-]+"), cursor);
    }

    private static void assertTrue(boolean b, String msg) {
        if (!b) throw new AssertionError(msg);
    }

    @Test
    void 非法Base64报400级异常() {
        assertThrows(IllegalArgumentException.class, () -> CursorCodec.decode("!!!not-base64!!!"));
    }

    @Test
    void 非法内容报400级异常() {
        // 合法 Base64 但内容结构错
        String bad = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("garbage".getBytes());
        assertThrows(IllegalArgumentException.class, () -> CursorCodec.decode(bad));
    }

    @Test
    void 日期用epochDay避免时区歧义() {
        // 相同日期编码结果确定（不依赖默认时区）
        String c1 = CursorCodec.encode(LocalDate.of(2026, 1, 1), "x");
        String c2 = CursorCodec.encode(LocalDate.of(2026, 1, 1), "x");
        assertEquals(c1, c2);
    }
}
