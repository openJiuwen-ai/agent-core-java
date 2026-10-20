/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.offload.cleanup;

import com.openjiuwen.core.context.offload.api.OffloadCatalog;
import com.openjiuwen.core.context.offload.api.OffloadException;
import com.openjiuwen.core.context.offload.api.OffloadService;
import com.openjiuwen.core.context.offload.api.OffloadStore;
import com.openjiuwen.core.context.offload.model.OffloadBackend;
import com.openjiuwen.core.context.offload.model.OffloadConfig;
import com.openjiuwen.core.context.offload.model.OffloadNamespace;
import com.openjiuwen.core.context.offload.model.OffloadRecord;
import com.openjiuwen.core.context.offload.model.OffloadStatus;
import com.openjiuwen.core.context.offload.store.CompositeOffloadStore;
import com.openjiuwen.core.context.offload.store.FileOffloadStore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Background reaper for the Context Offload v2 pipeline (架构文档 §3.1, P1).
 *
 * <p>Three scans run on a single daemon thread every
 * {@code janitor_interval_seconds}:</p>
 * <ol>
 *   <li><b>Expiry scan</b> — {@link OffloadCatalog#listExpired(long, int)}
 *       (oldest access first) → targeted backend delete + catalog remove.</li>
 *   <li><b>Quota scan</b> — per namespace, when {@code usedBytes} or
 *       {@code usedEntries} exceeds the configured session quota, evict
 *       least-recently-used unpinned entries until back under budget
 *       (pinned entries survive, 共享约定 8).</li>
 *   <li><b>Orphan scan</b> (T21/C6) — reclaims two orphan families the
 *       other scans cannot see: catalog entries stuck in PENDING (a save
 *       crashed before commit/abort; invisible to expiry and quota scans,
 *       which only consider READY) reaped after {@code orphan_ttl_seconds};
 *       and filesystem payloads with no catalog entry (leftovers from a
 *       process restart or a lost catalog) deleted when their mtime is
 *       older than the orphan TTL.</li>
 * </ol>
 *
 * <p>Deletion goes through {@link OffloadService#delete} when wired so the
 * session gate lock and the backend-aware store lookup are reused; without a
 * service the janitor deletes directly via the composite store. Backend
 * payloads are always deleted together with the catalog entry —
 * {@code catalog.evictLru} alone would orphan files/keys.</p>
 *
 * @since 2026-01-01
 */
public class OffloadJanitor implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(OffloadJanitor.class);

    /** Upper bound of entries removed per scan so one sweep stays bounded. */
    private static final int EXPIRED_BATCH_LIMIT = 500;

    /** Upper bound of evictions per namespace per scan (anti live-lock). */
    private static final int QUOTA_EVICTION_LIMIT = 100;

    /** Payload filename suffix ({@code {handle}.json}). */
    private static final String JSON_SUFFIX = ".json";

    /** Handles are 32 hex chars ({@code UUID.randomUUID()} stripped of dashes). */
    private static final Pattern HANDLE_PATTERN = Pattern.compile("[0-9a-fA-F]{32}");

    private final OffloadCatalog catalog;
    private final CompositeOffloadStore store;
    private final OffloadService service;
    private final OffloadConfig config;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private volatile ScheduledExecutorService scheduler;

    public OffloadJanitor(OffloadCatalog catalog, CompositeOffloadStore store, OffloadService service,
                          OffloadConfig config) {
        this.catalog = Objects.requireNonNull(catalog, "catalog must not be null");
        this.store = Objects.requireNonNull(store, "store must not be null");
        this.service = service;
        this.config = Objects.requireNonNull(config, "config must not be null");
    }

    /**
     * Starts the periodic scan. Idempotent: repeated calls are no-ops.
     */
    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        long intervalSeconds = Math.max(1L, config.getJanitorIntervalSeconds());
        // G.CON.12: a single-thread ScheduledThreadPoolExecutor keeps the
        // Janitor bounded; a plain new Thread() would bypass pool control.
        ScheduledThreadPoolExecutor newScheduler = new ScheduledThreadPoolExecutor(1,
                runnable -> {
                    Thread thread = new Thread(runnable, "offload-janitor");
                    thread.setDaemon(true);
                    thread.setUncaughtExceptionHandler((t, ex) ->
                            LOGGER.error("[OffloadJanitor] uncaught exception in thread {}", t.getName(), ex));
                    return thread;
                });
        newScheduler.setRemoveOnCancelPolicy(true);
        this.scheduler = newScheduler;
        newScheduler.scheduleWithFixedDelay(this::safeScan, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
        LOGGER.info("[OffloadJanitor] started, interval={}s", intervalSeconds);
    }

    /**
     * Runs one full scan synchronously (expiry + quota + orphans).
     * Exceptions are caught and logged so the scheduler thread never dies.
     */
    public void scanNow() {
        long now = System.currentTimeMillis();
        int expiredRemoved = scanExpired(now);
        int quotaEvicted = scanQuotas();
        int orphansRemoved = scanOrphans(now);
        if (expiredRemoved > 0 || quotaEvicted > 0 || orphansRemoved > 0) {
            LOGGER.info("[OffloadJanitor] scan done: expiredRemoved={}, quotaEvicted={}, orphansRemoved={}",
                    expiredRemoved, quotaEvicted, orphansRemoved);
        }
    }

    /**
     * Stops the scheduler. Idempotent; safe to call from a finalizer-style
     * cleanup path.
     */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        ScheduledExecutorService current = scheduler;
        if (current == null) {
            return;
        }
        current.shutdown();
        try {
            if (!current.awaitTermination(5L, TimeUnit.SECONDS)) {
                current.shutdownNow();
            }
        } catch (InterruptedException ex) {
            // G.CON.10: shutdown path only — proceed to shutdownNow() without
            // restoring the interrupt flag; the Janitor is being torn down so
            // no further work runs on this thread.
            current.shutdownNow();
        }
        LOGGER.info("[OffloadJanitor] stopped");
    }

    private void safeScan() {
        try {
            scanNow();
        } catch (RuntimeException ex) {
            // Intentional: Janitor must survive any exception to keep cleaning other entries
            // scheduleWithFixedDelay cancels the task on any uncaught throwable.
            LOGGER.warn("[OffloadJanitor] scan failed: {}", ex.getMessage(), ex);
        }
    }

    private int scanExpired(long now) {
        List<OffloadRecord> expired = catalog.listExpired(now, EXPIRED_BATCH_LIMIT);
        int removed = 0;
        for (OffloadRecord record : expired) {
            if (deleteRecord(record)) {
                removed++;
            }
        }
        return removed;
    }

    private int scanQuotas() {
        long maxBytes = config.getMaxBytesPerSession();
        int maxEntries = config.getMaxEntriesPerSession();
        if (maxBytes <= 0L && maxEntries <= 0) {
            return 0;
        }
        Map<OffloadNamespace, List<OffloadRecord>> byNamespace = catalog.snapshot().values().stream()
                .filter(record -> record.namespace() != null)
                .collect(Collectors.groupingBy(OffloadRecord::namespace, LinkedHashMap::new, Collectors.toList()));
        int evicted = 0;
        for (OffloadNamespace namespace : byNamespace.keySet()) {
            evicted += evictNamespaceOverQuota(namespace, maxBytes, maxEntries);
        }
        return evicted;
    }

    /**
     * T21/C6: reclaims orphan families invisible to the other scans.
     *
     * <p>Family 1 — stuck PENDING catalog entries: {@code listExpired} and
     * the quota scan only consider READY records, so a save that crashed
     * between reserve and commit/abort would linger forever. Reaped when
     * {@code createdAt + orphan_ttl_seconds} has passed (the normal
     * write-ahead window is milliseconds; an hour means the process died
     * mid-save). The backend payload of a PENDING entry may exist (published
     * before the crash) and is deleted together with the entry.</p>
     *
     * <p>Family 2 — filesystem payloads without a catalog entry: a process
     * restart loses the in-memory catalog while the {@code *.json} payloads
     * survive. Scans {@code {workspaceRoot}/context/**\/offload/*.json} and
     * deletes files whose mtime is older than the orphan TTL and whose
     * handle is unknown to the catalog. {@code *.tmp.*} and {@code *.lock}
     * siblings are left to the publisher's own cleanup (a live publisher
     * may still be mid-write) — only files older than the orphan TTL are
     * eligible anyway.</p>
     *
     * @param now current scan time in millis, used to compute the orphan TTL cutoff
     * @return number of orphan entries/files removed
     */
    private int scanOrphans(long now) {
        long orphanTtlSeconds = config.getOrphanTtlSeconds();
        if (orphanTtlSeconds <= 0L) {
            // 0 disables the orphan mechanism entirely (no TTL shortening,
            // no PENDING reaping, no file scan) — symmetric with
            // OffloadService#capOrphanTtl.
            return 0;
        }
        int removed = reapStalePendingEntries(now, orphanTtlSeconds);
        removed += deleteOrphanFiles(now, orphanTtlSeconds);
        return removed;
    }

    private int reapStalePendingEntries(long now, long orphanTtlSeconds) {
        long staleBefore = now - Duration.ofSeconds(orphanTtlSeconds).toMillis();
        List<OffloadRecord> stale = catalog.snapshot().values().stream()
                .filter(record -> record.status() == OffloadStatus.PENDING)
                .filter(record -> record.createdAt() <= staleBefore)
                .collect(Collectors.toList());
        int removed = 0;
        for (OffloadRecord record : stale) {
            OffloadNamespace namespace = record.namespace();
            String handle = record.handle();
            if (namespace == null) {
                // No namespace → no backend key can be derived; catalog only.
                catalog.remove(handle);
                removed++;
                continue;
            }
            // Best-effort payload delete: a PENDING record may or may not have
            // a published backend artifact.
            try {
                store.delete(namespace, handle);
            } catch (OffloadException ex) {
                // Intentional: Janitor must survive any exception to keep cleaning other entries
                LOGGER.warn("[OffloadJanitor] orphan payload delete failed for {} : {}",
                        handle, ex.getMessage());
            }
            catalog.remove(handle);
            removed++;
        }
        return removed;
    }

    private int deleteOrphanFiles(long now, long orphanTtlSeconds) {
        Optional<FileOffloadStore> fileStore = fileStoreOrNull();
        if (fileStore.isEmpty()) {
            return 0;
        }
        long olderThanMs = now - Duration.ofSeconds(orphanTtlSeconds).toMillis();
        Path offloadRoot = fileStore.get().workspaceRoot().resolve("context");
        if (!Files.isDirectory(offloadRoot)) {
            return 0;
        }
        Set<String> knownHandles = catalog.snapshot().keySet();
        int removed = 0;
        // Depth: tenant(1) / user(2) / {session}_context(3) / offload(4) / payload(5).
        try (Stream<Path> files = Files.walk(offloadRoot, 5)) {
            List<Path> candidates = files
                    .filter(path -> !Files.isDirectory(path))
                    .filter(path -> {
                        String name = path.getFileName().toString();
                        return name.endsWith(".json") && !name.contains(".tmp.") && !name.endsWith(".lock");
                    })
                    .collect(Collectors.toList());
            for (Path candidate : candidates) {
                try {
                    if (Files.getLastModifiedTime(candidate).toMillis() > olderThanMs) {
                        continue;
                    }
                    Optional<String> handle = handleOf(candidate.getFileName().toString());
                    if (handle.isEmpty() || knownHandles.contains(handle.get())) {
                        continue;
                    }
                    if (Files.deleteIfExists(candidate)) {
                        removed++;
                    }
                } catch (IOException ex) {
                    LOGGER.warn("[OffloadJanitor] orphan file scan skipped {}: {}", candidate, ex.getMessage());
                }
            }
        } catch (IOException ex) {
            LOGGER.warn("[OffloadJanitor] orphan file scan failed: {}", ex.getMessage());
        }
        if (removed > 0) {
            LOGGER.info("[OffloadJanitor] orphan file scan removed {} file(s)", removed);
        }
        return removed;
    }

    private Optional<FileOffloadStore> fileStoreOrNull() {
        // The composite chain exposes the FILE backend only when configured.
        try {
            OffloadStore resolved = this.store.storeOf(OffloadBackend.FILESYSTEM);
            return resolved instanceof FileOffloadStore fileStore ? Optional.of(fileStore) : Optional.empty();
        } catch (OffloadException ex) {
            // Intentional: Janitor must survive when the FILE backend is not configured
            return Optional.empty();
        }
    }

    /**
     * Extracts the handle from a payload filename: {@code {handle}.json}.
     * Returns empty for names that do not match (the legacy
     * {@code {algorithmId}_{handle}.json} layout is deliberately not
     * recognized — v2 stores key by handle alone).
     *
     * @param fileName the payload filename to parse, e.g. {@code {handle}.json}
     * @return the extracted 32-hex-char handle, or empty if the filename
     *         is not a valid v2 payload filename
     */
    private static Optional<String> handleOf(String fileName) {
        if (fileName.length() <= JSON_SUFFIX.length() || !fileName.endsWith(JSON_SUFFIX)) {
            // Not a payload file (no .json suffix or too short) — skip it during orphan scanning.
            return Optional.empty();
        }
        String handle = fileName.substring(0, fileName.length() - JSON_SUFFIX.length());
        // Handles are 32 lowercase hex chars (UUID stripped of dashes).
        // Returns empty when the name does not match the v2 handle format so the caller skips it.
        return HANDLE_PATTERN.matcher(handle).matches() ? Optional.of(handle) : Optional.empty();
    }

    private int evictNamespaceOverQuota(OffloadNamespace namespace, long maxBytes, int maxEntries) {
        int evicted = 0;
        while (evicted < QUOTA_EVICTION_LIMIT && isOverQuota(namespace, maxBytes, maxEntries)) {
            Optional<OffloadRecord> victim = lruUnpinnedVictim(namespace);
            if (victim.isEmpty()) {
                // Everything left is pinned; quota stays over until pins release.
                break;
            }
            if (!deleteRecord(victim.get())) {
                break;
            }
            evicted++;
        }
        return evicted;
    }

    private boolean isOverQuota(OffloadNamespace namespace, long maxBytes, int maxEntries) {
        if (maxBytes > 0L && catalog.usedBytes(namespace) > maxBytes) {
            return true;
        }
        return maxEntries > 0 && catalog.usedEntries(namespace) > maxEntries;
    }

    /**
     * Mirrors {@code OffloadCatalogImpl#evictLru} victim selection (READY,
     * unpinned, oldest access first) but keeps the handle so the backend
     * payload is deleted together with the catalog entry.
     *
     * @param namespace the namespace whose READY unpinned entries are considered
     * @return the least-recently-accessed unpinned READY record, or empty
     *         if no evictable candidate exists (everything left is pinned)
     */
    private Optional<OffloadRecord> lruUnpinnedVictim(OffloadNamespace namespace) {
        List<OffloadRecord> candidates = catalog.listByNamespace(namespace).stream()
                .filter(record -> record.status() == OffloadStatus.READY)
                .filter(record -> !record.isPinned())
                .collect(Collectors.toList());
        return candidates.stream()
                .min(Comparator.comparingLong(OffloadRecord::lastAccessAt));
    }

    /**
     * Deletes the backend payload and removes the catalog entry. Prefers the
     * service facade (session-gated); falls back to direct composite-store
     * deletion keyed by the recorded backend.
     *
     * @param record the catalog record to delete (backend payload + entry)
     * @return {@code true} if the catalog entry was removed; {@code false} if
     *         the backend delete failed and the entry was left in place
     */
    private boolean deleteRecord(OffloadRecord record) {
        OffloadNamespace namespace = record.namespace();
        String handle = record.handle();
        if (service != null) {
            return service.delete(namespace, handle);
        }
        try {
            store.storeOf(record.backend()).delete(namespace, handle);
        } catch (OffloadException ex) {
            LOGGER.warn("[OffloadJanitor] backend delete failed for {} : {}", handle, ex.code());
            return false;
        }
        return catalog.remove(handle);
    }
}
