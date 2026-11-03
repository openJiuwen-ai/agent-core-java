/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.kvcache;

import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.SessionContextHolder;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Keep Runtime inference accounting around affinity-enabled model calls.
 *
 * <p>Mirrors Python's {@code KVCacheModelHook} in
 * {@code openjiuwen/core/kv_cache/kv_cache_model_hook.py}. The gate checks
 * kwargs identity against the current session's lineage, then acquires a
 * (child, root) inference lease. Any failure is fail-open: inference proceeds
 * without accounting.</p>
 *
 * @since 0.1.16
 */
public final class KVCacheModelHook {
    private static final System.Logger LOG = System.getLogger(KVCacheModelHook.class.getName());

    private KVCacheModelHook() {
    }

    /**
     * Begin inference accounting for one affinity model call.
     *
     * @param runtime runtime resolved from the current session, may be {@code null}
     * @param requestKwargs invoke kwargs, must already carry {@code session_id}
     * @param session current session used for lineage verification
     * @return a lease holder, or a completed holder with null lease when skipped
     */
    public static CompletableFuture<RuntimeLease> begin(
            KVCacheTypes.KVCacheRuntimeProtocol runtime, Map<String, Object> requestKwargs, Object session) {
        Object requestId = requestKwargs == null ? null : requestKwargs.get("session_id");
        if (runtime == null || requestId == null || String.valueOf(requestId).isBlank()) {
            return CompletableFuture.completedFuture(RuntimeLease.EMPTY);
        }
        KVCacheMetadata.Lineage lineage = KVCacheMetadata.resolveSessionLineage(session);
        if (!lineage.isPresent() || !String.valueOf(requestId).equals(lineage.sessionId())) {
            return CompletableFuture.completedFuture(RuntimeLease.EMPTY);
        }
        KVCacheIdentity identity = new KVCacheIdentity(lineage.sessionId(), lineage.parentSessionId());
        return runtime.beginInference(identity, session == null ? null : modelFrom(session, requestKwargs), null)
                .handle((lease, throwable) -> {
                    if (throwable != null) {
                        LOG.log(System.Logger.Level.WARNING,
                                "KVC inference admission failed; continue inference: {0}", throwable.toString());
                        return RuntimeLease.EMPTY;
                    }
                    return new RuntimeLease(runtime, lease);
                });
    }

    /**
     * End inference accounting, preserving the model result on failure.
     *
     * @param holder lease holder returned by {@link #begin}
     * @param isSucceeded whether the model call succeeded
     * @return completion of the accounting
     */
    public static CompletableFuture<Void> end(RuntimeLease holder, boolean isSucceeded) {
        if (holder == null || holder.lease == null || holder.runtime == null) {
            return CompletableFuture.completedFuture(null);
        }
        return holder.runtime.endInference(holder.lease, isSucceeded)
                .handle((ignored, throwable) -> {
                    if (throwable != null) {
                        LOG.log(System.Logger.Level.WARNING,
                                "KVC inference cleanup failed; preserve model result: {0}", throwable.toString());
                    }
                    return null;
                });
    }

    private static Object modelFrom(Object session, Map<String, Object> requestKwargs) {
        Object model = requestKwargs == null ? null : requestKwargs.get("__openjiuwen_kvc_model");
        return model != null ? model : session;
    }

    /**
     * Resolve the current session's runtime and identity for the hook gate.
     *
     * @param sessionClass expected session type
     * @param runtimeResolver resolves a runtime from a session
     * @param <S> session type
     * @return holder of session and runtime, or empty holder
     */
    public static <S> SessionRuntime<S> currentSessionRuntime(
            Class<S> sessionClass, Function<S, KVCacheTypes.KVCacheRuntimeProtocol> runtimeResolver) {
        Object current = SessionContextHolder.getCurrentSession();
        S session = current == null ? null : sessionClass.cast(current);
        if (session == null) {
            return SessionRuntime.empty();
        }
        return new SessionRuntime<>(session, runtimeResolver.apply(session));
    }

    /**
     * Resolve the current {@link AgentSessionApi} session.
     *
     * @return current session, empty when none is bound
     */
    public static Optional<AgentSessionApi> currentSession() {
        Object current = SessionContextHolder.getCurrentSession();
        return current instanceof AgentSessionApi api ? Optional.of(api) : Optional.empty();
    }

    /**
     * Pair of runtime and lease produced by {@link #begin}.
     *
     * @since 0.1.16
     */
    public static final class RuntimeLease {
        /** Empty holder used when accounting is skipped. */
        public static final RuntimeLease EMPTY = new RuntimeLease(null, null);

        private final KVCacheTypes.KVCacheRuntimeProtocol runtime;
        private final KVCacheTypes.InferenceLease lease;

        private RuntimeLease(KVCacheTypes.KVCacheRuntimeProtocol runtime, KVCacheTypes.InferenceLease lease) {
            this.runtime = runtime;
            this.lease = lease;
        }

        /**
         * Create a lease holder.
         *
         * @param runtime owning runtime
         * @param lease acquired lease
         * @return holder
         * @since 0.1.16
         */
        public static RuntimeLease of(KVCacheTypes.KVCacheRuntimeProtocol runtime,
                KVCacheTypes.InferenceLease lease) {
            return new RuntimeLease(runtime, lease);
        }

        public KVCacheTypes.KVCacheRuntimeProtocol runtime() {
            return runtime;
        }

        public KVCacheTypes.InferenceLease lease() {
            return lease;
        }

        /**
         * Whether this holder carries a live lease.
         *
         * @return true when both runtime and lease are present
         */
        public boolean isPresent() {
            return runtime != null && lease != null;
        }
    }

    /**
     * Pair of session and its optional runtime.
     *
     * @param <S> session type
     * @since 0.1.16
     */
    public record SessionRuntime<S>(S session, KVCacheTypes.KVCacheRuntimeProtocol runtime) {
        /**
         * Empty pair used when no session or runtime is available.
         *
         * @return empty session/runtime holder
         */
        public static <S> SessionRuntime<S> empty() {
            return new SessionRuntime<>(null, null);
        }
    }
}
