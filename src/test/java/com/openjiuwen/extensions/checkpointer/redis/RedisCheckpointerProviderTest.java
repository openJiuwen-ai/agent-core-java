
package com.openjiuwen.extensions.checkpointer.redis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.openjiuwen.core.session.checkpointer.Checkpointer;
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;

import redis.clients.jedis.util.JedisClusterCRC16;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

class RedisCheckpointerProviderTest {
    @Test
    void providerUsesSuppliedRedisClient() {
        RedisCheckpointer.Provider provider = new RedisCheckpointer.Provider();
        FakeRedisClient redisClient = new FakeRedisClient();

        Checkpointer checkpointer = provider.create(Map.of("connection", Map.of("redis_client", redisClient)));

        RedisCheckpointer redisCheckpointer = assertInstanceOf(RedisCheckpointer.class, checkpointer);
        redisCheckpointer.getRedisStore().set("session-1:agent:key", "value");

        assertTrue(redisCheckpointer.sessionExists("session-1"));
    }

    @Test
    @Tag("integration")
    void providerBuildsStandaloneClientFromUrlAndPropagatesTtl() throws Exception {
        String redisIp = System.getenv("REDIS_IP");
        assumeTrue(redisIp != null && !redisIp.isBlank(), "Missing required env: REDIS_IP");

        RedisCheckpointer.Provider provider = new RedisCheckpointer.Provider();
        Map<String, Object> config = Map.of(
                "connection", Map.of("url", "redis://" + redisIp + ":6379"),
                "ttl", Map.of("default_ttl", 5, "refresh_on_read", true));

        RedisCheckpointer writer = assertInstanceOf(RedisCheckpointer.class, provider.create(config));
        RedisCheckpointer reader = assertInstanceOf(RedisCheckpointer.class, provider.create(config));
        String sessionId = "redis-provider-" + UUID.randomUUID();
        String key = sessionId + ":workflow:key";

        try {
            assertFalse(writer.getRedisStore().isCluster());
            writer.getRedisStore().set(key, "value");
            assertEquals("value", reader.getRedisStore().get(key));
            assertTrue(reader.sessionExists(sessionId));
            assertEquals(300, readField(writer.getAgentStorage(), "ttlSeconds"));
            assertEquals(Boolean.TRUE, readField(writer.getAgentStorage(), "refreshOnRead"));
        } finally {
            writer.getRedisStore().delete(key);
            writer.close();
            reader.close();
        }
    }

    @Test
    @Tag("integration")
    void clusterSessionExistsAndReleaseWorkAcrossHashSlots() {
        String clusterUrl = System.getenv("REDIS_CLUSTER_URL");
        assumeTrue(clusterUrl != null && !clusterUrl.isBlank(), "Missing required env: REDIS_CLUSTER_URL");

        RedisCheckpointer.Provider provider = new RedisCheckpointer.Provider();
        Map<String, Object> config = Map.of(
                "connection", Map.of("url", clusterUrl, "cluster_mode", true));
        RedisCheckpointer checkpointer = assertInstanceOf(RedisCheckpointer.class, provider.create(config));
        String sessionId = "redis-cluster-provider-" + UUID.randomUUID();
        String firstKey = sessionId + ":{core-slot-a}:workflow";
        String secondKey = sessionId + ":{core-slot-b}:agent";
        assertNotEquals(JedisClusterCRC16.getSlot(firstKey), JedisClusterCRC16.getSlot(secondKey));

        try {
            checkpointer.getRedisStore().set(firstKey, "first");
            checkpointer.getRedisStore().set(secondKey, "second");

            assertTrue(checkpointer.sessionExists(sessionId));
            checkpointer.release(sessionId);

            assertFalse(checkpointer.sessionExists(sessionId));
            assertNull(checkpointer.getRedisStore().get(firstKey));
            assertNull(checkpointer.getRedisStore().get(secondKey));
        } finally {
            checkpointer.getRedisStore().delete(firstKey);
            checkpointer.getRedisStore().delete(secondKey);
            checkpointer.close();
        }
    }

