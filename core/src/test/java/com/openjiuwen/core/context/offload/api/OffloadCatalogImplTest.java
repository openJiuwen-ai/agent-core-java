/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.offload.api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.core.context.offload.model.OffloadBackend;
import com.openjiuwen.core.context.offload.model.OffloadNamespace;
import com.openjiuwen.core.context.offload.model.OffloadRecord;
import com.openjiuwen.core.context.offload.model.OffloadStatus;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@link OffloadCatalogImpl} unit tests: legal and illegal state-machine
 * transitions, namespace-scoped find, pin/touch/TTL bookkeeping and the
 * Janitor scan inputs (架构文档 §2.3).
 */
class OffloadCatalogImplTest {
    private static final OffloadNamespace NS = OffloadNamespace.of("t1", "u1", "s1", "c1");
    private static final OffloadNamespace OTHER_NS = OffloadNamespace.of("t1", "u1", "s2", "c1");
    private static final String HANDLE = "handle-1";
    private static final String SHA = "deadbeef";

    private final OffloadCatalogImpl catalog = new OffloadCatalogImpl();

    private static OffloadRecord pendingRecord(String handle, OffloadNamespace ns) {
        return new OffloadRecord(handle, ns, null, null, SHA, 100L, 1000L, 2000L, 1000L,
                false, OffloadStatus.PENDING, "source-1");
    }

    private static OffloadRecord readyRecord(String handle, OffloadNamespace ns) {
        return new OffloadRecord(handle, ns, OffloadBackend.MEMORY, "offload:key", SHA, 100L,
                1000L, 2000L, 1000L, false, OffloadStatus.READY, "source-1");
    }

    @Test
    void reserveRegistersPendingRecord() {
        OffloadRecord reserved = catalog.reserve(pendingRecord(HANDLE, NS));

        assertEquals(OffloadStatus.PENDING, reserved.status());
        Optional<OffloadRecord> byHandle = catalog.findByHandle(HANDLE);
        assertTrue(byHandle.isPresent());
        assertEquals(OffloadStatus.PENDING, byHandle.get().status());
    }

    @Test
    void reserveRejectsNonPendingStatus() {
        OffloadRecord ready = new OffloadRecord(HANDLE, NS, OffloadBackend.MEMORY, "k", SHA, 1L, 1L, null, 1L,
                false, OffloadStatus.READY, "src");
        OffloadException ex = assertThrows(OffloadException.class, () -> catalog.reserve(ready));
        assertEquals(OffloadErrorCode.OFFLOAD_STATUS_INVALID, ex.code());
    }

    @Test
    void reserveRejectsDuplicateHandle() {
        catalog.reserve(pendingRecord(HANDLE, NS));
        OffloadException ex = assertThrows(OffloadException.class,
                () -> catalog.reserve(pendingRecord(HANDLE, NS)));
        assertEquals(OffloadErrorCode.OFFLOAD_STATUS_INVALID, ex.code());
    }

    @Test
    void commitFlipsPendingToReadyAndFillsStorageFields() {
        catalog.reserve(pendingRecord(HANDLE, NS));

        catalog.commit(HANDLE, OffloadBackend.FILESYSTEM, "/tmp/file.json", 777L, 9999L);

        Optional<OffloadRecord> record = catalog.findByHandle(HANDLE);
        assertTrue(record.isPresent());
        assertEquals(OffloadStatus.READY, record.get().status());
        assertEquals(OffloadBackend.FILESYSTEM, record.get().backend());
        assertEquals("/tmp/file.json", record.get().pathOrKey());
        assertEquals(777L, record.get().bytes());
        assertEquals(9999L, record.get().expiresAt());
    }

    @Test
    void commitKeepsOriginalExpiryWhenCommitValueIsNull() {
        catalog.reserve(pendingRecord(HANDLE, NS));
        catalog.commit(HANDLE, OffloadBackend.MEMORY, "offload:key", 1L, null);
        assertEquals(2000L, catalog.findByHandle(HANDLE).orElseThrow().expiresAt());
    }

    @Test
    void commitUnknownHandleFails() {
        OffloadException ex = assertThrows(OffloadException.class,
                () -> catalog.commit(HANDLE, OffloadBackend.MEMORY, "k", 1L, null));
        assertEquals(OffloadErrorCode.OFFLOAD_STATUS_INVALID, ex.code());
    }

    @Test
    void commitTwiceIsIllegal() {
        catalog.reserve(pendingRecord(HANDLE, NS));
        catalog.commit(HANDLE, OffloadBackend.MEMORY, "k", 1L, null);
        OffloadException ex = assertThrows(OffloadException.class,
                () -> catalog.commit(HANDLE, OffloadBackend.MEMORY, "k2", 2L, null));
        assertEquals(OffloadErrorCode.OFFLOAD_STATUS_INVALID, ex.code());
    }

