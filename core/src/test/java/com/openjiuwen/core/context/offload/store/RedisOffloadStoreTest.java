/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.offload.store;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openjiuwen.core.context.offload.api.OffloadErrorCode;
import com.openjiuwen.core.context.offload.api.OffloadException;
import com.openjiuwen.core.context.offload.model.OffloadBackend;
import com.openjiuwen.core.context.offload.model.OffloadNamespace;
import com.openjiuwen.core.context.offload.model.OffloadPayload;
import com.openjiuwen.core.context.offload.model.OffloadRedisConfig;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisException;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

/**
 * {@link RedisOffloadStore} unit tests with a mocked {@code JedisPool}:
 * SET/SETEX/GET/DEL command mapping, TTL clamping and JedisException →
 * OFFLOAD_REDIS_DOWN translation (架构文档 §2.3, AC-2-06).
 */
class RedisOffloadStoreTest {
    private static final OffloadNamespace NS = OffloadNamespace.of("t1", "s1", "c1");
    private static final String HANDLE = "handle-1";
    private static final String EXPECTED_KEY = NS.toRedisKey(HANDLE);

    private static OffloadPayload payload() {
        List<BaseMessage> messages = List.of(new UserMessage("hello redis"));
        return OffloadPayload.create(HANDLE, NS, messages, null);
    }

    private static JedisPool mockPool(Jedis jedis) {
        JedisPool pool = mock(JedisPool.class);
        when(pool.getResource()).thenReturn(jedis);
        return pool;
    }

    @Test
    void backendIsRedis() {
        JedisPool pool = mockPool(mock(Jedis.class));
        RedisOffloadStore store = new RedisOffloadStore(pool);
        assertEquals(OffloadBackend.REDIS, store.backend());
        store.close(); // borrowed pool: close must be a no-op
    }

    @Test
    void saveWithSubSecondTtlClampsToMinimumSeconds() {
        Jedis jedis = mock(Jedis.class);
        JedisPool pool = mockPool(jedis);
        RedisOffloadStore store = new RedisOffloadStore(pool);
        OffloadPayload payload = payload();

        store.save(NS, HANDLE, payload, Duration.ofMillis(1500));

        // 1500ms → 1s after clamping to the Redis minimum TTL.
        verify(jedis).setex(EXPECTED_KEY, 1L, payload.serializeJson());
        store.close();
    }

    @Test
    void saveWithTtlUsesSetex() {
        Jedis jedis = mock(Jedis.class);
        JedisPool pool = mockPool(jedis);
        RedisOffloadStore store = new RedisOffloadStore(pool);
        OffloadPayload payload = payload();

        store.save(NS, HANDLE, payload, Duration.ofSeconds(30));

        verify(jedis).setex(EXPECTED_KEY, 30L, payload.serializeJson());
        store.close();
    }

    @Test
    void saveWithNullTtlUsesPlainSet() {
        Jedis jedis = mock(Jedis.class);
        JedisPool pool = mockPool(jedis);
        RedisOffloadStore store = new RedisOffloadStore(pool);
        OffloadPayload payload = payload();

        store.save(NS, HANDLE, payload, null);

        verify(jedis).set(EXPECTED_KEY, payload.serializeJson());
        store.close();
    }

    @Test
    void saveWithZeroOrNegativeTtlUsesPlainSet() {
        Jedis jedis = mock(Jedis.class);
        JedisPool pool = mockPool(jedis);
        RedisOffloadStore store = new RedisOffloadStore(pool);
        OffloadPayload payload = payload();

        store.save(NS, HANDLE, payload, Duration.ZERO);
        verify(jedis).set(EXPECTED_KEY, payload.serializeJson());

        store.save(NS, HANDLE, payload, Duration.ofSeconds(-5));
        verify(jedis, times(2)).set(EXPECTED_KEY, payload.serializeJson());
        store.close();
    }

    @Test
    void saveMapsJedisExceptionToRedisDown() {
        Jedis jedis = mock(Jedis.class);
        JedisPool pool = mockPool(jedis);
        // stub both command shapes: plain SET (null TTL) and SETEX (positive TTL)
        when(jedis.set(anyString(), anyString())).thenThrow(new JedisException("connection refused"));
        when(jedis.setex(anyString(), anyLong(), anyString())).thenThrow(new JedisException("connection refused"));
        RedisOffloadStore store = new RedisOffloadStore(pool);

        OffloadException exPlain = assertThrows(OffloadException.class,
                () -> store.save(NS, HANDLE, payload(), null));
        assertEquals(OffloadErrorCode.OFFLOAD_REDIS_DOWN, exPlain.code());

        OffloadException exTtl = assertThrows(OffloadException.class,
                () -> store.save(NS, HANDLE, payload(), Duration.ofSeconds(30)));
        assertEquals(OffloadErrorCode.OFFLOAD_REDIS_DOWN, exTtl.code());

        store.close();
    }

    @Test
    void loadReturnsPayloadAndDeserializes() {
        Jedis jedis = mock(Jedis.class);
        JedisPool pool = mockPool(jedis);
        OffloadPayload payload = payload();
        when(jedis.get(EXPECTED_KEY)).thenReturn(payload.serializeJson());
        RedisOffloadStore store = new RedisOffloadStore(pool);

        OffloadPayload loaded = store.load(NS, HANDLE);

        assertEquals(payload.offloadHandle(), loaded.offloadHandle());
        assertTrue(loaded.sha256Matches());
        store.close();
    }

