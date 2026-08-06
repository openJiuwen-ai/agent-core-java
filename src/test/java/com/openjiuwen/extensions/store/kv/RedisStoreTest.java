
package com.openjiuwen.extensions.store.kv;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openjiuwen.spi.store.KVStorePipeline;

import redis.clients.jedis.Connection;
import redis.clients.jedis.ConnectionPool;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;
import redis.clients.jedis.util.JedisClusterCRC16;

import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class RedisStoreTest {
    @Test
    void basicCrudAndPrefixOperationsWork() {
        RedisStore store = new RedisStore(new FakeRedisClient());

        byte[] bytes = new byte[]{1, 2, 3};
        store.set("user:1", "alice");
        store.set("user:2", bytes);
        store.set("other:1", "ignored");

        assertEquals("alice", store.get("user:1"));
        assertArrayEquals(bytes, (byte[]) store.get("user:2"));
        assertTrue(store.exists("user:1"));

        Map<String, Object> byPrefix = store.getByPrefix("user:");
        assertEquals(2, byPrefix.size());
        assertEquals("alice", byPrefix.get("user:1"));
        assertArrayEquals(bytes, (byte[]) byPrefix.get("user:2"));

        store.deleteByPrefix("user:", 1);
        assertFalse(store.exists("user:1"));
        assertFalse(store.exists("user:2"));
        assertTrue(store.exists("other:1"));
    }

    @Test
    void batchDeleteHandlesSingleAndVarargsDeleteOverloads() {
        RedisStore store = new RedisStore(new FakeRedisClient());
        store.set("key-1", "value-1");
        store.set("key-2", "value-2");

        assertEquals(2, store.batchDelete(List.of("key-1", "key-2"), null));
        assertFalse(store.exists("key-1"));
        assertFalse(store.exists("key-2"));
    }

    @Test
    void exclusiveSetMgetAndBatchDeleteFollowRedisSemantics() throws InterruptedException {
        RedisStore store = new RedisStore(new FakeRedisClient());

        assertTrue(store.exclusiveSet("lock", "first", 1));
        assertFalse(store.exclusiveSet("lock", "second", 1));

        Thread.sleep(1200L);

        assertTrue(store.exclusiveSet("lock", "second", 1));
        store.set("k1", "v1");
        store.set("k2", "v2");

        assertEquals(Arrays.asList("v1", null, "second"), store.mget(List.of("k1", "missing", "lock")));
        assertEquals(2, store.batchDelete(List.of("k1", "missing", "k2"), 1));
        assertNull(store.get("k1"));
        assertNull(store.get("k2"));
    }

    @Test
    void pipelineAndRefreshTtlUseTheStoreContract() throws InterruptedException {
        FakeRedisClient redisClient = new FakeRedisClient();
        RedisStore store = new RedisStore(redisClient);

        KVStorePipeline pipeline = store.pipeline();
        pipeline.set("pipe:ttl", "value", 1);
        pipeline.set("pipe:stable", "stable");
        pipeline.get("pipe:ttl");
        pipeline.exists("pipe:stable");

        List<Object> results = pipeline.execute();
        assertEquals(4, results.size());
        assertEquals("value", results.get(2));
        assertEquals(Boolean.TRUE, results.get(3));

        Thread.sleep(600L);
        store.refreshTtl(List.of("pipe:ttl"), 2);
        assertTrue(redisClient.lastPipeline.isClosed());
        Thread.sleep(700L);
        assertTrue(store.exists("pipe:ttl"));

        Thread.sleep(1600L);
        assertFalse(store.exists("pipe:ttl"));
    }

    @Test
    void clusterDetectionAndRefreshFailuresAreHandled() {
        assertTrue(new RedisStore(new FakeRedisClusterClient()).isCluster());
        assertFalse(new RedisStore(new FakeRedisClient()).isCluster());

        RedisStore failingStore = new RedisStore(new ExplodingRedisClient());
        failingStore.set("volatile", "value");
        assertDoesNotThrow(() -> failingStore.refreshTtl(List.of("volatile"), 5));
    }

    @Test
    void getPreservesBinaryValuesLikeJedisByteApi() {
        RedisStore store = new RedisStore(new JedisLikeBinaryClient());
        byte[] serialized = new byte[]{(byte) 0xAC, (byte) 0xED, 0, 5, 0x7B};

        store.set("blob-key", serialized);

        assertArrayEquals(serialized, (byte[]) store.get("blob-key"));
    }

    @Test
    void clusterScanAggregatesAllNodePagesAndDeduplicatesKeys() {
        JedisCluster cluster = mock(JedisCluster.class);
        ConnectionPool firstPool = mock(ConnectionPool.class);
        ConnectionPool secondPool = mock(ConnectionPool.class);
        Connection firstConnection = mock(Connection.class);
        Connection secondConnection = mock(Connection.class);
        when(firstPool.getResource()).thenReturn(firstConnection);
        when(secondPool.getResource()).thenReturn(secondConnection);
        Map<String, ConnectionPool> nodePools = new LinkedHashMap<>();
        nodePools.put("first", firstPool);
        nodePools.put("second", secondPool);
        when(cluster.getClusterNodes()).thenReturn(nodePools);
        when(cluster.get("session:first")).thenReturn("first-value");
        when(cluster.get("session:second")).thenReturn("second-value");
        when(cluster.get("session:third")).thenReturn("third-value");

        try (MockedConstruction<Jedis> nodes = mockConstruction(Jedis.class, (node, context) -> {
            Connection connection = (Connection) context.arguments().get(0);
            if (connection == firstConnection) {
                when(node.scan(eq("0"), any(ScanParams.class)))
                        .thenReturn(new ScanResult<>("17", List.of("session:first", "other:key")));
                when(node.scan(eq("17"), any(ScanParams.class)))
                        .thenReturn(new ScanResult<>("0", List.of("session:second")));
            } else {
                when(node.scan(eq("0"), any(ScanParams.class)))
                        .thenReturn(new ScanResult<>("0", List.of("session:second", "session:third")));
            }
        })) {
            RedisStore store = new RedisStore(cluster);

            Map<String, Object> result = store.getByPrefix("session:");

            assertEquals(Map.of(
                    "session:first", "first-value",
                    "session:second", "second-value",
                    "session:third", "third-value"), result);
            assertEquals(2, nodes.constructed().size());
            nodes.constructed().forEach(node -> verify(node).close());
        }
    }

    @Test
    void clusterBatchDeleteGroupsKeysByHashSlot() {
        JedisCluster cluster = mock(JedisCluster.class);
        List<List<String>> deleteBatches = new ArrayList<>();
        when(cluster.del(any(String[].class))).thenAnswer(invocation -> {
            List<String> batch = Arrays.stream(invocation.getArguments()).map(String.class::cast).toList();
            deleteBatches.add(batch);
            return (long) batch.size();
        });
        RedisStore store = new RedisStore(cluster);
        String firstKey = "{slot-a}:first";
        String secondKey = "{slot-a}:second";
        String thirdKey = "{slot-b}:third";
        assertNotEquals(JedisClusterCRC16.getSlot(firstKey), JedisClusterCRC16.getSlot(thirdKey));

        int deleted = store.batchDelete(List.of(firstKey, secondKey, thirdKey), null);

        assertEquals(3, deleted);
        assertEquals(List.of(List.of(firstKey, secondKey), List.of(thirdKey)), deleteBatches);
    }

    static class FakeRedisClient {
        private final Map<String, Object> values = new ConcurrentHashMap<>();
        private final Map<String, Long> expiryAt = new ConcurrentHashMap<>();
        private FakeRedisPipeline lastPipeline;

        public void set(String key, Object value) {
            cleanup(key);
            values.put(key, value);
            expiryAt.remove(key);
        }

        public void set(byte[] key, byte[] value) {
            set(new String(key, StandardCharsets.UTF_8), value);
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

        public byte[] get(byte[] key) {
            Object value = get(new String(key, StandardCharsets.UTF_8));
            return value instanceof byte[] bytes ? bytes : null;
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

        public long delete(String key) {
            return delete(new String[]{key});
        }

        public List<Object> mget(String... keys) {
            List<Object> valueList = new ArrayList<>(keys.length);
            for (String key : keys) {
                valueList.add(get(key));
            }
            return valueList;
        }

        public List<String> scanIter(String pattern) {
            String prefix = pattern.endsWith("*") ? pattern.substring(0, pattern.length() - 1) : pattern;
            List<String> keys = new ArrayList<>();
            for (String key : new ArrayList<>(values.keySet())) {
                cleanup(key);
                if (this.values.containsKey(key) && key.startsWith(prefix)) {
                    keys.add(key);
                }
            }
            keys.sort(String::compareTo);
            return keys;
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
            lastPipeline = new FakeRedisPipeline(this);
            return lastPipeline;
        }

        private void cleanup(String key) {
            Long expiresAt = expiryAt.get(key);
            if (expiresAt != null && expiresAt <= System.currentTimeMillis()) {
                values.remove(key);
                expiryAt.remove(key);
            }
        }
    }

    static final class FakeRedisClusterClient extends FakeRedisClient {
    }

    static class ExplodingRedisClient extends FakeRedisClient {
        @Override
        public FakeRedisPipeline pipeline() {
            return new ExplodingPipeline(this);
        }
    }

    static class FakeRedisPipeline {
        protected final FakeRedisClient client;
        private final List<Runnable> operations = new ArrayList<>();
        private boolean closed;

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

        public void close() {
            closed = true;
        }

        boolean isClosed() {
            return closed;
        }
    }

    static final class ExplodingPipeline extends FakeRedisPipeline {
        ExplodingPipeline(FakeRedisClient client) {
            super(client);
        }

        @Override
        public FakeRedisPipeline expire(String key, int ttlSeconds) {
            throw new IllegalStateException("boom");
        }
    }

    /**
     * Mimics Jedis: binary values are stored via {@code set(byte[], byte[])} and only
     * {@code get(byte[])} returns raw bytes; {@code get(String)} would UTF-8-decode.
     */
    static class JedisLikeBinaryClient {
        private final Map<String, byte[]> values = new ConcurrentHashMap<>();

        public String set(byte[] key, byte[] value) {
            values.put(new String(key, java.nio.charset.StandardCharsets.UTF_8), value);
            return "OK";
        }

        public byte[] get(byte[] key) {
            return values.get(new String(key, java.nio.charset.StandardCharsets.UTF_8));
        }

        public String get(String key) {
            byte[] value = values.get(key);
            if (value == null) {
                return null;
            }
            return new String(value, java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}
