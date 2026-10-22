/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.offload.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.core.context.offload.cleanup.SessionOffloadGate;
import com.openjiuwen.core.context.offload.model.OffloadBackend;
import com.openjiuwen.core.context.offload.model.OffloadConfig;
import com.openjiuwen.core.context.offload.model.OffloadNamespace;
import com.openjiuwen.core.context.offload.model.OffloadPayload;
import com.openjiuwen.core.context.offload.model.OffloadRecord;
import com.openjiuwen.core.context.offload.model.OffloadRef;
import com.openjiuwen.core.context.offload.model.OffloadRequest;
import com.openjiuwen.core.context.offload.model.OffloadStatus;
import com.openjiuwen.core.context.offload.store.CompositeOffloadStore;
import com.openjiuwen.core.context.offload.store.MemoryOffloadStore;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@link OffloadServiceImpl} unit tests: write-ahead publish protocol,
 * PENDING invisibility, sha256 tamper detection, session-gate
 * serialization and error taxonomy (架构文档 §2.3, AC-2-02..10).
 */
class OffloadServiceImplTest {
    private static final OffloadNamespace NS = OffloadNamespace.of("t1", "s1", "c1");
    private static final OffloadNamespace FOREIGN_NS = OffloadNamespace.of("t1", "s2", "c1");
    private static final String ALGORITHM_ID = "MessageOffloader";

    private final OffloadCatalogImpl catalog = new OffloadCatalogImpl();
    private final MemoryOffloadStore memory = new MemoryOffloadStore();
    private final CompositeOffloadStore store = buildCompositeStore();
    private final SessionOffloadGate gate = new SessionOffloadGate();
    private final OffloadServiceImpl service = new OffloadServiceImpl(catalog, store, gate, new OffloadConfig());

    /**
     * The chain must wrap the very instance the tests poke directly
     * ({@code memory}); a fresh store inside the chain would leave direct
     * backing-entry manipulations (delete/tamper) invisible to the service.
     *
     * @return a composite store wrapping the shared memory store instance
     */
    private CompositeOffloadStore buildCompositeStore() {
        LinkedHashMap<OffloadBackend, OffloadStore> chain = new LinkedHashMap<>();
        chain.put(OffloadBackend.MEMORY, memory);
        return new CompositeOffloadStore(chain);
    }

    private static List<BaseMessage> messages(String... contents) {
        List<BaseMessage> list = new ArrayList<>();
        for (String content : contents) {
            list.add(new UserMessage(content));
        }
        return list;
    }

    private static OffloadRequest request(List<BaseMessage> messages) {
        return OffloadRequest.builder()
                .namespace(NS)
                .algorithmId(ALGORITHM_ID)
                .source("tool-call-1")
                .messages(messages)
                .preTokenCount(42L)
                .build();
    }

    @Test
    void saveCommitsReadyRecordAndReturnsRef() {
        Optional<OffloadRef> ref = service.save(request(messages("hello")));

        assertTrue(ref.isPresent());
        assertEquals("in_memory", ref.get().type());
        assertEquals(OffloadBackend.MEMORY, ref.get().backend());
        assertEquals(NS.toRedisKey(ref.get().handle()), ref.get().pathOrKey());

        OffloadRecord record = catalog.findByHandle(ref.get().handle()).orElseThrow();
        assertEquals(OffloadStatus.READY, record.status());
        assertEquals(OffloadBackend.MEMORY, record.backend());
        assertEquals("tool-call-1", record.source());
    }

    @Test
    void saveThenLoadRoundTripsOriginalMessages() {
        List<BaseMessage> original = messages("m1", "m2");
        Optional<OffloadRef> ref = service.save(request(original));
        assertTrue(ref.isPresent());

        OffloadPayload loaded = service.load(NS, ref.get().handle(), "in_memory");
        assertEquals(original.size(), loaded.messages().size());
        assertTrue(loaded.sha256Matches());
    }

    @Test
    void loadUnknownHandleThrowsMissing() {
        OffloadException ex = assertThrows(OffloadException.class,
                () -> service.load(NS, "no-such-handle", "in_memory"));
        assertEquals(OffloadErrorCode.OFFLOAD_MISSING, ex.code());
    }