    @Test
    void abortRemovesPendingEntryOnly() {
        catalog.reserve(pendingRecord(HANDLE, NS));
        catalog.abort(HANDLE);
        assertTrue(catalog.findByHandle(HANDLE).isEmpty(), "aborted entry must vanish");

        // abort on a READY entry must not remove it (rollback is PENDING-only)
        catalog.reserve(pendingRecord(HANDLE, NS));
        catalog.commit(HANDLE, OffloadBackend.MEMORY, "k", 1L, null);
        catalog.abort(HANDLE);
        assertTrue(catalog.findByHandle(HANDLE).isPresent(), "abort must not remove READY entries");
    }

    @Test
    void findHidesPendingFromNamespaceLookup() {
        catalog.reserve(pendingRecord(HANDLE, NS));
        assertTrue(catalog.find(NS, HANDLE).isEmpty(), "PENDING must be invisible to load paths");
        catalog.commit(HANDLE, OffloadBackend.MEMORY, "k", 1L, null);
        assertTrue(catalog.find(NS, HANDLE).isPresent());
    }

    @Test
    void findReturnsEmptyForForeignNamespace() {
        catalog.reserve(pendingRecord(HANDLE, NS));
        catalog.commit(HANDLE, OffloadBackend.MEMORY, "k", 1L, null);
        assertTrue(catalog.find(OTHER_NS, HANDLE).isEmpty(), "cross-namespace reads must not leak");
    }

    @Test
    void markStatusAppliesOneWayTransitionsFromReady() {
        catalog.reserve(pendingRecord(HANDLE, NS));
        catalog.commit(HANDLE, OffloadBackend.MEMORY, "k", 1L, null);

        assertTrue(catalog.markStatus(HANDLE, OffloadStatus.EXPIRED));
        assertEquals(OffloadStatus.EXPIRED, catalog.findByHandle(HANDLE).orElseThrow().status());
    }

    @Test
    void markStatusRejectsPendingAndReadyTargets() {
        catalog.reserve(pendingRecord(HANDLE, NS));
        catalog.commit(HANDLE, OffloadBackend.MEMORY, "k", 1L, null);

        assertFalse(catalog.markStatus(HANDLE, OffloadStatus.PENDING));
        assertFalse(catalog.markStatus(HANDLE, OffloadStatus.READY));
        // still READY here: moving to a terminal state is the legal one-way transition
        assertTrue(catalog.markStatus(HANDLE, OffloadStatus.CORRUPT));
        // terminal → terminal is refused (one-way transitions only)
        assertFalse(catalog.markStatus(HANDLE, OffloadStatus.EXPIRED));
    }

    @Test
    void markStatusUnknownHandleReturnsFalse() {
        assertFalse(catalog.markStatus("nope", OffloadStatus.MISSING));
    }

    @Test
    void touchUpdatesLastAccessAt() {
        catalog.reserve(pendingRecord(HANDLE, NS));
        catalog.commit(HANDLE, OffloadBackend.MEMORY, "k", 1L, null);
        long before = catalog.findByHandle(HANDLE).orElseThrow().lastAccessAt();

        catalog.touch(HANDLE);

        long after = catalog.findByHandle(HANDLE).orElseThrow().lastAccessAt();
        assertTrue(after >= before);
    }

    @Test
    void touchUnknownHandleIsNoOp() {
        assertDoesNotThrow(() -> catalog.touch("ghost"));
    }

    @Test
    void setPinnedTogglesAndReports() {
        catalog.reserve(pendingRecord(HANDLE, NS));
        catalog.commit(HANDLE, OffloadBackend.MEMORY, "k", 1L, null);

        assertTrue(catalog.setPinned(HANDLE, true));
        assertTrue(catalog.findByHandle(HANDLE).orElseThrow().isPinned());
        assertTrue(catalog.setPinned(HANDLE, false));
        assertFalse(catalog.findByHandle(HANDLE).orElseThrow().isPinned());
    }

    @Test
    void setPinnedUnknownHandleReturnsFalse() {
        assertFalse(catalog.setPinned("ghost", true));
    }

    @Test
    void extendTtlOverwritesExpiry() {
        catalog.reserve(pendingRecord(HANDLE, NS));
        catalog.commit(HANDLE, OffloadBackend.MEMORY, "k", 1L, 2000L);

        assertTrue(catalog.extendTtl(HANDLE, 5000L));
        assertEquals(5000L, catalog.findByHandle(HANDLE).orElseThrow().expiresAt());
    }

    @Test
    void extendTtlUnknownHandleReturnsFalse() {
        assertFalse(catalog.extendTtl("ghost", 5000L));
    }

