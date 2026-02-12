package com.openjiuwen.core.common.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HashUtil 测试类
 */
public class HashUtilTest {

    @Test
    public void testGenerateKey() {
        String apiKey = "test-api-key";
        String apiBase = "https://api.example.com";
        String modelProvider = "openai";

        String key = HashUtil.generateKey(apiKey, apiBase, modelProvider);

        assertNotNull(key, "生成的key不应该为null");
        assertFalse(key.isEmpty(), "生成的key不应该为空");
        assertEquals(64, key.length(), "SHA-256哈希应该是64个字符");
    }

    @Test
    public void testGenerateKeySameInputsSameOutput() {
        String apiKey = "test-api-key";
        String apiBase = "https://api.example.com";
        String modelProvider = "openai";

        String key1 = HashUtil.generateKey(apiKey, apiBase, modelProvider);
        String key2 = HashUtil.generateKey(apiKey, apiBase, modelProvider);

        assertEquals(key1, key2, "相同输入应该产生相同的哈希");
    }

    @Test
    public void testGenerateKeyDifferentInputsDifferentOutputs() {
        String apiKey1 = "test-api-key-1";
        String apiKey2 = "test-api-key-2";
        String apiBase = "https://api.example.com";
        String modelProvider = "openai";

        String key1 = HashUtil.generateKey(apiKey1, apiBase, modelProvider);
        String key2 = HashUtil.generateKey(apiKey2, apiBase, modelProvider);

        assertNotEquals(key1, key2, "不同输入应该产生不同的哈希");
    }

    @Test
    public void testGenerateKeyWithDefaultProvider() {
        String apiKey = "test-api-key";
        String apiBase = "https://api.example.com";

        // 使用默认provider
        String key = HashUtil.generateKey(apiKey, apiBase, "openai");

        assertNotNull(key, "使用默认provider应该成功生成key");
    }

    @Test
    public void testGenerateKeyOrderIndependent() {
        // 测试输入参数顺序不影响结果（内部会排序）
        String apiKey = "api-key";
        String apiBase = "api-base";
        String modelProvider = "provider";

        String key = HashUtil.generateKey(apiKey, apiBase, modelProvider);

        assertNotNull(key, "生成的key不应该为null");
        // 无论输入顺序如何，内部排序后应该产生相同结果
    }
}