    @Test
    void loadFromForeignNamespaceThrowsForbidden() {
        Optional<OffloadRef> ref = service.save(request(messages("secret")));
        assertTrue(ref.isPresent());

        OffloadException ex = assertThrows(OffloadException.class,
                () -> service.load(FOREIGN_NS, ref.get().handle(), "in_memory"));
        assertEquals(OffloadErrorCode.OFFLOAD_FORBIDDEN, ex.code());
    }

    @Test
    void pendingEntryIsInvisibleToLoad() {
        // A record that was reserved but never committed stays PENDING and
        // must be reported as OFFLOAD_MISSING (write-ahead publish, AC-2-04).
        String handle = "pending-handle";
        OffloadPayload payload = OffloadPayload.create(handle, NS, messages("p"), null);
        catalog.reserve(new OffloadRecord(handle, NS, null, null, payload.contentSha256(), 10L,
                System.currentTimeMillis(), null, System.currentTimeMillis(), false,
                OffloadStatus.PENDING, "src"));

        OffloadException ex = assertThrows(OffloadException.class,
                () -> service.load(NS, handle, "in_memory"));
        assertEquals(OffloadErrorCode.OFFLOAD_MISSING, ex.code());
    }

    @Test
    void vanishedBackingEntryMarksRecordMissingAndThrowsGone() {
        Optional<OffloadRef> ref = service.save(request(messages("gone soon")));
        assertTrue(ref.isPresent());
        // remove the backing entry behind the catalog's back
        memory.delete(NS, ref.get().handle());

        OffloadException ex = assertThrows(OffloadException.class,
                () -> service.load(NS, ref.get().handle(), "in_memory"));
        assertEquals(OffloadErrorCode.OFFLOAD_GONE, ex.code());
        assertEquals(OffloadStatus.MISSING,
                catalog.findByHandle(ref.get().handle()).orElseThrow().status());
    }

    @Test
    void tamperedPayloadMarksRecordCorruptAndThrowsCorrupt() {
        Optional<OffloadRef> ref = service.save(request(messages("payload to tamper")));
        assertTrue(ref.isPresent());
        OffloadPayload stored = memory.load(NS, ref.get().handle());

        // Replace the stored payload with one whose sha256 no longer matches.
        OffloadPayload tampered = new OffloadPayload(stored.schemaVersion(), stored.offloadHandle(),
                stored.namespace(), stored.createdAt(), stored.expiresAt(), stored.contentSha256(),
                List.of(new UserMessage("tampered content").modelDump()));
        memory.save(NS, ref.get().handle(), tampered, null);

        OffloadException ex = assertThrows(OffloadException.class,
                () -> service.load(NS, ref.get().handle(), "in_memory"));
        assertEquals(OffloadErrorCode.OFFLOAD_CORRUPT, ex.code());
        assertEquals(OffloadStatus.CORRUPT,
                catalog.findByHandle(ref.get().handle()).orElseThrow().status());
    }

    @Test
    void totalStoreFailureReturnsEmptyOptionalAndAborts() {
        LinkedHashMap<OffloadBackend, OffloadStore> failing = new LinkedHashMap<>();
        failing.put(OffloadBackend.FILESYSTEM, new FailingStore(OffloadErrorCode.OFFLOAD_STORE_IO));
        failing.put(OffloadBackend.REDIS, new FailingStore(OffloadErrorCode.OFFLOAD_REDIS_DOWN));
        CompositeOffloadStore failingStore = new CompositeOffloadStore(failing);
        OffloadServiceImpl failingService = new OffloadServiceImpl(catalog, failingStore, gate, new OffloadConfig());

        Optional<OffloadRef> ref = failingService.save(request(messages("doomed")));

        assertTrue(ref.isEmpty(), "all-backends-failed save must not throw (keep inline)");
        assertTrue(catalog.snapshot().isEmpty(), "aborted handle must not linger in the catalog");
    }

