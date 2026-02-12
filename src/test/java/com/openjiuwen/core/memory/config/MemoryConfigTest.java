/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */

package com.openjiuwen.core.memory.config;

import com.openjiuwen.core.memory.common.CryptoUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for memory configuration models.
 * Corresponds to Python: test_config.py
 */
class MemoryConfigTest {

    @Nested
    @DisplayName("Tests for MemoryEngineConfig")
    class TestMemoryEngineConfig {

        @Test
        @DisplayName("Test crypto_key validation: empty, valid 32 bytes, and invalid lengths")
        void testCryptoKeyValidation() {
            // Empty key is allowed (encryption disabled)
            MemoryEngineConfig config1 = MemoryEngineConfig.builder()
                .cryptoKey(new byte[0])
                .build();
            assertEquals(0, config1.getCryptoKey().length);

            // Valid 32-byte key
            byte[] validKey = "1234567890abcdef1234567890abcdef".getBytes(StandardCharsets.UTF_8);
            assertEquals(CryptoUtils.AES_KEY_LENGTH, validKey.length);
            MemoryEngineConfig config2 = MemoryEngineConfig.builder()
                .cryptoKey(validKey)
                .build();
            assertArrayEquals(validKey, config2.getCryptoKey());

            // Invalid length raises IllegalArgumentException
            IllegalArgumentException ex1 = assertThrows(IllegalArgumentException.class, () ->
                MemoryEngineConfig.builder()
                    .cryptoKey("short_key".getBytes(StandardCharsets.UTF_8))
                    .build()
            );
            assertTrue(ex1.getMessage().contains("Invalid crypto_key"));

            // 16 bytes (AES-128) also raises
            assertThrows(IllegalArgumentException.class, () ->
                MemoryEngineConfig.builder()
                    .cryptoKey("1234567890abcdef".getBytes(StandardCharsets.UTF_8))
                    .build()
            );
        }
    }
}

