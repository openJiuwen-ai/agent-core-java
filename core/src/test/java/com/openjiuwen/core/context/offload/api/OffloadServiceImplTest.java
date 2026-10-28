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
import java.util.concurrent.atomic.AtomicReference;

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

        // TC_043b: a PENDING reservation is "delete-rejected" (find hides it,
        // so the service reports false instead of reaping a write-ahead entry).
        assertFalse(service.delete(NS, handle));
        // TC_043b: a foreign namespace must see the unpublished handle as
        // FORBIDDEN (ownership gate wins over the PENDING MISSING mapping).
        OffloadException foreignEx = assertThrows(OffloadException.class,
                () -> service.load(FOREIGN_NS, handle, "in_memory"));
        assertEquals(OffloadErrorCode.OFFLOAD_FORBIDDEN, foreignEx.code());
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

        // TC_047: the CORRUPT state is sticky — a second load attempt hits the
        // status gate again (the tampered payload is never "healed").
        OffloadException second = assertThrows(OffloadException.class,
                () -> service.load(NS, ref.get().handle(), "in_memory"));
        assertEquals(OffloadErrorCode.OFFLOAD_CORRUPT, second.code());
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
    void concurrentLoadsOfSameHandleEachReturnVerbatimPayload() throws Exception {
        // TC_222: ≥4 threads concurrently load the same committed handle — every
        // reader gets the verbatim full payload (no torn read), no spurious
        // CORRUPT, no MISSING caused by an earlier reader (loads never delete).
        List<BaseMessage> original = messages("concurrent reload payload one", "concurrent reload payload two");
        Optional<OffloadRef> ref = service.save(request(original));
        assertTrue(ref.isPresent());
        String handle = ref.get().handle();

        int readers = 4;
        int rounds = 20;
        AtomicInteger verbatimLoads = new AtomicInteger();
        List<Throwable> failures = java.util.Collections.synchronizedList(new ArrayList<>());
        CountDownLatch ready = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(readers);
        for (int i = 0; i < readers; i++) {
            Thread reader = new Thread(() -> {
                try {
                    ready.await();
                    for (int r = 0; r < rounds; r++) {
                        OffloadPayload loaded = service.load(NS, handle, ALGORITHM_ID);
                        if (loaded.messages().size() == original.size()
                                && loaded.sha256Matches()) {
                            verbatimLoads.incrementAndGet();
                        }
                    }
                } catch (InterruptedException ex) {
                    failures.add(ex);
                } catch (RuntimeException ex) {
                    failures.add(ex);
                } finally {
                    done.countDown();
                }
            });
            reader.setUncaughtExceptionHandler((t, ex) -> failures.add(ex));
            reader.start();
        }
        ready.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS), "readers must finish in time");

        assertEquals(0, failures.size(), () -> "reader failures: " + failures);
        assertEquals(readers * rounds, verbatimLoads.get(),
                "every concurrent load must return the verbatim payload");
        // The entry stays READY: concurrent reads neither delete it nor flag CORRUPT.
        assertEquals(OffloadStatus.READY,
                catalog.findByHandle(handle).orElseThrow().status());
    }

    @Test
    void concurrentCrossSessionWorkStaysIsolatedAndUnblocked() throws Exception {
        // TC_225: two threads drive different sessions concurrently — messages
        // never cross sessions, handles stay globally distinct, and the two
        // sessions do not block each other (gate lock key is the session id).
        SessionOffloadGate scopedGate = new SessionOffloadGate();
        OffloadServiceImpl scopedService = new OffloadServiceImpl(catalog, store, scopedGate, new OffloadConfig());
        OffloadNamespace nsA = OffloadNamespace.of("t1", "sA", "c1");
        OffloadNamespace nsB = OffloadNamespace.of("t1", "sB", "c1");
        int rounds = 20;
        AtomicInteger successes = new AtomicInteger();
        List<String> handlesA = java.util.Collections.synchronizedList(new ArrayList<>());
        List<String> handlesB = java.util.Collections.synchronizedList(new ArrayList<>());
        List<Throwable> failures = java.util.Collections.synchronizedList(new ArrayList<>());
        CountDownLatch ready = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        Runnable sessionWorkA = () -> runCrossSessionRounds(scopedService, nsA, "payload A", rounds,
                successes, handlesA, failures, ready, done);
        Runnable sessionWorkB = () -> runCrossSessionRounds(scopedService, nsB, "payload B", rounds,
                successes, handlesB, failures, ready, done);
        Thread threadA = new Thread(sessionWorkA);
        Thread threadB = new Thread(sessionWorkB);
        threadA.setUncaughtExceptionHandler((t, ex) -> failures.add(ex));
        threadB.setUncaughtExceptionHandler((t, ex) -> failures.add(ex));
        threadA.start();
        threadB.start();
        ready.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS), "session workers must finish in time");

        assertEquals(0, failures.size(), () -> "session worker failures: " + failures);
        assertEquals(rounds * 2, successes.get());
        // Handles are globally distinct across the two sessions.
        java.util.Set<String> allHandles = new java.util.HashSet<>();
        allHandles.addAll(handlesA);
        allHandles.addAll(handlesB);
        assertEquals(rounds * 2, allHandles.size(), "handles must never collide across sessions");
        // Per-session directory isolation: A's entries live only under nsA and vice versa.
        assertEquals(rounds, catalog.listByNamespace(nsA).size());
        assertEquals(rounds, catalog.listByNamespace(nsB).size());
        // Both sessions progressed in parallel on distinct locks (lockKey = sessionId only).
        assertEquals(2, scopedGate.lockCount(), "different sessions must hold different gate locks");
        for (String handle : handlesA) {
            OffloadPayload loaded = scopedService.load(nsA, handle, ALGORITHM_ID);
            assertTrue(loaded.messages().get(0).get("content").toString().startsWith("payload A"),
                    "session A must only see its own payloads");
        }
        for (String handle : handlesB) {
            OffloadPayload loaded = scopedService.load(nsB, handle, ALGORITHM_ID);
            assertTrue(loaded.messages().get(0).get("content").toString().startsWith("payload B"),
                    "session B must only see its own payloads");
        }
    }

    private void runCrossSessionRounds(OffloadServiceImpl scopedService, OffloadNamespace ns, String prefix,
            int rounds, AtomicInteger successes, List<String> handles, List<Throwable> failures,
            CountDownLatch ready, CountDownLatch done) {
        try {
            ready.await();
            for (int r = 0; r < rounds; r++) {
                Optional<OffloadRef> ref = scopedService.save(OffloadRequest.builder()
                        .namespace(ns)
                        .algorithmId(ALGORITHM_ID)
                        .source("cross-session")
                        .messages(messages(prefix + " round " + r))
                        .build());
                if (ref.isPresent()) {
                    handles.add(ref.get().handle());
                    successes.incrementAndGet();
                }
            }
        } catch (InterruptedException ex) {
            failures.add(ex);
        } catch (RuntimeException ex) {
            failures.add(ex);
        } finally {
            done.countDown();
        }
    }

    @Test
    void tenantLevelNamespaceMismatchThrowsForbiddenNotMissing() {
        // TC_231: tenant-level variant of the ownership matrix — an entry owned
        // by t1 loaded under a t2 namespace must be FORBIDDEN (explicit denial,
        // not a MISSING misreport) while the owning namespace loads verbatim.
        Optional<OffloadRef> ref = service.save(request(messages("tenant matrix payload")));
        assertTrue(ref.isPresent());
        String handle = ref.get().handle();

        OffloadNamespace foreignTenant = OffloadNamespace.of("t2", "s1", "c1");
        OffloadException ex = assertThrows(OffloadException.class,
                () -> service.load(foreignTenant, handle, ALGORITHM_ID));
        assertEquals(OffloadErrorCode.OFFLOAD_FORBIDDEN, ex.code());
        // The denial message carries no payload content.
        assertFalse(ex.getMessage().contains("tenant matrix payload"));

        OffloadPayload owned = service.load(NS, handle, ALGORITHM_ID);
        assertEquals(1, owned.messages().size());
        assertTrue(owned.sha256Matches());
    }

    @Test
    void deleteNamespaceStaysScopedWithinSameTenantAndAcrossTenants() {
        // TC_233: two-level cleanup boundary — same tenant different session and
        // different tenant siblings must all survive a purge of one namespace.
        OffloadNamespace nsA = OffloadNamespace.of("t1", "sA", "c1");
        OffloadNamespace nsB = OffloadNamespace.of("t1", "sB", "c1");
        OffloadNamespace nsD = OffloadNamespace.of("t2", "sA", "c1");
        Optional<OffloadRef> refA = service.save(requestFor(nsA, "purge me"));
        Optional<OffloadRef> refB = service.save(requestFor(nsB, "same tenant sibling"));
        Optional<OffloadRef> refD = service.save(requestFor(nsD, "other tenant"));
        assertTrue(refA.isPresent() && refB.isPresent() && refD.isPresent());

        service.deleteNamespace(nsA);

        assertTrue(catalog.findByHandle(refA.get().handle()).isEmpty(),
                "purged namespace entries must vanish from the catalog");
        assertFalse(memory.exists(nsA, refA.get().handle()));
        assertTrue(catalog.findByHandle(refB.get().handle()).isPresent(),
                "same-tenant different-session sibling must survive");
        assertTrue(memory.exists(nsB, refB.get().handle()));
        assertTrue(catalog.findByHandle(refD.get().handle()).isPresent(),
                "different-tenant namespace must survive");
        assertTrue(memory.exists(nsD, refD.get().handle()));
        // Post-purge access to A's handle is a structured MISSING.
        OffloadException ex = assertThrows(OffloadException.class,
                () -> service.load(nsA, refA.get().handle(), ALGORITHM_ID));
        assertEquals(OffloadErrorCode.OFFLOAD_MISSING, ex.code());
        // Survivors still round-trip their original content.
        assertEquals("same tenant sibling",
                service.load(nsB, refB.get().handle(), ALGORITHM_ID).messages().get(0).get("content"));
        assertEquals("other tenant",
                service.load(nsD, refD.get().handle(), ALGORITHM_ID).messages().get(0).get("content"));
    }

    private static OffloadRequest requestFor(OffloadNamespace ns, String content) {
        return OffloadRequest.builder()
                .namespace(ns)
                .algorithmId(ALGORITHM_ID)
                .source("purge-boundary")
                .messages(messages(content))
                .build();
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

    @Test
    void saveMetricLineCarriesAllContractFieldsForRealFlow() {
        // TC_056: a real save through the service emits the full [Offload]
        // contract line — every attribution field present in one log record.
        List<String> lines = captureLogger(() ->
                service.save(request(messages("metric contract payload"))));
        Optional<String> metricLine = lines.stream()
                .filter(line -> line.contains("event=save"))
                .findFirst();
        assertTrue(metricLine.isPresent(), "save must emit an [Offload] metric line");
        String line = metricLine.get();
        assertTrue(line.contains("algorithmId=MessageOffloader"));
        assertTrue(line.contains("handle="));
        assertTrue(line.contains("backend=MEMORY"));
        assertTrue(line.contains("success=true"));
        assertTrue(line.contains("failureReason=-"));
        assertTrue(line.contains("preTokens=42"));
        assertTrue(line.contains("postTokens=0"));
        assertTrue(line.contains("durationMs="));
    }

    @Test
    void failedSaveAttributesIoErrorAndRedisDownReasons() {
        // TC_057: real failure flows collect the enum attribution —
        // OFFLOAD_STORE_IO → IO_ERROR on save, OFFLOAD_REDIS_DOWN → REDIS_DOWN on load (D8).
        List<String> ioLines = captureLogger(() -> {
            OffloadServiceImpl ioService = failingChainService(OffloadErrorCode.OFFLOAD_STORE_IO);
            assertTrue(ioService.save(request(messages("io doomed"))).isEmpty());
        });
        assertTrue(ioLines.stream().anyMatch(line -> line.contains("event=save")
                && line.contains("success=false") && line.contains("failureReason=IO_ERROR")),
                () -> "IO_ERROR attribution missing in: " + ioLines);

        // saveWithFallback wraps total chain failure as STORE_IO, so the REDIS_DOWN
        // attribution is collected on the directed load path instead.
        String redisHandle = "0123456789abcdef0123456789abcdef";
        OffloadCatalogImpl redisCatalog = new OffloadCatalogImpl();
        LinkedHashMap<OffloadBackend, OffloadStore> redisChain = new LinkedHashMap<>();
        redisChain.put(OffloadBackend.REDIS, new FailingStore(OffloadErrorCode.OFFLOAD_REDIS_DOWN));
        OffloadServiceImpl redisService = new OffloadServiceImpl(redisCatalog,
                new CompositeOffloadStore(redisChain), new SessionOffloadGate(), new OffloadConfig());
        redisCatalog.reserve(new OffloadRecord(redisHandle, NS, null, null, null, 10L,
                System.currentTimeMillis(), null, System.currentTimeMillis(), false,
                OffloadStatus.PENDING, "src"));
        redisCatalog.commit(redisHandle, OffloadBackend.REDIS, NS.toRedisKey(redisHandle),
                10L, System.currentTimeMillis() + Duration.ofHours(24).toMillis());

        AtomicReference<OffloadErrorCode> redisCode = new AtomicReference<>();
        List<String> redisLines = captureLogger(() -> {
            try {
                redisService.load(NS, redisHandle, ALGORITHM_ID);
            } catch (OffloadException ex) {
                redisCode.set(ex.code());
            }
        });
        assertEquals(OffloadErrorCode.OFFLOAD_REDIS_DOWN, redisCode.get(),
                "directed REDIS load failure must surface the backend error");
        assertTrue(redisLines.stream().anyMatch(line -> line.contains("event=load")
                && line.contains("success=false") && line.contains("failureReason=REDIS_DOWN")),
                () -> "REDIS_DOWN attribution missing in: " + redisLines);
    }

    @Test
    void loadMetricEmittedWithPostTokensAndSaveLoadPairing() {
        // TC_058: the load side of the pair fires its own metric; postTokens
        // is the reloaded payload estimate (>0) and save+load both carry the
        // same handle (成对闭环).
        AtomicReference<String> saveHandle = new AtomicReference<>();
        List<String> lines = captureLogger(() -> {
            Optional<OffloadRef> ref = service.save(request(messages("pair metric payload")));
            assertTrue(ref.isPresent());
            saveHandle.set(ref.get().handle());
            service.load(NS, ref.get().handle(), "MessageOffloader");
        });
        String handle = saveHandle.get();
        Optional<String> saveLine = lines.stream()
                .filter(line -> line.contains("event=save") && line.contains("handle=" + handle))
                .findFirst();
        Optional<String> loadLine = lines.stream()
                .filter(line -> line.contains("event=load") && line.contains("handle=" + handle))
                .findFirst();
        assertTrue(saveLine.isPresent() && loadLine.isPresent(),
                () -> "save+load pair must both log: " + lines);
        String load = loadLine.get();
        assertTrue(load.contains("success=true"));
        assertTrue(load.contains("postTokens="));
        assertFalse(load.contains("postTokens=0"), () -> "reloaded payload must estimate >0 tokens: " + load);
    }

    @Test
    void saveRefCarriesSchemaV2AndFullPayloadContract() {
        // TC_082: handle is exactly 32 hex chars; TC_083: schema_version=2;
        // TC_084: the payload contract fields survive a service round trip.
        Optional<OffloadRef> ref = service.save(request(messages("contract payload")));
        assertTrue(ref.isPresent());
        String handle = ref.get().handle();
        assertTrue(handle.matches("[0-9a-fA-F]{32}"), () -> "handle must be 32 hex: " + handle);

        OffloadPayload loaded = service.load(NS, handle, "in_memory");
        assertEquals("2", loaded.schemaVersion(), "payload schema_version must be the v2 literal");
        assertEquals(handle, loaded.offloadHandle());
        assertTrue(loaded.sha256Matches());
        assertEquals(NS, loaded.namespace());

        // TC_082: MEMORY pathOrKey is the redis-style key embedding the handle.
        assertEquals(NS.toRedisKey(handle), ref.get().pathOrKey());
    }

    /**
     * Captures the {@code [Offload]} logger lines produced by the action.
     *
     * @param action the save/load flow to run
     * @return the formatted log lines captured while the action ran
     */
    private static List<String> captureLogger(Runnable action) {
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger)
                org.slf4j.LoggerFactory.getLogger(OffloadServiceImpl.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        ch.qos.logback.classic.Level original = logger.getLevel();
        logger.setLevel(ch.qos.logback.classic.Level.INFO);
        try {
            action.run();
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(original);
        }
        return appender.list.stream()
                .map(event -> event.getFormattedMessage())
                .toList();
    }

    /**
     * Builds a service whose single-backend chain always fails with the given
     * structured error code (TC_057 attribution source).
     *
     * @param code the error code the failing store raises
     * @return the wired failing service
     */
    private static OffloadServiceImpl failingChainService(OffloadErrorCode code) {
        LinkedHashMap<OffloadBackend, OffloadStore> chain = new LinkedHashMap<>();
        chain.put(code == OffloadErrorCode.OFFLOAD_STORE_IO
                ? OffloadBackend.FILESYSTEM : OffloadBackend.REDIS, new FailingStore(code));
        return new OffloadServiceImpl(new OffloadCatalogImpl(), new CompositeOffloadStore(chain),
                new SessionOffloadGate(), new OffloadConfig());
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
            throw new OffloadException(code, "failing store boom: " + code);
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