    @Test
    void loadMissingKeyThrowsOffloadMissing() {
        Jedis jedis = mock(Jedis.class);
        JedisPool pool = mockPool(jedis);
        when(jedis.get(EXPECTED_KEY)).thenReturn(null);
        RedisOffloadStore store = new RedisOffloadStore(pool);

        OffloadException ex = assertThrows(OffloadException.class, () -> store.load(NS, HANDLE));
        assertEquals(OffloadErrorCode.OFFLOAD_MISSING, ex.code());
        store.close();
    }

    @Test
    void loadUnparsableJsonThrowsCorrupt() {
        Jedis jedis = mock(Jedis.class);
        JedisPool pool = mockPool(jedis);
        when(jedis.get(EXPECTED_KEY)).thenReturn("{ broken json");
        RedisOffloadStore store = new RedisOffloadStore(pool);

        OffloadException ex = assertThrows(OffloadException.class, () -> store.load(NS, HANDLE));
        assertEquals(OffloadErrorCode.OFFLOAD_CORRUPT, ex.code());
        store.close();
    }

    @Test
    void loadMapsJedisExceptionToRedisDown() {
        Jedis jedis = mock(Jedis.class);
        JedisPool pool = mockPool(jedis);
        when(jedis.get(anyString())).thenThrow(new JedisException("timeout"));
        RedisOffloadStore store = new RedisOffloadStore(pool);

        OffloadException ex = assertThrows(OffloadException.class, () -> store.load(NS, HANDLE));
        assertEquals(OffloadErrorCode.OFFLOAD_REDIS_DOWN, ex.code());
        store.close();
    }

    @Test
    void deleteReturnsTrueWhenKeyExisted() {
        Jedis jedis = mock(Jedis.class);
        JedisPool pool = mockPool(jedis);
        when(jedis.del(EXPECTED_KEY)).thenReturn(1L);
        RedisOffloadStore store = new RedisOffloadStore(pool);

        assertTrue(store.delete(NS, HANDLE));
        verify(jedis).del(EXPECTED_KEY);
        store.close();
    }

    @Test
    void deleteReturnsFalseWhenKeyAbsent() {
        Jedis jedis = mock(Jedis.class);
        JedisPool pool = mockPool(jedis);
        when(jedis.del(EXPECTED_KEY)).thenReturn(0L);
        RedisOffloadStore store = new RedisOffloadStore(pool);

        assertEquals(false, store.delete(NS, HANDLE));
        store.close();
    }

    @Test
    void deleteMapsJedisExceptionToRedisDown() {
        Jedis jedis = mock(Jedis.class);
        JedisPool pool = mockPool(jedis);
        when(jedis.del(anyString())).thenThrow(new JedisException("down"));
        RedisOffloadStore store = new RedisOffloadStore(pool);

        OffloadException ex = assertThrows(OffloadException.class, () -> store.delete(NS, HANDLE));
        assertEquals(OffloadErrorCode.OFFLOAD_REDIS_DOWN, ex.code());
        store.close();
    }

    @Test
    void existsReportsKeyPresence() {
        Jedis jedis = mock(Jedis.class);
        JedisPool pool = mockPool(jedis);
        when(jedis.exists(EXPECTED_KEY)).thenReturn(true);
        RedisOffloadStore store = new RedisOffloadStore(pool);

        assertTrue(store.exists(NS, HANDLE));
        verify(jedis).exists(EXPECTED_KEY);
        store.close();
    }

    @Test
    void attackNamespaceSegmentsSanitizeInRedisKeys() {
        // TC_241 (Redis leg): the storage key derives from the sanitized
        // namespace — traversal and injection segments collapse to safe
        // charset tokens, keys never grow extra delimiter-free hazards, and
        // every attacked key stays within the offload: prefix scheme.
        List<String> attacks = List.of("..", ".", "", " ../../etc ", "a:b", "a\\b", "s<>|:*?");
        for (int i = 0; i < attacks.size(); i++) {
            String attack = attacks.get(i);
            OffloadNamespace evil = OffloadNamespace.of(attack, attack, attack);
            String key = evil.toRedisKey("h" + i);
            assertTrue(key.startsWith("offload:"), "key must keep the scheme prefix: " + key);
            for (String segment : key.split(":")) {
                assertTrue(segment.matches("[A-Za-z0-9._\\-]+"),
                        "key segment must be sanitized: " + segment + " for " + attack);
                assertFalse(".".equals(segment) || "..".equals(segment),
                        "traversal segment must not survive in keys: " + key);
            }
        }
        // Delimiter neutrality: different attacked tenants must not melt into
        // the same key as an unrelated clean namespace.
        assertNotEquals(NS.toRedisKey("h"), OffloadNamespace.of("..", "s1", "c1").toRedisKey("h"));
    }

    @Test
    void ownedPoolCtorDoesNotReachNetworkAtConstruction() {
        // Building the store from config must not touch the network; pool
        // creation is lazy. Constructing and closing must be side-effect safe.
        OffloadRedisConfig config = new OffloadRedisConfig();
        config.setHost("127.0.0.1");
        config.setPort(1); // nothing listens here; construction must still succeed
        RedisOffloadStore store = assertDoesNotThrow(() -> new RedisOffloadStore(config));
        assertDoesNotThrow(store::close);
    }

    @Test
    void closeIsIdempotentOnOwnedPool() {
        OffloadRedisConfig config = new OffloadRedisConfig();
        RedisOffloadStore store = new RedisOffloadStore(config);
        assertDoesNotThrow(store::close);
        assertDoesNotThrow(store::close);
    }
}