    @Test
    void clusterUrlsNormalizeAndFactoryUsesRedisProvider() {
        RedisConnectionConfig connection =
            RedisConnectionConfig.fromMap(Map.of("url", "redis+cluster://cluster.example.invalid:7000"));

        assertTrue(connection.isClusterMode());
        assertEquals("redis://cluster.example.invalid:7000", connection.getConnectionUrl());

        FakeRedisClient redisClient = new FakeRedisClient();
        Checkpointer checkpointer = CheckpointerFactory.create(
                "redis", Map.of("connection", Map.of("redis_client", redisClient)));

        RedisCheckpointer redisCheckpointer = assertInstanceOf(RedisCheckpointer.class, checkpointer);
        assertNotNull(redisCheckpointer.graphStore());
    }

    @Test
    void invalidConfigRaisesHelpfulError() {
        RedisCheckpointer.Provider provider = new RedisCheckpointer.Provider();

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> provider.create(Map.of()));

        assertTrue(error.getMessage().contains("connection"));
    }

    @Test
    void invalidUrlClientArgumentsRaiseHelpfulError() {
        RedisCheckpointer.Provider provider = new RedisCheckpointer.Provider();
        Map<String, Object> config = Map.of("connection", Map.of(
                "url", "redis://127.0.0.1:6379",
                "connection_args", Map.of("socket_connect_timeout", 0)));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> provider.create(config));

        assertTrue(error.getMessage().contains("Failed to create Redis client"));
        assertInstanceOf(IllegalArgumentException.class, error.getCause());
    }

    private static Object readField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getSuperclass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    static class FakeRedisClient {
        private final Map<String, Object> values = new ConcurrentHashMap<>();
        private final Map<String, Long> expiryAt = new ConcurrentHashMap<>();

        public void set(String key, Object value) {
            cleanup(key);
            values.put(key, value);
            expiryAt.remove(key);
        }

        public boolean set(String key, Object value, boolean nx, Integer expiry) {
            cleanup(key);
            if (nx && values.containsKey(key)) {
                return false;
            }
            values.put(key, value);
            if (expiry != null && expiry > 0) {
                expiryAt.put(key, System.currentTimeMillis() + expiry * 1000L);
            } else {
                expiryAt.remove(key);
            }
            return true;
        }

        public Object get(String key) {
            cleanup(key);
            return values.get(key);
        }

        public long exists(String key) {
            cleanup(key);
            return values.containsKey(key) ? 1L : 0L;
        }

        public long delete(String... keys) {
            long deleted = 0L;
            for (String key : keys) {
                cleanup(key);
                if (values.remove(key) != null) {
                    expiryAt.remove(key);
                    deleted++;
                }
            }
            return deleted;
        }

        public List<Object> mget(String... keys) {
            return java.util.Arrays.stream(keys).map(this::get).toList();
        }

        public List<String> scanIter(String pattern) {
            String prefix = pattern.endsWith("*") ? pattern.substring(0, pattern.length() - 1) : pattern;
            return values.keySet().stream().peek(this::cleanup).filter(values::containsKey)
                    .filter(key -> key.startsWith(prefix)).sorted().toList();
        }

        public boolean expire(String key, int ttlSeconds) {
            cleanup(key);
            if (!values.containsKey(key)) {
                return false;
            }
            expiryAt.put(key, System.currentTimeMillis() + ttlSeconds * 1000L);
            return true;
        }

        public FakeRedisPipeline pipeline() {
            return new FakeRedisPipeline(this);
        }

        private void cleanup(String key) {
            Long expiresAt = expiryAt.get(key);
            if (expiresAt != null && expiresAt <= System.currentTimeMillis()) {
                values.remove(key);
                expiryAt.remove(key);
            }
        }
    }

    static class FakeRedisPipeline {
        private final FakeRedisClient client;
        private final List<Runnable> operations = new java.util.ArrayList<>();

        FakeRedisPipeline(FakeRedisClient client) {
            this.client = client;
        }

        public FakeRedisPipeline expire(String key, int ttlSeconds) {
            operations.add(() -> client.expire(key, ttlSeconds));
            return this;
        }

        public List<Object> execute() {
            operations.forEach(Runnable::run);
            operations.clear();
            return List.of();
        }
    }
}
