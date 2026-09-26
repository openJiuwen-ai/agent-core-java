/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.kvcache;

/**
 * Runtime contracts for KV cache scheduling: admission states, action kinds,
 * leases and the narrow runtime protocol used by Session and model hooks.
 *
 * <p>Mirrors Python's {@code Admission}/{@code ActionKind}/
 * {@code InferenceLease}/{@code KVCacheRuntimeProtocol} in
 * {@code openjiuwen/core/kv_cache/kv_cache_types.py}.</p>
 *
 * @since 0.1.16
 */
public final class KVCacheTypes {
    private KVCacheTypes() {
    }

    /** How open one scope is to new inference. */
    public enum Admission {
        /** Inference enters directly. */
        OPEN,
        /** Cache is not in place; inference prepares (auto prefetch) first. */
        BLOCKED,
        /** Permanently released; no more accounting. */
        TERMINAL
    }

    /** Management action kinds understood by the gateway. */
    public enum ActionKind {
        PREFETCH("prefetch"),
        OFFLOAD("offload"),
        EVICT("evict");

        private final String value;

        ActionKind(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    /** Per-inference accounting lease held on both child and root scopes. */
    public static final class InferenceLease {
        private final ActionKey childKey;
        private final ActionKey rootKey;
        private boolean isReleased;

        public InferenceLease(ActionKey childKey, ActionKey rootKey) {
            this.childKey = childKey;
            this.rootKey = rootKey;
        }

        public ActionKey childKey() {
            return childKey;
        }

        public ActionKey rootKey() {
            return rootKey;
        }

        public boolean isReleased() {
            return isReleased;
        }

        public void markReleased() {
            this.isReleased = true;
        }
    }

    /**
     * Narrow runtime contract used by Session and model-call integration.
     *
     * <p>Mirrors Python's {@code KVCacheRuntimeProtocol}.</p>
     */
    public interface KVCacheRuntimeProtocol {
        /**
         * Start best-effort cache preparation for one identity.
         *
         * @param identity lineage identity
         * @return whether any prefetch was issued or the cache was resident
         */
        java.util.concurrent.CompletableFuture<Boolean> prepare(KVCacheIdentity identity);

        /**
         * Start best-effort cache offload for one identity.
         *
         * @param identity lineage identity
         * @return whether an offload was scheduled
         */
        java.util.concurrent.CompletableFuture<Boolean> suspend(KVCacheIdentity identity);

        /**
         * Permanently release one identity's remote cache.
         *
         * @param identity lineage identity
         * @return whether any eviction succeeded
         */
        java.util.concurrent.CompletableFuture<Boolean> release(KVCacheIdentity identity);

        /**
         * Register one inference and acquire a lease on (child, root) scopes.
         *
         * @param identity lineage identity
         * @param model live model to bind
         * @param modelName optional model name override
         * @return lease, or {@code null} when accounting is not possible
         */
        java.util.concurrent.CompletableFuture<InferenceLease> beginInference(
                KVCacheIdentity identity, Object model, String modelName);

        /**
         * Release an inference lease and record residency on success.
         *
         * @param lease lease returned by {@link #beginInference}
         * @param isSucceeded whether the model call succeeded
         * @return completion of the accounting
         */
        java.util.concurrent.CompletableFuture<Void> endInference(InferenceLease lease, boolean isSucceeded);
    }
}
