/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.store.kv;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Map;

class RedisConnectionMappingTest {
    @Test
    void flatLegacyCredentialsAndClusterAreRetained() {
        var connection = RedisKVStoreProvider
                .connection(Map.of("host", "localhost", "port", 6380, "password", "p@ss", "cluster", true));
        assertTrue(connection.isClusterMode());
        var jedis = JedisRedisClientFactory.clientConfig(URI.create(connection.getConnectionUrl()),
                connection.getConnectionArgs());
        assertEquals("p@ss", jedis.getPassword());
    }

    @Test
    void nestedConfigurationRetainsTlsDatabaseAndTimeoutSeconds() {
        var connection = RedisKVStoreProvider
                .connection(Map.of("connection", Map.of("url", "rediss://user:pass@localhost:6380/2",
                        "connection_args", Map.of("socket_connect_timeout", 1.5, "socket_timeout", 2))));
        var jedis = JedisRedisClientFactory.clientConfig(URI.create(connection.getConnectionUrl()),
                connection.getConnectionArgs());
        assertTrue(jedis.isSsl());
        assertEquals(2, jedis.getDatabase());
        assertEquals("user", jedis.getUser());
        assertEquals(1500, jedis.getConnectionTimeoutMillis());
        assertEquals(2000, jedis.getSocketTimeoutMillis());
    }

    @Test
    void malformedConfigurationDoesNotFallBackToLocalhost() {
        assertThrows(IllegalArgumentException.class,
                () -> RedisKVStoreProvider.connection(Map.of("connection", Map.of())));
        assertThrows(IllegalArgumentException.class, () -> RedisKVStoreProvider
                .connection(Map.of("connection", Map.of("url", "redis://localhost"), "host", "other")));
        assertThrows(IllegalArgumentException.class, () -> JedisRedisClientFactory.timeout(Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> RedisKVStoreProvider.connection(Map.of("connection_args", "bad")));
        assertThrows(IllegalArgumentException.class, () -> RedisKVStoreProvider
                .connection(Map.of("connection", Map.of("url", "redis://localhost", "connection_args", "bad"))));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(doubles = { 0, -1, 65536, 1.5, Double.NaN,
            Double.POSITIVE_INFINITY })
    void invalidPortCannotSilentlySelectAnotherEndpoint(double port) {
        assertThrows(IllegalArgumentException.class, () -> RedisKVStoreProvider.connection(Map.of("port", port)));
    }

    @Test
    void malformedLegacyFieldsFailInsteadOfUsingDefaults() {
        for (Map<String, Object> conf : java.util.List.of(Map.<String, Object>of("port", "6379"),
                Map.<String, Object>of("host", 123), Map.<String, Object>of("host", " "),
                Map.<String, Object>of("tls", "wrong"), Map.<String, Object>of("cluster", "wrong"))) {
            assertThrows(IllegalArgumentException.class, () -> RedisKVStoreProvider.connection(conf));
        }
    }

    @Test
    void suppliedClientTakesPriorityAndLegacyConfigurationTypeStillWorks() {
        Object client = new Object();
        var old = com.openjiuwen.extensions.checkpointer.redis.RedisConnectionConfig.fromMap(
                Map.of("redis_client", client, "url", "redis://unused:1"));
        old.validate();
        assertSame(client, old.getRedisClient());
        assertSame(client, RedisKVStoreProvider.connection(Map.of("connection",
                Map.of("redis_client", client, "url", "redis://unused:1"))).getRedisClient());
    }

    @Test
    void retryAndTimeoutRejectFractionalCountsAndOverflow() {
        for (Object attempts : java.util.List.of(0, -1, 1.5, Double.NaN)) {
            assertThrows(IllegalArgumentException.class,
                    () -> JedisRedisClientFactory.attempts(Map.of("retry", Map.of("attempts", attempts))));
        }
        assertThrows(IllegalArgumentException.class, () -> JedisRedisClientFactory.timeout(Double.MAX_VALUE));
        assertThrows(IllegalArgumentException.class,
                () -> RedisOperations.ttlSeconds(java.time.Duration.ofSeconds(Long.MAX_VALUE, 1)));
        assertEquals(1, RedisOperations.ttlSeconds(java.time.Duration.ofNanos(1)));
    }

    @Test
    void invalidPoolAndClusterDatabaseFailBeforeOpeningConnection() {
        for (Map<String, Object> args : java.util.List.of(
                Map.<String, Object>of("pool", "invalid"),
                Map.<String, Object>of("pool", Map.of("min_idle", 9, "max_idle", 1)),
                Map.<String, Object>of("pool", Map.of("max_total", 0)),
                Map.<String, Object>of("pool", Map.of("max_wait", -1)))) {
            var config = RedisConnectionConfig.fromMap(Map.of("url", "redis://127.0.0.1:1", "connection_args", args));
            assertThrows(IllegalArgumentException.class, () -> JedisRedisClientFactory.create(config));
        }
        var cluster = new RedisConnectionConfig("redis+cluster://127.0.0.1:1/2");
        assertThrows(IllegalArgumentException.class, () -> JedisRedisClientFactory.create(cluster));
    }

}
