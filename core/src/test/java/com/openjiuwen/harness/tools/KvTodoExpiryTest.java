/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import static org.junit.jupiter.api.Assertions.*;

import com.openjiuwen.core.foundation.store.kv.InMemoryKVStore;
import com.openjiuwen.extensions.store.kv.RedisStore;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class KvTodoExpiryTest {
    @Test
    void todoUsesAtomicExpiryAndRefreshesOnlyPresentValues() throws Exception {
        Client client = new Client();
        TodoStorage storage = new KvTodoStorageProvider().create(Map.of("sharedKvStore", new RedisStore(client),
                "ttl", Map.of("default_ttl", 0.5, "refresh_on_read", true)));
        assertTrue(storage.load("s").isEmpty());
        assertEquals(0, client.refreshes);
        storage.save("s", List.of(TodoItem.create("task")));
        assertEquals(30, client.seconds);
        assertEquals(1, client.atomicWrites);
        assertEquals(1, storage.load("s").size());
        assertEquals(1, client.refreshes);
        client.value = null; // server-side expiration
        assertTrue(storage.load("s").isEmpty());
        assertEquals(1, client.refreshes);
    }

    @Test
    void unsupportedAndInvalidPoliciesFailAtAssembly() {
        var provider = new KvTodoStorageProvider();
        for (double minutes : new double[] { 0, -1, Double.NaN, Double.POSITIVE_INFINITY }) {
            assertThrows(IllegalArgumentException.class, () -> provider
                    .create(Map.of("sharedKvStore", new InMemoryKVStore(), "ttl", Map.of("default_ttl", minutes))));
        }
        assertThrows(IllegalArgumentException.class, () -> provider
                .create(Map.of("sharedKvStore", new InMemoryKVStore(), "ttl", Map.of("default_ttl", 10))));
        assertThrows(IllegalArgumentException.class,
                () -> provider.create(Map.of("ttl", Map.of("refresh_on_read", true))));
    }

    @Test
    void ttlIsPerConsumerAndDisabledRefreshDoesNotTouchExpiry() throws Exception {
        Client client = new Client();
        RedisStore store = new RedisStore(client);
        var first = new KvTodoStorage(store, java.time.Duration.ofSeconds(30), false);
        var second = new KvTodoStorage(store, java.time.Duration.ofSeconds(90), true);
        first.save("first", List.of(TodoItem.create("first")));
        assertEquals(30, client.seconds);
        first.load("first");
        assertEquals(0, client.refreshes);
        second.save("second", List.of(TodoItem.create("second")));
        assertEquals(90, client.seconds);
        first.save("first", List.of(TodoItem.create("updated")));
        assertEquals(30, client.seconds);
    }

    @Test
    void unsupportedAtomicExpiryCannotFallBackToUnboundedSet() {
        class PlainClient {
            int writes;

            public void set(String key, Object value) {
                writes++;
            }
        }
        PlainClient client = new PlainClient();
        var storage = new KvTodoStorage(new RedisStore(client), java.time.Duration.ofSeconds(10), false);
        assertThrows(IllegalStateException.class, () -> storage.save("s", List.of(TodoItem.create("task"))));
        assertEquals(0, client.writes);
    }

    @Test
    void refreshFailureIsVisibleToCaller() throws Exception {
        Client client = new Client() {
            @Override
            public long expire(String key, long seconds) {
                throw new IllegalStateException("denied");
            }
        };
        var storage = new KvTodoStorage(new RedisStore(client), java.time.Duration.ofSeconds(10), true);
        storage.save("s", List.of(TodoItem.create("task")));
        assertThrows(IllegalStateException.class, () -> storage.load("s"));
    }

    public static class Client {
        String value;
        long seconds;
        int atomicWrites;
        int refreshes;

        public String setex(String key, long seconds, String value) {
            this.value = value;
            this.seconds = seconds;
            atomicWrites++;
            return "OK";
        }

        public Object get(String key) {
            return value;
        }

        public long expire(String key, long seconds) {
            refreshes++;
            return value == null ? 0 : 1;
        }
    }
}