    @Test
    void expiredRecordLoadThrowsOffloadExpired() {
        Optional<OffloadRef> ref = service.save(request(messages("ttl test")));
        assertTrue(ref.isPresent());
        catalog.markStatus(ref.get().handle(), OffloadStatus.EXPIRED);

        OffloadException ex = assertThrows(OffloadException.class,
                () -> service.load(NS, ref.get().handle(), "in_memory"));
        assertEquals(OffloadErrorCode.OFFLOAD_EXPIRED, ex.code());
    }

    @Test
    void pinUnpinAndExtendTtlFlowThroughToCatalog() {
        Optional<OffloadRef> ref = service.save(request(messages("pinned payload")));
        assertTrue(ref.isPresent());
        String handle = ref.get().handle();

        service.pin(handle, Duration.ofMinutes(10));
        OffloadRecord pinned = catalog.findByHandle(handle).orElseThrow();
        assertTrue(pinned.isPinned());
        assertTrue(pinned.expiresAt() > System.currentTimeMillis());

        service.unpin(handle);
        assertFalse(catalog.findByHandle(handle).orElseThrow().isPinned());

        service.extendTtl(handle, Duration.ofHours(1));
        assertTrue(catalog.findByHandle(handle).orElseThrow().expiresAt()
                > System.currentTimeMillis() + Duration.ofMinutes(30).toMillis());
    }

    @Test
    void pinUnknownHandleThrowsMissing() {
        OffloadException ex = assertThrows(OffloadException.class,
                () -> service.pin("ghost", Duration.ofMinutes(1)));
        assertEquals(OffloadErrorCode.OFFLOAD_MISSING, ex.code());
    }

    @Test
    void capOrphanTtlShortensFarFutureExpiry() {
        // AC-C6-02: an orphan-suspect entry's TTL is capped at the configured
        // orphan window (default 1h) instead of waiting out the full 24h TTL.
        Optional<OffloadRef> ref = service.save(request(messages("soon orphan")));
        assertTrue(ref.isPresent());
        String handle = ref.get().handle();

        service.capOrphanTtl(handle);

        long expiry = catalog.findByHandle(handle).orElseThrow().expiresAt();
        long expectedUpperBound = System.currentTimeMillis() + Duration.ofHours(1).toMillis() + 1000L;
        long expectedLowerBound = System.currentTimeMillis() + Duration.ofMinutes(55).toMillis();
        assertTrue(expiry <= expectedUpperBound, "orphan expiry must be capped to ~1h: " + expiry);
        assertTrue(expiry >= expectedLowerBound, "orphan expiry must not be shortened twice: " + expiry);
    }

    @Test
    void capOrphanTtlNeverPostponesSoonerExpiry() {
        // 只缩短不延长：已临近过期的条目 cap 后必须保持原过期时间。
        Optional<OffloadRef> ref = service.save(request(messages("dying soon")));
        assertTrue(ref.isPresent());
        String handle = ref.get().handle();
        long soon = System.currentTimeMillis() + Duration.ofMinutes(5).toMillis();
        catalog.extendTtl(handle, soon);

        service.capOrphanTtl(handle);

        assertEquals(soon, catalog.findByHandle(handle).orElseThrow().expiresAt());
    }

    @Test
    void capOrphanTtlSkipsPinnedEntries() {
        // AC-C6-01 保护语义：pinned 条目（可能是压缩中的 pin，C5）不被缩 TTL。
        Optional<OffloadRef> ref = service.save(request(messages("pinned orphan")));
        assertTrue(ref.isPresent());
        String handle = ref.get().handle();
        service.pin(handle, Duration.ofMinutes(10));
        long pinnedExpiry = catalog.findByHandle(handle).orElseThrow().expiresAt();

        service.capOrphanTtl(handle);

        assertEquals(pinnedExpiry, catalog.findByHandle(handle).orElseThrow().expiresAt());
    }

    @Test
    void capOrphanTtlDisabledWhenOrphanTtlSecondsZero() {
        OffloadConfig disabled = new OffloadConfig();
        disabled.setOrphanTtlSeconds(0L);
        OffloadServiceImpl disabledService = new OffloadServiceImpl(catalog, store, gate, disabled);
        Optional<OffloadRef> ref = disabledService.save(request(messages("kept full ttl")));
        assertTrue(ref.isPresent());
        String handle = ref.get().handle();
        long before = catalog.findByHandle(handle).orElseThrow().expiresAt();

        disabledService.capOrphanTtl(handle);

        assertEquals(before, catalog.findByHandle(handle).orElseThrow().expiresAt());
    }

