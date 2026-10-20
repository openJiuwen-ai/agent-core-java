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
import com.openjiuwen.core.context.offload.api.OffloadStore;
import com.openjiuwen.core.context.offload.model.OffloadBackend;
import com.openjiuwen.core.context.offload.model.OffloadNamespace;
import com.openjiuwen.core.context.offload.model.OffloadPayload;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * {@link CompositeOffloadStore} unit tests: fallback-chain switching, the
 * all-fail exception, directed {@code storeOf} lookup and chain key
 * validation (架构文档 §2.3, AC-2-06/07/08).
 */
class CompositeOffloadStoreTest {
    private static final OffloadNamespace NS = OffloadNamespace.of("t1", "s1", "c1");
    private static final String HANDLE = "handle-1";

    private static OffloadPayload payload() {
        List<BaseMessage> messages = List.of(new UserMessage("composite payload"));
        return OffloadPayload.create(HANDLE, NS, messages, null);
    }

    /**
     * Chain keys are {@link OffloadBackend} values: at most one store per
     * backend, a duplicate silently overwrites the earlier entry. Tests
     * therefore pair distinct backends (FILESYSTEM + MEMORY).
     *
     * @param stores the stores to include in the chain, each with a distinct backend
     * @return a LinkedHashMap mapping each store's backend to the store
     */
    private static LinkedHashMap<OffloadBackend, OffloadStore> chainOf(OffloadStore... stores) {
        LinkedHashMap<OffloadBackend, OffloadStore> chain = new LinkedHashMap<>();
        for (OffloadStore store : stores) {
            chain.put(store.backend(), store);
        }
        return chain;
    }

    @Test
    void backendIsComposite() {
        CompositeOffloadStore composite = new CompositeOffloadStore(chainOf(new MemoryOffloadStore()));
        assertEquals(OffloadBackend.COMPOSITE, composite.backend());
    }

    @Test
    void firstBackendWinsOnSave(@TempDir Path tempDir) {
        MemoryOffloadStore first = new MemoryOffloadStore();
        FileOffloadStore second = new FileOffloadStore(tempDir);
        CompositeOffloadStore composite = new CompositeOffloadStore(chainOf(first, second));

        CompositeOffloadStore.ChainedSaveOutcome outcome =
                composite.saveWithFallback(NS, HANDLE, payload(), Duration.ofSeconds(30));

        assertEquals(OffloadBackend.MEMORY, outcome.backend());
        assertEquals(NS.toRedisKey(HANDLE), outcome.pathOrKey());
        assertTrue(first.exists(NS, HANDLE));
        assertFalse(second.exists(NS, HANDLE), "fallback must not be written when primary succeeds");
    }

    @Test
    void chainFallsThroughWhenPrimaryFails(@TempDir Path tempDir) throws IOException {
        // Occupy the filesystem workspace root with a regular file so that
        // createDirectories fails and the FILESYSTEM backend cannot persist.
        Path blockedRoot = tempDir.resolve("blocked-root");
        Files.writeString(blockedRoot, "occupies the path");
        FileOffloadStore failing = new FileOffloadStore(blockedRoot);
        MemoryOffloadStore fallback = new MemoryOffloadStore();
        CompositeOffloadStore composite = new CompositeOffloadStore(chainOf(failing, fallback));

        CompositeOffloadStore.ChainedSaveOutcome outcome =
                composite.saveWithFallback(NS, HANDLE, payload(), null);

        assertEquals(OffloadBackend.MEMORY, outcome.backend());
        assertTrue(fallback.exists(NS, HANDLE));
    }

    @Test
    void allBackendsFailingThrowsStoreIoWithLastCause() {
        OffloadStore failingFirst = new FailingStore(OffloadErrorCode.OFFLOAD_STORE_IO);
        OffloadStore failingSecond = new FailingStore(OffloadErrorCode.OFFLOAD_REDIS_DOWN);
        CompositeOffloadStore composite = new CompositeOffloadStore(chainOf(failingFirst, failingSecond));

        OffloadException ex = assertThrows(OffloadException.class,
                () -> composite.saveWithFallback(NS, HANDLE, payload(), null));

        assertEquals(OffloadErrorCode.OFFLOAD_STORE_IO, ex.code());
        assertTrue(ex.getCause() instanceof OffloadException, "last failure must ride as cause");
        assertEquals(OffloadErrorCode.OFFLOAD_REDIS_DOWN,
                ((OffloadException) ex.getCause()).code());
    }

    @Test
    void allBackendsFailingWrapsUnexpectedRuntimeException() {
        OffloadStore exploding = new OffloadStore() {
            @Override
            public OffloadBackend backend() {
                return OffloadBackend.MEMORY;
            }

            @Override
            public void save(OffloadNamespace ns, String handle, OffloadPayload payload, Duration ttl) {
                throw new IllegalStateException("unexpected explosion");
            }

            @Override
            public OffloadPayload load(OffloadNamespace ns, String handle) {
                throw new IllegalStateException("unexpected explosion");
            }

            @Override
            public boolean delete(OffloadNamespace ns, String handle) {
                return false;
            }

            @Override
            public boolean exists(OffloadNamespace ns, String handle) {
                return false;
            }
        };
        CompositeOffloadStore composite = new CompositeOffloadStore(chainOf(exploding));

        OffloadException ex = assertThrows(OffloadException.class,
                () -> composite.saveWithFallback(NS, HANDLE, payload(), null));
        assertEquals(OffloadErrorCode.OFFLOAD_STORE_IO, ex.code());
    }

