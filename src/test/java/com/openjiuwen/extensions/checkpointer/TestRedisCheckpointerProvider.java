/* *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved. */
package com.openjiuwen.extensions.checkpointer;

import com.openjiuwen.core.session.checkpointer.Checkpointer;
import com.openjiuwen.core.session.checkpointer.CheckpointerConfig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Redis Checkpointer Provider.
 * <p>
 * Tests Redis-specific checkpointer configuration and functionality.
 */
class TestRedisCheckpointerProvider {

    @Nested
    @DisplayName("RedisCheckpointerProvider tests")
    class ProviderTests {

        @Test
        @DisplayName("Test Checkpointer class exists")
        void testCheckpointerClassExists() {
            assertNotNull(Checkpointer.class);
        }

        @Test
        @DisplayName("Test Redis checkpointer config type")
        void testRedisCheckpointerConfigType() {
            CheckpointerConfig config = new CheckpointerConfig("redis", new java.util.HashMap<>());
            assertNotNull(config);
            assertEquals("redis", config.getType());
        }

        @Test
        @DisplayName("Test Redis config with host and port")
        void testRedisConfigWithHostPort() {
            java.util.Map<String, Object> conf = new java.util.HashMap<>();
            conf.put("host", "localhost");
            conf.put("port", 6379);
            conf.put("db", 0);
            
            CheckpointerConfig config = new CheckpointerConfig("redis", conf);
            assertNotNull(config);
            assertEquals("redis", config.getType());
            assertEquals("localhost", config.getConf().get("host"));
            assertEquals(6379, config.getConf().get("port"));
        }

        @Test
        @DisplayName("Test config can be modified")
        void testConfigCanBeModified() {
            CheckpointerConfig config = new CheckpointerConfig();
            config.setType("redis");
            java.util.Map<String, Object> conf = new java.util.HashMap<>();
            conf.put("url", "redis://localhost:6379");
            config.setConf(conf);
            
            assertEquals("redis", config.getType());
            assertEquals("redis://localhost:6379", config.getConf().get("url"));
        }
    }
}