    @Test
    void capOrphanTtlUnknownHandleThrowsMissing() {
        OffloadException ex = assertThrows(OffloadException.class, () -> service.capOrphanTtl("ghost"));
        assertEquals(OffloadErrorCode.OFFLOAD_MISSING, ex.code());
    }

    @Test
    void failedSaveRollsBackPendingReservationOnRuntimeFailure() {
        // G.MET.01: long test method is acceptable for integration test
        // T21 补漏：外层 RuntimeException 路径也要 abort，PENDING 不滞留。
        AtomicInteger reserveCount = new AtomicInteger();
        OffloadCatalog countingCatalog = new OffloadCatalog() {
            @Override
            public OffloadRecord reserve(OffloadRecord pending) {
                reserveCount.incrementAndGet();
                throw new IllegalStateException("boom after reserve");
            }

            @Override
            public void commit(String handle, OffloadBackend backend, String pathOrKey, long bytes, Long expiresAt) {
            }

            @Override
            public void abort(String handle) {
                throw new UnsupportedOperationException("not expected in this test");
            }

            @Override
            public java.util.Optional<OffloadRecord> find(OffloadNamespace ns, String handle) {
                return java.util.Optional.empty();
            }

            @Override
            public java.util.Optional<OffloadRecord> findByHandle(String handle) {
                return java.util.Optional.empty();
            }

            @Override
            public boolean markStatus(String handle, OffloadStatus status) {
                return false;
            }

            @Override
            public void touch(String handle) {
            }

            @Override
            public boolean setPinned(String handle, boolean isPinned) {
                return false;
            }

            @Override
            public boolean extendTtl(String handle, long newExpiresAtMs) {
                return false;
            }

            @Override
            public java.util.List<OffloadRecord> listExpired(long now, int limit) {
                return java.util.List.of();
            }

            @Override
            public java.util.List<OffloadRecord> listByNamespace(OffloadNamespace ns) {
                return java.util.List.of();
            }

            @Override
            public long usedBytes(OffloadNamespace ns) {
                return 0L;
            }

            @Override
            public int usedEntries(OffloadNamespace ns) {
                return 0;
            }

            @Override
            public boolean evictLru(OffloadNamespace ns) {
                return false;
            }

            @Override
            public boolean remove(String handle) {
                return false;
            }

            @Override
            public java.util.Map<String, OffloadRecord> snapshot() {
                return java.util.Map.of();
            }
        };
        OffloadServiceImpl abortingService = new OffloadServiceImpl(countingCatalog, store, gate, new OffloadConfig());

        Optional<OffloadRef> ref = abortingService.save(request(messages("reserve boom")));

        assertEquals(1, reserveCount.get(), "reserve must have been attempted");
        assertTrue(ref.isEmpty(), "save never throws to the caller");
    }

    @Test
    void otelReporterIsNullWhenDisabledAndCreatedWhenEnabled() {
        // T19: default config keeps the P0 Slf4j-only path (empty reporter);
        // enableOtel=true attempts creation (may still degrade to empty when
        // no SDK is on the classpath — both are valid, the hot path must not
        // care).
        assertTrue(new OffloadServiceImpl(catalog, store, gate, new OffloadConfig()).otelReporter().isEmpty());

        OffloadConfig enabled = new OffloadConfig();
        enabled.setEnableOtel(true);
        OffloadServiceImpl otelService = new OffloadServiceImpl(catalog, store, gate, enabled);
        try {
            Optional<OffloadOtelReporter> reporter = otelService.otelReporter();
            if (reporter.isPresent()) {
                reporter.get().record("save", new OffloadMetric(ALGORITHM_ID, "h", true, null, 3L, 2L, 4L,
                        OffloadBackend.MEMORY));
                reporter.get().record(null, null);
            }
        } finally {
            otelService.otelReporter().ifPresent(OffloadOtelReporter::close);
        }
    }

