// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.core.common.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link HashUtil}.
 */
class HashUtilTest {

    /**
     * Test basic hash generation with all parameters.
     */
    @Test
    void testGenerateKey() {
        String apiKey = "test_key_123";
        String apiBase = "https://api.example.com";
        String modelProvider = "openai";

        String hash = HashUtil.generateKey(apiKey, apiBase, modelProvider);

        assertNotNull(hash);
        assertEquals(64, hash.length(), "SHA256 hash should be 64 characters");
        assertFalse(hash.contains("-"), "SHA256 hex string should not contain dashes");
    }

    /**
     * Test hash generation with default provider.
     */
    @Test
    void testGenerateKeyWithDefaultProvider() {
        String apiKey = "test_key_123";
        String apiBase = "https://api.example.com";

        String hash1 = HashUtil.generateKey(apiKey, apiBase, "openai");
        String hash2 = HashUtil.generateKey(apiKey, apiBase);

        assertEquals(hash1, hash2, "Default provider should be 'openai'");
    }

    /**
     * Test consistency - same inputs produce same hash.
     */
    @Test
    void testGenerateKeyConsistency() {
        String apiKey = "test_key_123";
        String apiBase = "https://api.example.com";
        String modelProvider = "openai";

        String hash1 = HashUtil.generateKey(apiKey, apiBase, modelProvider);
        String hash2 = HashUtil.generateKey(apiKey, apiBase, modelProvider);
        String hash3 = HashUtil.generateKey(apiKey, apiBase, modelProvider);

        assertEquals(hash1, hash2, "Same inputs should produce same hash");
        assertEquals(hash2, hash3, "Same inputs should produce same hash");
    }

    /**
     * Test order independence - the algorithm sorts inputs before hashing.
     */
    @ParameterizedTest
    @CsvSource({
        "'key1', 'base1', 'provider1', 'base1', 'key1', 'provider1'",
        "'key1', 'base1', 'provider1', 'provider1', 'key1', 'base1'",
        "'abc', 'def', 'ghi', 'ghi', 'abc', 'def'"
    })
    void testGenerateKeyOrderIndependence(String key1, String base1, String provider1,
                                          String key2, String base2, String provider2) {
        String hash1 = HashUtil.generateKey(key1, base1, provider1);
        String hash2 = HashUtil.generateKey(key2, base2, provider2);

        assertEquals(hash1, hash2, "Hash should be independent of input order");
    }
}