    @Test
    void listExpiredReturnsOldestAccessFirstWithLimit() throws Exception {
        OffloadRecord a = readyRecord("h-a", NS);
        OffloadRecord b = readyRecord("h-b", NS);
        catalog.reserve(new OffloadRecord(a.handle(), a.namespace(), null, null, a.contentSha256(), a.bytes(),
                a.createdAt(), 100L, 1L, false, OffloadStatus.PENDING, a.source()));
        catalog.commit("h-a", OffloadBackend.MEMORY, "k-a", a.bytes(), 100L);
        catalog.reserve(new OffloadRecord(b.handle(), b.namespace(), null, null, b.contentSha256(), b.bytes(),
                b.createdAt(), 100L, 2L, false, OffloadStatus.PENDING, b.source()));
        catalog.commit("h-b", OffloadBackend.MEMORY, "k-b", b.bytes(), 100L);
        Thread.sleep(2); // make sure now > expiresAt for both entries

        List<OffloadRecord> expired = catalog.listExpired(System.currentTimeMillis(), 10);
        assertEquals(2, expired.size());
        assertEquals("h-a", expired.get(0).handle()); // lastAccessAt 1 < 2

        List<OffloadRecord> limited = catalog.listExpired(System.currentTimeMillis(), 1);
        assertEquals(1, limited.size());
        assertEquals("h-a", limited.get(0).handle());
    }

    @Test
    void listExpiredIgnoresUnexpiredAndNonReadyEntries() throws Exception {
        // unexpired READY entry
        catalog.reserve(pendingRecord("h-live", NS));
        catalog.commit("h-live", OffloadBackend.MEMORY, "k", 1L, Long.MAX_VALUE);
        // expired but PENDING entry (never published)
        catalog.reserve(new OffloadRecord("h-pending", NS, null, null, SHA, 1L, 1L, 1L, 1L,
                false, OffloadStatus.PENDING, "src"));

        assertTrue(catalog.listExpired(System.currentTimeMillis(), 10).isEmpty());
    }

    @Test
    void usedBytesAndEntriesCountOnlyReadyEntriesOfNamespace() {
        catalog.reserve(pendingRecord("h-1", NS));
        catalog.commit("h-1", OffloadBackend.MEMORY, "k", 120L, null);
        catalog.reserve(pendingRecord("h-2", NS));
        catalog.commit("h-2", OffloadBackend.MEMORY, "k", 80L, null);
        // foreign namespace must not be charged
        catalog.reserve(pendingRecord("h-3", OTHER_NS));
        catalog.commit("h-3", OffloadBackend.MEMORY, "k", 999L, null);
        // PENDING must not be charged
        catalog.reserve(pendingRecord("h-4", NS));

        assertEquals(200L, catalog.usedBytes(NS));
        assertEquals(2, catalog.usedEntries(NS));
        assertEquals(999L, catalog.usedBytes(OTHER_NS));
    }

    @Test
    void evictLruRemovesOldestUnpinnedEntry() {
        catalog.reserve(pendingRecord("h-old", NS));
        catalog.commit("h-old", OffloadBackend.MEMORY, "k", 1L, null);
        catalog.reserve(pendingRecord("h-new", NS));
        catalog.commit("h-new", OffloadBackend.MEMORY, "k", 1L, null);
        // h-old has the older lastAccessAt (1000 < 1000+sleep? both same ms) → force distinct
        catalog.touch("h-new");
        try {
            Thread.sleep(2);
        } catch (InterruptedException ex) {
            // G.CON.10: test-only sleep; no interrupt restoration needed
        }
        catalog.touch("h-new");

        assertTrue(catalog.evictLru(NS));
        assertFalse(catalog.findByHandle("h-old").isPresent(), "oldest unpinned entry must be evicted");
        assertTrue(catalog.findByHandle("h-new").isPresent());
    }

    @Test
    void evictLruSkipsPinnedEntries() {
        catalog.reserve(pendingRecord("h-old", NS));
        catalog.commit("h-old", OffloadBackend.MEMORY, "k", 1L, null);
        catalog.reserve(pendingRecord("h-new", NS));
        catalog.commit("h-new", OffloadBackend.MEMORY, "k", 1L, null);
        catalog.setPinned("h-old", true);

        assertTrue(catalog.evictLru(NS));
        assertTrue(catalog.findByHandle("h-old").isPresent(), "pinned entries must survive");
        assertFalse(catalog.findByHandle("h-new").isPresent());
    }

    @Test
    void evictLruReturnsFalseOnEmptyNamespace() {
        assertFalse(catalog.evictLru(NS));
    }

    @Test
    void removeDeletesAnyStatusRecord() {
        catalog.reserve(pendingRecord(HANDLE, NS));
        assertTrue(catalog.remove(HANDLE));
        assertFalse(catalog.remove(HANDLE));
    }

    @Test
    void snapshotReturnsIndependentCopy() {
        catalog.reserve(pendingRecord(HANDLE, NS));
        Map<String, OffloadRecord> snapshot = catalog.snapshot();
        assertEquals(1, snapshot.size());
        catalog.abort(HANDLE);
        assertEquals(1, snapshot.size(), "snapshot must not be a live view");
    }

    @Test
    void namespaceNormalizationAppliesInFind() {
        catalog.reserve(pendingRecord(HANDLE, NS));
        catalog.commit(HANDLE, OffloadBackend.MEMORY, "k", 1L, null);
        // of() normalization (trim, null→"") makes equivalent spellings match
        assertTrue(catalog.find(OffloadNamespace.of(" t1 ", " u1 ", " s1 ", " c1 "), HANDLE).isPresent(),
                "namespace normalization must apply in find");
    }
}