    @Test
    void deleteRemovesRecordAndBackingEntry() {
        Optional<OffloadRef> ref = service.save(request(messages("delete me")));
        assertTrue(ref.isPresent());

        assertTrue(service.delete(NS, ref.get().handle()));
        assertFalse(memory.exists(NS, ref.get().handle()));
        assertTrue(catalog.findByHandle(ref.get().handle()).isEmpty());
        assertFalse(service.delete(NS, ref.get().handle()));
    }

    @Test
    void deleteNamespaceRemovesEverythingOwned() {
        Optional<OffloadRef> first = service.save(request(messages("one")));
        Optional<OffloadRef> second = service.save(request(messages("two")));
        assertTrue(first.isPresent() && second.isPresent());
        OffloadRequest foreignRequest = OffloadRequest.builder()
                .namespace(FOREIGN_NS)
                .messages(messages("other session"))
                .build();
        Optional<OffloadRef> foreign = service.save(foreignRequest);
        assertTrue(foreign.isPresent());

        service.deleteNamespace(NS);

        assertTrue(catalog.findByHandle(first.get().handle()).isEmpty());
        assertTrue(catalog.findByHandle(second.get().handle()).isEmpty());
        // foreign namespace untouched
        assertTrue(memory.exists(FOREIGN_NS, foreign.get().handle()));
        assertTrue(catalog.findByHandle(foreign.get().handle()).isPresent());
    }

    @Test
    void concurrentSavesSerializePerSessionViaGate() throws Exception {
        AtomicInteger successCount = new AtomicInteger();
        int workers = 4;
        int rounds = 25;
        CountDownLatch ready = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(workers);
        List<Thread> threads = new ArrayList<>();
        List<Throwable> failures = java.util.Collections.synchronizedList(new ArrayList<>());
        for (int i = 0; i < workers; i++) {
            // G.CON.12: bare Thread is acceptable for a short-lived unit test;
            // production must use a managed ExecutorService.
            Thread worker = new Thread(() -> {
                try {
                    ready.await();
                    for (int r = 0; r < rounds; r++) {
                        if (service.save(request(messages("concurrent " + r))).isPresent()) {
                            successCount.incrementAndGet();
                        }
                    }
                } catch (InterruptedException ex) {
                    // Interrupted while waiting on the ready latch
                    failures.add(ex);
                } catch (RuntimeException ex) {
                    // Error/OutOfMemoryError should propagate and fail the test
                    failures.add(ex);
                } finally {
                    done.countDown();
                }
            });
            // G.CON.08: install an UncaughtExceptionHandler so no failure is silently swallowed
            worker.setUncaughtExceptionHandler((t, ex) -> failures.add(ex));
            threads.add(worker);
            worker.start();
        }
        ready.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS), "workers must finish in time");

        assertEquals(0, failures.size(), () -> "worker failures: " + failures);
        assertEquals(workers * rounds, successCount.get());
    }

    @Test
    void metricLogLineCarriesAttributionFields() {
        String line = new OffloadMetric(ALGORITHM_ID, "h1", false, OffloadFailureReason.REDIS_DOWN.name(),
                10L, 0L, 5L, OffloadBackend.REDIS).toLogLine("save");
        assertTrue(line.contains("event=save"));
        assertTrue(line.contains("algorithmId=MessageOffloader"));
        assertTrue(line.contains("handle=h1"));
        assertTrue(line.contains("backend=REDIS"));
        assertTrue(line.contains("success=false"));
        assertTrue(line.contains("failureReason=REDIS_DOWN"));
        assertTrue(line.contains("preTokens=10"));
    }

    @Test
    void loadMetricEventNameIsConfigurable() {
        String line = new OffloadMetric(null, "h2", true, null, 0L, 0L, 1L, null).toLogLine("load");
        assertTrue(line.contains("event=load"));
        assertTrue(line.contains("algorithmId=-"));
        assertTrue(line.contains("backend=-"));
    }

    /** Stub store failing on every save with a fixed error code. */
    private static final class FailingStore implements OffloadStore {
        private final OffloadErrorCode code;

        private FailingStore(OffloadErrorCode code) {
            this.code = code;
        }

        @Override
        public OffloadBackend backend() {
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
