/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.offload.api;

import com.openjiuwen.core.context.offload.model.OffloadNamespace;
import com.openjiuwen.core.context.offload.model.OffloadPayload;
import com.openjiuwen.core.context.offload.model.OffloadRef;
import com.openjiuwen.core.context.offload.model.OffloadRequest;

import java.time.Duration;
import java.util.Optional;

/**
 * Facade of the Context Offload v2 pipeline: write-ahead publish against a
 * fallback store chain, five-step load validation, pinning and TTL control.
 *
 * <p>Policy processors ({@code MessageOffloader}, {@code ToolResultBudgetProcessor})
 * call {@link #save}; the ReloaderTool calls {@link #load}. Mirrors
 * 架构文档 §3.1 / §4.</p>
 *
 * @since 2026-01-01
 */
public interface OffloadService {
    /**
     * Saves the request through the write-ahead publish protocol:
     * gate lock → catalog reserve (PENDING) → {@code saveWithFallback} on
     * the store chain → commit (READY) or abort.
     *
     * <p>Never throws to the caller: when every backend fails the returned
     * Optional is empty and the caller keeps the original messages inline
     * (共享约定 13).</p>
     *
     * @param request the offload request carrying namespace, messages and metadata
     * @return the save receipt carrying the actual backend, or empty on
     * total store failure
     */
    Optional<OffloadRef> save(OffloadRequest request);

    /**
     * Loads a payload with the five-step validation: namespace ownership →
     * catalog status → backend-directed read → sha256 → touch.
     *
     * @param ns     namespace constructed from the server-side session
     *               context (never from model input)
     * @param handle offload handle requested by the model
     * @param type   expected offload type hint (diagnostics only)
     * @return the loaded payload
     * @throws OffloadException {@code OFFLOAD_MISSING} / {@code OFFLOAD_EXPIRED} /
     *                          {@code OFFLOAD_CORRUPT} / {@code OFFLOAD_GONE} /
     *                          {@code OFFLOAD_FORBIDDEN}
     */
    OffloadPayload load(OffloadNamespace ns, String handle, String type);

    /**
     * Deletes a published entry (idempotent).
     *
     * @param ns     the namespace owning the handle
     * @param handle the offload handle to delete
     * @return {@code true} when a READY entry was removed
     */
    boolean delete(OffloadNamespace ns, String handle);

    /**
     * Removes every entry owned by the namespace (session teardown).
     *
     * @param ns the namespace to purge
     */
    void deleteNamespace(OffloadNamespace ns);

    /**
     * Pins an entry so the Janitor must not evict or expire it before
     * {@code duration} elapses (compression protocol C5).
     *
     * @param handle   the offload handle to pin
     * @param duration how long the pin holds
     */
    void pin(String handle, Duration duration);

    /**
     * Removes the pin flag.
     *
     * @param handle the offload handle to unpin
     */
    void unpin(String handle);

    /**
     * Extends the entry expiry by {@code delta}.
     *
     * @param handle the offload handle to extend
     * @param delta  the duration to add to the current expiry
     */
    void extendTtl(String handle, Duration delta);

    /**
     * T21/C6: caps the entry's remaining lifetime at the configured orphan
     * TTL ({@code orphan_ttl_seconds}, default 1h). An entry that already
     * expires sooner — or is pinned — is left untouched, and a configured
     * TTL of {@code 0} disables the shortening entirely.
     *
     * <p>Callers: the compressor, after replacing a block with a summary
     * that no longer references the handle (unreferenced = orphan-suspect:
     * nothing can reload it, so storage should be reclaimed quickly).</p>
     *
     * @param handle the offload handle to cap
     * @throws OffloadException {@code OFFLOAD_MISSING} when the handle is
     *                          unknown
     */
    void capOrphanTtl(String handle);
}
