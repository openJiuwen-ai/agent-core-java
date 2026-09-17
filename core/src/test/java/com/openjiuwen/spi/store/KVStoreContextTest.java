/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.spi.store;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.Map;

class KVStoreContextTest {
    @Test
    void registersAliasesAndRejectsUnknownOrConflictingNames() {
        KVStoreContext context = new KVStoreContext();
        BaseKVStore store = new StubStore();
        context.register("default", store);
        context.register("todo", store);
        assertSame(store, context.resolve("default"));
        assertSame(store, context.resolve("todo"));
        assertThrows(IllegalArgumentException.class, () -> context.register("default", new StubStore()));
        assertThrows(IllegalArgumentException.class, () -> context.resolve("missing"));
    }

    @Test
    void concurrentCreationBorrowsOnlyOneResourceAndNeverReplacesIt() throws Exception {
        KVStoreContext context = new KVStoreContext();
        var creations = new java.util.concurrent.atomic.AtomicInteger();
        String type = "context-race-" + java.util.UUID.randomUUID();
        KVStoreFactory.register(type, new KVStoreProvider() {
            public String typeName() {
                return type;
            }

            public BaseKVStore create(Map<String, Object> conf) {
                creations.incrementAndGet();
                return new StubStore();
            }
        });
        var barrier = new java.util.concurrent.CyclicBarrier(8);
        var executor = java.util.concurrent.Executors.newFixedThreadPool(8);
        try {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<Boolean>>();
            for (int index = 0; index < 8; index++) {
                futures.add(executor.submit(() -> {
                    barrier.await(5, java.util.concurrent.TimeUnit.SECONDS);
                    try {
                        context.create("default", type, Map.of());
                        return true;
                    } catch (IllegalArgumentException duplicate) {
                        return false;
                    }
                }));
            }
            int successes = 0;
            for (var future : futures) {
                if (future.get(10, java.util.concurrent.TimeUnit.SECONDS)) {
                    successes++;
                }
            }
            assertEquals(1, successes);
            assertEquals(1, creations.get());
            assertNotNull(context.resolve("default"));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void failedCreationDoesNotReserveNameAndInvalidNamesNeverInvokeProvider() {
        KVStoreContext context = new KVStoreContext();
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        String type = "context-retry-" + java.util.UUID.randomUUID();
        KVStoreFactory.register(type, new KVStoreProvider() {
            public String typeName() {
                return type;
            }

            public BaseKVStore create(Map<String, Object> conf) {
                if (calls.incrementAndGet() == 1) {
                    throw new IllegalStateException("unavailable");
                }
                return new StubStore();
            }
        });
        assertThrows(IllegalArgumentException.class, () -> context.create(" ", type, Map.of()));
        assertEquals(0, calls.get());
        assertThrows(IllegalStateException.class, () -> context.create("default", type, Map.of()));
        assertFalse(context.contains("default"));
        assertSame(context.create("default", type, Map.of()), context.resolve("default"));
        assertEquals(2, calls.get());
    }

    private static final class StubStore extends BaseKVStore {
        public void set(String key, Object value) {
        }

        public boolean exclusiveSet(String key, Object value, Integer expiry) {
            return true;
        }

        public Object get(String key) {
            return null;
        }

        public boolean isExists(String key) {
            return false;
        }

        public void delete(String key) {
        }

        public Map<String, Object> getByPrefix(String prefix) {
            return Map.of();
        }

        public void deleteByPrefix(String prefix, Integer batchSize) {
        }

        public java.util.List<Object> mget(java.util.List<String> keys) {
            return java.util.List.of();
        }

        public int batchDelete(java.util.List<String> keys, Integer batchSize) {
            return 0;
        }

        public KVStorePipeline pipeline() {
            return null;
        }
    }
}
