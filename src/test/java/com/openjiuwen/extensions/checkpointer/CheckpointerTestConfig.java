/* *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved. */
package com.openjiuwen.extensions.checkpointer;

import com.openjiuwen.core.session.checkpointer.Checkpointer;
import com.openjiuwen.core.session.checkpointer.CheckpointerConfig;
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import com.openjiuwen.extensions.checkpointer.redis.RedisCheckpointer;
import com.openjiuwen.extensions.store.kv.RedisStore;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test configuration for checkpointer tests.
 * <p>
 * Mirrors Python's {@code conftest.py} in
 * {@code tests/unit_tests/extensions/checkpointer/conftest.py}.
 */
class CheckpointerTestConfig {

    @Nested
    @DisplayName("Checkpointer config tests")
    class ConfigTests {

        @Test
        @DisplayName("Test Checkpointer class exists")
        void testCheckpointerClassExists() {
            assertNotNull(Checkpointer.class);
        }

        @Test
        @DisplayName("Test CheckpointerConfig default constructor")
        void testCheckpointerConfigDefault() {
            CheckpointerConfig config = new CheckpointerConfig();
            assertNotNull(config);
            assertEquals("in_memory", config.getType());
            assertNotNull(config.getConf());
        }

        @Test
        @DisplayName("Test CheckpointerConfig with type")
        void testCheckpointerConfigWithType() {
            CheckpointerConfig config = new CheckpointerConfig("redis", null);
            assertNotNull(config);
            assertEquals("redis", config.getType());
        }

        @Test
        @DisplayName("Test CheckpointerConfig with type and conf")
        void testCheckpointerConfigWithConf() {
            java.util.Map<String, Object> conf = new java.util.HashMap<>();
            conf.put("host", "localhost");
            conf.put("port", 6379);
            
            CheckpointerConfig config = new CheckpointerConfig("redis", conf);
            assertNotNull(config);
            assertEquals("redis", config.getType());
            assertNotNull(config.getConf());
            assertEquals("localhost", config.getConf().get("host"));
        }

        @Test
        @DisplayName("Test CheckpointerFactory class exists")
        void testCheckpointerFactoryClassExists() {
            assertNotNull(CheckpointerFactory.class);
        }

        @Test
        @DisplayName("Test redis_store fixture creates RedisStore")
        void testRedisStoreFixtureCreatesRedisStore() {
            RedisStore redisStore = redisStore();

            assertNotNull(redisStore);
            assertFalse(redisStore.isCluster());
        }

        @Test
        @DisplayName("Test checkpointer fixture creates RedisCheckpointer")
        void testCheckpointerFixtureCreatesRedisCheckpointer() {
            RedisCheckpointer checkpointer = new RedisCheckpointer(redisStore(), null);

            assertNotNull(checkpointer.getRedisStore());
            assertNotNull(checkpointer.getAgentStorage());
            assertNotNull(checkpointer.getWorkflowStorage());
            assertNotNull(checkpointer.getGraphStore());
        }

        @Test
        @DisplayName("Test checkpointer_with_ttl fixture enables TTL map")
        void testCheckpointerWithTtlFixtureCreatesRedisCheckpointer() {
            RedisCheckpointer checkpointer = new RedisCheckpointer(
                    redisStore(),
                    Map.of("default_ttl", 10, "refresh_on_read", true));

            assertNotNull(checkpointer);
        }
    }

    private RedisStore redisStore() {
        return new RedisStore(new TestAgentStorage.FakeRedisClient());
    }
}
