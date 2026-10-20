/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.offload.store;

import com.openjiuwen.core.context.offload.api.OffloadErrorCode;
import com.openjiuwen.core.context.offload.api.OffloadException;
import com.openjiuwen.core.context.offload.api.OffloadStore;
import com.openjiuwen.core.context.offload.model.OffloadBackend;
import com.openjiuwen.core.context.offload.model.OffloadNamespace;
import com.openjiuwen.core.context.offload.model.OffloadPayload;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Ordered 1..N fallback chain over concrete stores (FILE → REDIS → MEMORY).
 *
 * <p>{@link #saveWithFallback} walks the chain in construction order and
 * returns the first success as a {@link ChainedSaveOutcome} carrying the
 * actual backend and its path/key; all-fail raises
 * {@code OffloadException(STORE_IO)} with the last failure as cause
 * (架构文档 §3.1, AC-2-06/07/08). {@link #storeOf} serves load paths that
 * target the backend recorded in the catalog — no blind chain probing.</p>
 *
 * @since 2026-01-01
 */
public class CompositeOffloadStore implements OffloadStore {
    private final LinkedHashMap<OffloadBackend, OffloadStore> chain;

    public CompositeOffloadStore(LinkedHashMap<OffloadBackend, OffloadStore> chain) {
        Objects.requireNonNull(chain, "chain must not be null");
        if (chain.isEmpty()) {
            throw new IllegalArgumentException("composite offload chain must not be empty");
        }
        chain.forEach((backend, store) -> {
            Objects.requireNonNull(store, "chain store must not be null");
            if (store.backend() != backend) {
                throw new IllegalArgumentException(
                        "chain key " + backend + " does not match store backend " + store.backend());
            }
        });
        this.chain = new LinkedHashMap<>(chain);
    }

    @Override
    public OffloadBackend backend() {
        return OffloadBackend.COMPOSITE;
    }

    /**
     * Interface-level save: delegates to {@link #saveWithFallback} and
     * discards the outcome (throws on total failure, per SPI contract).
     *
     * @param ns      sanitized namespace
     * @param handle  unique offload handle
     * @param payload payload to persist
     * @param ttl     time-to-live, may be {@code null}
     * @throws OffloadException when every backend failed
     */
    @Override
    public void save(OffloadNamespace ns, String handle, OffloadPayload payload, Duration ttl) throws OffloadException {
        saveWithFallback(ns, handle, payload, ttl);
    }

    /**
     * Interface-level load: probes the chain in order and returns the first
     * hit (the service layer prefers directed {@link #storeOf} reads; this
     * probing variant only serves generic SPI usage).
     *
     * @param ns     sanitized namespace
     * @param handle unique offload handle
     * @return the loaded payload
     * @throws OffloadException when the entry is missing in every backend
     */
    @Override
    public OffloadPayload load(OffloadNamespace ns, String handle) throws OffloadException {
        OffloadException lastFailure = null;
        for (OffloadStore store : chain.values()) {
            try {
                return store.load(ns, handle);
            } catch (OffloadException ex) {
                lastFailure = ex;
                if (ex.code() != OffloadErrorCode.OFFLOAD_MISSING) {
                    throw ex;
                }
            }
        }
        throw lastFailure != null
                ? lastFailure
                : new OffloadException(OffloadErrorCode.OFFLOAD_MISSING, "offload entry missing: " + handle);
    }

    /**
     * Interface-level delete: removes the entry from every store in the
     * chain (idempotent across backends).
     *
     * @param ns     sanitized namespace
     * @param handle unique offload handle
     * @return {@code true} when an entry existed and was removed
     */
    @Override
    public boolean delete(OffloadNamespace ns, String handle) {
        boolean hasRemoved = false;
        for (OffloadStore store : chain.values()) {
            hasRemoved |= store.delete(ns, handle);
        }
        return hasRemoved;
    }

    @Override
    public boolean exists(OffloadNamespace ns, String handle) {
        for (OffloadStore store : chain.values()) {
            if (store.exists(ns, handle)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Tries every store in order; the first success wins.
     *
     * @param ns      sanitized namespace
     * @param handle  unique offload handle
     * @param payload payload to persist
     * @param ttl     time-to-live, may be {@code null}
     * @return the save outcome carrying the concrete backend and path/key
     * @throws OffloadException {@code OFFLOAD_STORE_IO} when every backend
     *                          failed; the original last failure rides as cause
     */
    public ChainedSaveOutcome saveWithFallback(OffloadNamespace ns, String handle, OffloadPayload payload,
                                               Duration ttl) throws OffloadException {
        Objects.requireNonNull(ns, "ns must not be null");
        Objects.requireNonNull(handle, "handle must not be null");
        Objects.requireNonNull(payload, "payload must not be null");
        OffloadException lastFailure = null;
        for (Map.Entry<OffloadBackend, OffloadStore> entry : chain.entrySet()) {
            OffloadStore store = entry.getValue();
            try {
                store.save(ns, handle, payload, ttl);
                return new ChainedSaveOutcome(store.backend(), pathOrKeyOf(store, ns, handle));
            } catch (OffloadException ex) {
                lastFailure = ex;
            } catch (RuntimeException ex) {
                lastFailure = new OffloadException(OffloadErrorCode.OFFLOAD_STORE_IO,
                        "store " + store.backend() + " failed unexpectedly: " + ex.getMessage(), ex);
            }
        }
        throw new OffloadException(OffloadErrorCode.OFFLOAD_STORE_IO,
                "all offload backends failed for handle " + handle, lastFailure);
    }

    /**
     * Directed lookup for load paths (catalog records the committed backend).
     *
     * @param backend the backend to look up
     * @return the store for the given backend
     */
    public OffloadStore storeOf(OffloadBackend backend) {
        OffloadStore store = chain.get(backend);
        if (store == null) {
            throw new OffloadException(OffloadErrorCode.OFFLOAD_STORE_IO,
                    "no offload store configured for backend " + backend);
        }
        return store;
    }

    /**
     * Checks whether the chain has a store for the given backend.
     *
     * @param backend the backend to check
     * @return {@code true} when the chain contains a store for the backend
     */
    public boolean supports(OffloadBackend backend) {
        return chain.containsKey(backend);
    }

    private static String pathOrKeyOf(OffloadStore store, OffloadNamespace ns, String handle) {
        if (store instanceof FileOffloadStore fileStore) {
            return FileOffloadStore.resolveFilePath(fileStore.workspaceRoot(), ns, handle);
        }
        return ns.toRedisKey(handle);
    }

    /**
     * Save receipt: the concrete backend that accepted the payload plus the
     * file path (FILESYSTEM) or storage key (REDIS/MEMORY).
     */
    public record ChainedSaveOutcome(OffloadBackend backend, String pathOrKey) {
    }
}
