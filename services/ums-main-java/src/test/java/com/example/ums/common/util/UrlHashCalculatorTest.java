package com.example.ums.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UrlHashCalculatorTest {

    @Test
    void sha256_已知向量_abc() {
        assertEquals(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                UrlHashCalculator.sha256Hex("abc"));
    }

    @Test
    void sha256_已知向量_空串() {
        assertEquals(
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                UrlHashCalculator.sha256Hex(""));
    }

    @Test
    void sha256_中文URL_utf8编码() {
        // 与 Python 端 hashlib.sha256(url.encode('utf-8')) 口径一致
        assertEquals(64, UrlHashCalculator.sha256Hex("https://www.escn.com.cn/news/储能.html").length());
    }

    @Test
    void sha256_null入参_抛出NPE() {
        assertThrows(NullPointerException.class, () -> UrlHashCalculator.sha256Hex(null));
    }
}