    @Test
    void storeOfReturnsDirectedStore() {
        MemoryOffloadStore memory = new MemoryOffloadStore();
        CompositeOffloadStore composite = new CompositeOffloadStore(chainOf(memory));

        assertEquals(memory, composite.storeOf(OffloadBackend.MEMORY));
        assertTrue(composite.supports(OffloadBackend.MEMORY));
        assertFalse(composite.supports(OffloadBackend.REDIS));
        OffloadException ex = assertThrows(OffloadException.class,
                () -> composite.storeOf(OffloadBackend.REDIS));
        assertEquals(OffloadErrorCode.OFFLOAD_STORE_IO, ex.code());
    }

    @Test
    void probingLoadReturnsFirstAvailableCopy(@TempDir Path tempDir) {
        FileOffloadStore primary = new FileOffloadStore(tempDir);
        MemoryOffloadStore secondary = new MemoryOffloadStore();
        primary.save(NS, HANDLE, payload(), null);
        CompositeOffloadStore composite = new CompositeOffloadStore(chainOf(primary, secondary));

        OffloadPayload loaded = composite.load(NS, HANDLE);
        assertEquals(HANDLE, loaded.offloadHandle());
    }

    @Test
    void probingLoadFallsThroughMissingToSecondBackend(@TempDir Path tempDir) {
        FileOffloadStore primary = new FileOffloadStore(tempDir);
        MemoryOffloadStore secondary = new MemoryOffloadStore();
        secondary.save(NS, HANDLE, payload(), null);
        CompositeOffloadStore composite = new CompositeOffloadStore(chainOf(primary, secondary));

        OffloadPayload loaded = composite.load(NS, HANDLE);
        assertEquals(HANDLE, loaded.offloadHandle());
    }

    @Test
    void probingLoadAllMissingThrowsMissing(@TempDir Path tempDir) {
        CompositeOffloadStore composite = new CompositeOffloadStore(chainOf(
                new FileOffloadStore(tempDir), new MemoryOffloadStore()));
        OffloadException ex = assertThrows(OffloadException.class, () -> composite.load(NS, HANDLE));
        assertEquals(OffloadErrorCode.OFFLOAD_MISSING, ex.code());
    }

    @Test
    void deleteRemovesFromEveryBackend(@TempDir Path tempDir) {
        MemoryOffloadStore primary = new MemoryOffloadStore();
        FileOffloadStore secondary = new FileOffloadStore(tempDir);
        primary.save(NS, HANDLE, payload(), null);
        secondary.save(NS, HANDLE, payload(), null);
        CompositeOffloadStore composite = new CompositeOffloadStore(chainOf(primary, secondary));

        assertTrue(composite.delete(NS, HANDLE));
        assertFalse(primary.exists(NS, HANDLE));
        assertFalse(secondary.exists(NS, HANDLE));
    }

    @Test
    void chainKeyMismatchIsRejected() {
        LinkedHashMap<OffloadBackend, OffloadStore> bad = new LinkedHashMap<>();
        bad.put(OffloadBackend.REDIS, new MemoryOffloadStore());
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new CompositeOffloadStore(bad));
        assertTrue(ex.getMessage().contains("does not match"));
    }

    @Test
    void emptyChainIsRejected() {
        LinkedHashMap<OffloadBackend, OffloadStore> empty = new LinkedHashMap<>();
        assertThrows(IllegalArgumentException.class, () -> new CompositeOffloadStore(empty));
    }

    /** Stub store failing on save with a fixed error code. */
    private static final class FailingStore implements OffloadStore {
        private final OffloadErrorCode code;

        private FailingStore(OffloadErrorCode code) {
            this.code = code;
        }

        @Override
        public OffloadBackend backend() {
            // two distinct concrete backends so the chain key check passes
            return code == OffloadErrorCode.OFFLOAD_STORE_IO
                    ? OffloadBackend.FILESYSTEM : OffloadBackend.REDIS;
        }

        @Override
        public void save(OffloadNamespace ns, String handle, OffloadPayload payload, Duration ttl)
                throws OffloadException {
            throw new OffloadException(code, "failing store boom: " + code);
        }

        @Override
        public OffloadPayload load(OffloadNamespace ns, String handle) {
            throw new OffloadException(OffloadErrorCode.OFFLOAD_MISSING, "nothing stored");
        }

        @Override
        public boolean delete(OffloadNamespace ns, String handle) {
            return false;
        }

        @Override
        public boolean exists(OffloadNamespace ns, String handle) {
            return false;
        }
    }
}
