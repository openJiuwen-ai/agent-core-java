/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.store.kv;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.UnifiedJedis;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

/**
 * Verifies atomic expiration writes and strict failure handling across Redis clients.
 *
 * @since 0.1.15
 */
class RedisStoreExpiryTest {
    @Test
    void jedisTextAndBinaryWritesUseAtomicSetex() {
        Jedis client = mock(Jedis.class);
        RedisStore store = new RedisStore(client);
        store.set("text", "value", Duration.ofSeconds(7));
        byte[] bytes = {1, 2};
        store.set("binary", bytes, Duration.ofSeconds(9));
        verify(client).setex("text", 7L, "value");
        verify(client).setex("binary".getBytes(java.nio.charset.StandardCharsets.UTF_8), 9L, bytes);
        verify(client, never()).set(anyString(), anyString());
        verify(client, never()).expire(anyString(), anyLong());
    }

    @Test
    void unifiedJedisWritesAndRenewalUseSeconds() {
        UnifiedJedis client = mock(UnifiedJedis.class);
        RedisStore store = new RedisStore(client);
        store.set("key", "value", Duration.ofSeconds(60));
        store.refreshTtl(List.of("key"), Duration.ofSeconds(60));
        verify(client).setex("key", 60L, "value");
        verify(client).expire("key", 60L);
    }

    @Test
    void unsupportedClientDoesNotFallBackToPlainSet() {
        PlainClient client = mock(PlainClient.class);
        RedisStore store = new RedisStore(client);
        assertThatThrownBy(() -> store.set("key", "value", Duration.ofSeconds(1)))
                .isInstanceOf(IllegalStateException.class);
        verify(client, never()).set(anyString(), anyString());
    }

    @Test
    void newRefreshPropagatesCommandFailure() {
        Jedis client = mock(Jedis.class);
        doThrow(new IllegalStateException("disconnected")).when(client).expire(anyString(), anyLong());
        assertThatThrownBy(() -> new RedisStore(client).refreshTtl(List.of("key"), Duration.ofSeconds(1)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void invalidDurationsAreRejectedBeforeIo() {
        Jedis client = mock(Jedis.class);
        RedisStore store = new RedisStore(client);
        for (Duration ttl : List.of(Duration.ZERO, Duration.ofSeconds(-1), Duration.ofMillis(1),
                Duration.ofSeconds((long) Integer.MAX_VALUE + 1))) {
            assertThatThrownBy(() -> store.set("key", "value", ttl))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        verify(client, never()).setex(anyString(), anyLong(), anyString());
    }

    /**
     * Reflectively accessible client stub without expiration support.
     */
    public interface PlainClient {
        /**
         * Writes a value without setting expiration.
         *
         * @param key the key to write
         * @param value the value to store
         */
        void set(String key, String value);
    }
}
