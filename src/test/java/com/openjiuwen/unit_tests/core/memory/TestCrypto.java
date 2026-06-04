/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.memory;

import com.openjiuwen.core.memory.common.MemoryCrypto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for Crypto utilities.
 *
 * <p>Mirrors Python's tests/unit_tests/core/memory/test_crypto.py.</p>
 */
class TestCrypto {

    @Test
    @DisplayName("test encrypt")
    void testEncrypt() {
        byte[] testKey = "1234567890abcdef1234567890123456".getBytes(StandardCharsets.UTF_8);
        String testData = "hello, 我叫张三, xixi";

        String[] encrypted = MemoryCrypto.encrypt(testKey, testData);
        String decrypted = MemoryCrypto.decrypt(testKey, encrypted[0], encrypted[1], encrypted[2]);

        assertEquals(testData, decrypted);
    }

    @Test
    @DisplayName("test key error")
    void testKeyError() {
        byte[] invalidKey = "1234567890abcedfgg".getBytes(StandardCharsets.UTF_8);
        String testData = "你好, 我叫李四";

        assertThrows(IllegalArgumentException.class, () -> MemoryCrypto.encrypt(invalidKey, testData));
    }
}
