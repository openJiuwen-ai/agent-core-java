
package com.openjiuwen.extensions.checkpointer.redis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.core.session.checkpointer.Checkpointer;
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
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
    void providerBuildsStandaloneClientFromUrlAndPropagatesTtl() throws Exception {
        RedisCheckpointer.Provider provider = new RedisCheckpointer.Provider();

        Checkpointer checkpointer = provider.create(Map.of("connection", Map.of("url", "redis://127.0.0.1:6379"), "ttl",
                Map.of("default_ttl", 5, "refresh_on_read", true)));

        RedisCheckpointer redisCheckpointer = assertInstanceOf(RedisCheckpointer.class, checkpointer);
        assertFalse(redisCheckpointer.getRedisStore().isCluster());
        redisCheckpointer.getRedisStore().set("session-2:workflow:key", "value");
        assertTrue(redisCheckpointer.sessionExists("session-2"));
        assertEquals(300, readField(redisCheckpointer.getAgentStorage(), "ttlSeconds"));
        assertEquals(Boolean.TRUE, readField(redisCheckpointer.getAgentStorage(), "refreshOnRead"));
    }

    @Test
    void clusterUrlsNormalizeAndFactoryUsesRedisProvider() {
        RedisConnectionConfig connection =
            RedisConnectionConfig.fromMap(Map.of("url", "redis+cluster://127.0.0.1:7000"));

        assertTrue(connection.isClusterMode());
        assertEquals("redis://127.0.0.1:7000", connection.getConnectionUrl());

        Checkpointer checkpointer =
            CheckpointerFactory.create("redis", Map.of("connection", Map.of("url", "redis://127.0.0.1:6379")));

        RedisCheckpointer redisCheckpointer = assertInstanceOf(RedisCheckpointer.class, checkpointer);
        assertNotNull(redisCheckpointer.graphStore());
    }

    @Test
    void invalidConfigRaisesHelpfulError() {
        RedisCheckpointer.Provider provider = new RedisCheckpointer.Provider();

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> provider.create(Map.of()));

        assertTrue(error.getMessage().contains("connection"));
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
