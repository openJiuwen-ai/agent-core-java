/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.offload.api;

import com.openjiuwen.core.context.offload.model.OffloadBackend;
import com.openjiuwen.core.context.offload.model.OffloadNamespace;
import com.openjiuwen.core.context.offload.model.OffloadRecord;
import com.openjiuwen.core.context.offload.model.OffloadStatus;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory state machine tracking every offload handle (架构文档 §3.1).
 *
 * <p>Lifecycle: {@code reserve} registers a PENDING entry, {@code commit}
 * flips it to READY, {@code abort} rolls it back. PENDING entries are
 * invisible to load paths. Illegal transitions raise
 * {@code OffloadException(OFFLOAD_STATUS_INVALID)}.</p>
 *
 * @since 2026-01-01
 */
public interface OffloadCatalog {
    /**
     * Registers a PENDING record.
     *
     * @param pending the record to register (must be in PENDING status)
     * @return the registered record
     * @throws OffloadException {@code OFFLOAD_STATUS_INVALID} when the
     *                          handle already exists (duplicate reserve)
     */
    OffloadRecord reserve(OffloadRecord pending);

    /**
     * PENDING → READY, filling in the actual backend, path/key, byte size
     * and expiry.
     *
     * @param handle     the offload handle to commit
     * @param backend    the physical storage backend
     * @param pathOrKey  file path or Redis key
     * @param bytes      payload size in bytes
     * @param expiresAt  epoch-millis expiry, or {@code null} for no expiry
     */
    void commit(String handle, OffloadBackend backend, String pathOrKey, long bytes, Long expiresAt);

    /**
     * PENDING → removed (rollback after a total store failure).
     *
     * @param handle the offload handle to abort
     */
    void abort(String handle);

    /**
     * Finds a record only when it belongs to {@code ns}; returns empty for
     * foreign namespaces (never leaks other sessions' handles).
     *
     * @param ns     the namespace to match
     * @param handle the offload handle to look up
     * @return the matching record, or empty if not found or foreign namespace
     */
    Optional<OffloadRecord> find(OffloadNamespace ns, String handle);

    /**
     * Service-layer helper: looks a record up by handle alone, ignoring
     * namespace ownership. Callers must combine it with {@link #find} to
     * distinguish {@code OFFLOAD_FORBIDDEN} (exists elsewhere) from
     * {@code OFFLOAD_MISSING} (unknown).
     *
     * @param handle the offload handle to look up
     * @return the matching record, or empty if not found
     */
    Optional<OffloadRecord> findByHandle(String handle);

    /**
     * READY → EXPIRED/CORRUPT/MISSING.
     *
     * @param handle the offload handle to update
     * @param status the terminal status to apply
     * @return {@code true} when the transition was applied
     */
    boolean markStatus(String handle, OffloadStatus status);

    /**
     * Updates {@code lastAccessAt} to now (LRU bookkeeping).
     *
     * @param handle the offload handle to touch
     */
    void touch(String handle);

    /**
     * Sets or clears the pin flag (pinned entries survive Janitor eviction).
     *
     * @param handle   the offload handle to update
     * @param isPinned whether to pin or unpin the entry
     * @return {@code true} when the pin flag was changed
     */
    boolean setPinned(String handle, boolean isPinned);

    /**
     * Overwrites {@code expiresAt} with {@code newExpiresAtMs}.
     *
     * @param handle         the offload handle to update
     * @param newExpiresAtMs the new expiry in epoch-millis
     * @return {@code true} when the TTL was extended
     */
    boolean extendTtl(String handle, long newExpiresAtMs);

    /**
     * Lists up to {@code limit} entries whose expiry has passed, oldest
     * access first (Janitor scan input).
     *
     * @param now   the current epoch-millis
     * @param limit the maximum number of entries to return
     * @return a list of expired entries, oldest access first
     */
    List<OffloadRecord> listExpired(long now, int limit);

    /**
     * Lists every entry owned by the namespace.
     *
     * @param ns the namespace to match
     * @return a list of all entries in the namespace
     */
    List<OffloadRecord> listByNamespace(OffloadNamespace ns);

    /**
     * Returns total persisted bytes charged to the namespace quota.
     *
     * @param ns the namespace to match
     * @return total persisted bytes charged to the namespace quota
     */
    long usedBytes(OffloadNamespace ns);

    /**
     * Returns number of entries charged to the namespace quota.
     *
     * @param ns the namespace to match
     * @return number of entries charged to the namespace quota
     */
    int usedEntries(OffloadNamespace ns);

    /**
     * Evicts the least-recently-used unpinned entry of the namespace.
     *
     * @param ns the namespace to evict from
     * @return {@code true} when an entry was evicted
     */
    boolean evictLru(OffloadNamespace ns);

    /**
     * Removes a record regardless of status (service delete / Janitor).
     *
     * @param handle the offload handle to remove
     * @return {@code true} when the handle existed and was removed
     */
    boolean remove(String handle);

    /**
     * Point-in-time copy of all records, for state snapshots and tests.
     *
     * @return an unmodifiable map of handle → record
     */
    Map<String, OffloadRecord> snapshot();
}
