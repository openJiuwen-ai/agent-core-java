/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.memory.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MemoryCryptoTest {

    @Test
    void encryptThenDecryptRoundTrip() {
        byte[] key = "1234567890abcdef1234567890123456".getBytes();
        String plaintext = "hello, 我叫张三, xixi";

        String[] encrypted = MemoryCrypto.encrypt(key, plaintext);
        String decrypted = MemoryCrypto.decrypt(key, encrypted[0], encrypted[1], encrypted[2]);

        assertEquals(plaintext, decrypted);
    }

    @Test
    void encryptRejectsWrongKeyLength() {
        byte[] key = "1234567890abcedfgg".getBytes();

        assertThrows(IllegalArgumentException.class, () -> MemoryCrypto.encrypt(key, "你好"));
    }
}
