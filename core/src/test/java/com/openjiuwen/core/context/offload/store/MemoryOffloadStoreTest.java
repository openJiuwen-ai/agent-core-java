/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.offload.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.core.context.offload.api.OffloadErrorCode;
import com.openjiuwen.core.context.offload.api.OffloadException;
import com.openjiuwen.core.context.offload.model.OffloadBackend;
import com.openjiuwen.core.context.offload.model.OffloadNamespace;
import com.openjiuwen.core.context.offload.model.OffloadPayload;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * {@link MemoryOffloadStore} unit tests: round-trip semantics, key
 * derivation and concurrent thread safety (架构文档 §2.3).
 */
class MemoryOffloadStoreTest {
    private static final OffloadNamespace NS = OffloadNamespace.of("t1", "s1", "c1");
    private static final String HANDLE = "handle-1";

    private static OffloadPayload payload(String text) {
        List<BaseMessage> messages = List.of(new UserMessage(text));
        return OffloadPayload.create(HANDLE, NS, messages, null);
    }

    @Test
    void backendIsMemory() {
        assertEquals(OffloadBackend.MEMORY, new MemoryOffloadStore().backend());
    }

    @Test
    void saveThenLoadRoundTrips() {
        MemoryOffloadStore store = new MemoryOffloadStore();
        OffloadPayload payload = payload("hello");
        store.save(NS, HANDLE, payload, Duration.ofSeconds(30));

        OffloadPayload loaded = store.load(NS, HANDLE);
        assertEquals(payload, loaded);
        assertTrue(store.exists(NS, HANDLE));
    }

    @Test
    void loadMissingThrowsOffloadMissing() {
        MemoryOffloadStore store = new MemoryOffloadStore();
        OffloadException ex = assertThrows(OffloadException.class, () -> store.load(NS, HANDLE));
        assertEquals(OffloadErrorCode.OFFLOAD_MISSING, ex.code());
    }

    @Test
    void deleteRemovesEntryAndIsIdempotent() {
        MemoryOffloadStore store = new MemoryOffloadStore();
        store.save(NS, HANDLE, payload("x"), null);
        assertTrue(store.delete(NS, HANDLE));
        assertFalse(store.exists(NS, HANDLE));
        assertFalse(store.delete(NS, HANDLE));
    }

    @Test
    void keysAreNamespacedBySession() {
        MemoryOffloadStore store = new MemoryOffloadStore();
        OffloadPayload payload = payload("x");
        store.save(NS, HANDLE, payload, null);

        OffloadNamespace otherSession = OffloadNamespace.of("t1", "s2", "c1");
        assertFalse(store.exists(otherSession, HANDLE), "another session must not see this entry");
        OffloadException ex = assertThrows(OffloadException.class, () -> store.load(otherSession, HANDLE));
        assertEquals(OffloadErrorCode.OFFLOAD_MISSING, ex.code());
    }

    @Test
    void concurrentWritersDoNotCorruptEntries() throws Exception {
        MemoryOffloadStore store = new MemoryOffloadStore();
        int writers = 8;
        int writesPerWriter = 200;
        ExecutorService pool = Executors.newFixedThreadPool(writers); // G.CON.12: managed pool, shut down below
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int w = 0; w < writers; w++) {
            final int writerIndex = w;
            futures.add(pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < writesPerWriter; i++) {
                        OffloadNamespace ns = OffloadNamespace.of("t", "s" + writerIndex, "c");
                        String handle = "h" + i;
                        store.save(ns, handle, payload("w" + writerIndex + "-" + i), null);
                        store.load(ns, handle);
                        if (i % 50 == 0) {
                            store.delete(ns, handle);
                        }
                        // after a delete at i%50==0 the entry must be absent;
                        // otherwise it must round-trip cleanly.
                        if (i % 50 == 0) {
                            assertThrows(OffloadException.class, () -> store.load(ns, handle));
                        } else {
                            store.load(ns, handle);
                        }
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("writer " + writerIndex + " interrupted", ex);
                } catch (RuntimeException ex) {
                    // Rethrow so future.get() surfaces test failures
                    throw new IllegalStateException("writer " + writerIndex + " failed", ex);
                }
            }));
        }
        start.countDown();
        for (Future<?> future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }
        pool.shutdownNow();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
    }
}
