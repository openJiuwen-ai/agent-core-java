package com.openjiuwen.spi.store;

import com.openjiuwen.core.foundation.store.kv.InMemoryKVStore;
import com.openjiuwen.extensions.store.kv.RedisStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link BaseKVStore#close()} default method and overrides.
 *
 * @since 0.1.13
 */
class BaseKVStoreCloseTest {

    @Test
    @DisplayName("BaseKVStore.close() default is no-op for subclass that does not override")
    void test_baseKVStore_close_defaultNoOp() {
        BaseKVStore store = new NoOpKVStore();
        store.close();
        // Reaching here without exception means default no-op close works
        assertThat(store.isExists("any")).isFalse();
    }

    @Test
    @DisplayName("InMemoryKVStore.close() clears internal map; subsequent reads return null")
    void test_inMemoryKVStore_close_clearsInternalMap() {
        InMemoryKVStore store = new InMemoryKVStore();
        store.set("k1", "v1");
        store.set("k2", "v2");
        assertThat(store.get("k1")).isEqualTo("v1");
        assertThat(store.get("k2")).isEqualTo("v2");
        assertThat(store.isExists("k1")).isTrue();

        store.close();

        assertThat(store.get("k1")).isNull();
        assertThat(store.get("k2")).isNull();
        assertThat(store.isExists("k1")).isFalse();
        assertThat(store.isExists("k2")).isFalse();
    }

    @Test
    @DisplayName("InMemoryKVStore.close() is idempotent (safe to call multiple times)")
    void test_inMemoryKVStore_close_idempotent() {
        InMemoryKVStore store = new InMemoryKVStore();
        store.set("k", "v");

        store.close();
        store.close();
        store.close();

        assertThat(store.get("k")).isNull();
    }

    @Test
    @DisplayName("RedisStore.close() invokes close() on underlying client via reflection")
    void test_redisStore_close_invokesClientClose() {
        RecordingClient client = new RecordingClient();
        RedisStore store = new RedisStore(client);

        store.close();

        assertThat(client.closeCalled).isTrue();
    }

    @Test
    @DisplayName("RedisStore.close() does not throw when client lacks close/shutdown/disconnect method")
    void test_redisStore_close_clientWithoutCloseMethod_doesNotThrow() {
        Object clientWithoutClose = new Object();
        RedisStore store = new RedisStore(clientWithoutClose);

        store.close();
        // Reaching here without exception means close() is graceful on unsupported clients
    }

    @Test
    @DisplayName("RedisStore.close() is idempotent (safe to call multiple times)")
    void test_redisStore_close_idempotent() {
        RecordingClient client = new RecordingClient();
        RedisStore store = new RedisStore(client);

        store.close();
        store.close();
        store.close();

        // Multiple close() calls should not throw; client may record 1+ invocations
        assertThat(client.closeCalled).isTrue();
    }

    /**
     * Minimal BaseKVStore subclass that does not override close(),
     * used to verify the default no-op behavior.
     */
    private static class NoOpKVStore extends BaseKVStore {
        @Override
        public void set(String key, Object value) {
        }

        @Override
        public boolean exclusiveSet(String key, Object value, Integer expiry) {
            return false;
        }

        @Override
        public Object get(String key) {
            return null;
        }

        @Override
        public boolean isExists(String key) {
            return false;
        }

        @Override
        public void delete(String key) {
        }

        @Override
        public java.util.Map<String, Object> getByPrefix(String prefix) {
            return new java.util.LinkedHashMap<>();
        }

        @Override
        public void deleteByPrefix(String prefix, Integer batchSize) {
        }

        @Override
        public List<Object> mget(List<String> keys) {
            return new ArrayList<>();
        }

        @Override
        public int batchDelete(List<String> keys, Integer batchSize) {
            return 0;
        }

        @Override
        public KVStorePipeline pipeline() {
            return new KVStorePipeline(ops -> new ArrayList<>());
        }
    }

    /**
     * Minimal Redis client stub that records close() invocations.
     */
    private static class RecordingClient {
        boolean closeCalled = false;

        public void close() {
            closeCalled = true;
        }
    }
}
