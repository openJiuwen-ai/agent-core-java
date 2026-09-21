/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.kvcache;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.kvcache.KVCacheConfig;
import com.openjiuwen.core.session.AgentSessionApi;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Cancellation-safe cleanup around Session-owned KVC release.
 *
 * <p>Mirrors Python's {@code openjiuwen/agent_teams/kv_cache/kv_cache_cleanup.py}
 * and the harness session lifecycle hook: bind one-shot worker sessions to a
 * product-level cache root, then release their KVC after inference settles.
 * A cancellation storm must still release before dispose, and must never
 * swallow an outer cancellation.</p>
 *
 * @since 0.1.16
 */
public final class KVCacheCleanup {
    private static final System.Logger LOG = System.getLogger(KVCacheCleanup.class.getName());
    private static final Map<Object, HarnessSessionHookState> HARNESS_SESSION_HOOKS = new ConcurrentHashMap<>();

    private KVCacheCleanup() {
    }

    private record HarnessSessionHookState(String parentSessionId, boolean isEvictOnFinish) {
    }

    /**
     * Configure lineage and terminal behavior for a standalone worker harness.
     *
     * <p>Mirrors Python's {@code configure_harness_session_hooks}.</p>
     *
     * @param harness owner object used as the state key
     * @param productSessionId product cache root id
     * @param isEvictOnFinish whether the worker cache is evicted on finish
     * @return true when the hooks were recorded
     */
    public static boolean configureHarnessSessionHooks(
            Object harness, String productSessionId, boolean isEvictOnFinish) {
        if (harness == null) {
            return false;
        }
        String parentSessionId = productSessionId == null ? null : productSessionId.strip();
        if (parentSessionId == null || parentSessionId.isEmpty()) {
            return false;
        }
        HARNESS_SESSION_HOOKS.put(harness, new HarnessSessionHookState(parentSessionId, isEvictOnFinish));
        return true;
    }

    /**
     * Attach a worker session to its product-level cache root.
     *
     * <p>Mirrors Python's {@code on_harness_session_created}: lineage binding
     * is one-shot, so rebinding to a different parent is an ownership error
     * surfaced by {@code bindParentSessionId}.</p>
     *
     * @param harness owner object used as the state key
     * @param session worker session
     */
    public static void onHarnessSessionCreated(Object harness, AgentSessionApi session) {
        try {
            HarnessSessionHookState state = harness == null ? null : HARNESS_SESSION_HOOKS.get(harness);
            if (state == null || session == null || !(session instanceof com.openjiuwen.core.session.AgentSession
                    agentSession)) {
                return;
            }
            agentSession.bindParentSessionId(state.parentSessionId());
        } catch (IllegalStateException exception) {
            Loggers.SESSION.warning("[TeamKVC] on_harness_session_created failed; preserving normal flow: {0}",
                    exception.toString());
        }
    }

    /**
     * Release a one-shot worker's KVC after its inference has settled.
     *
     * <p>Mirrors Python's {@code after_harness_session_finished}.</p>
     *
     * @param harness owner object used as the state key
     * @param session worker session
     * @return completion of the release
     */
    public static CompletableFuture<Boolean> afterHarnessSessionFinished(Object harness, AgentSessionApi session) {
        HarnessSessionHookState state = harness == null ? null : HARNESS_SESSION_HOOKS.get(harness);
        try {
            if (state == null || !state.isEvictOnFinish() || session == null) {
                return CompletableFuture.completedFuture(false);
            }
            return session.releaseKvc();
        } catch (IllegalStateException exception) {
            Loggers.SESSION.warning("[TeamKVC] after_harness_session_finished failed; preserving normal flow: {0}",
                    exception.toString());
            return CompletableFuture.completedFuture(false);
        } finally {
            if (state != null && state.isEvictOnFinish()) {
                HARNESS_SESSION_HOOKS.remove(harness);
            }
        }
    }

    /**
     * Drop the recorded state for one harness owner.
     *
     * @param harness owner object used as the state key
     */
    public static void clearHarnessSessionHooks(Object harness) {
        if (harness != null) {
            HARNESS_SESSION_HOOKS.remove(harness);
        }
    }

    /**
     * Finish best-effort KVC release and disposal without swallowing
     * cancellation.
     *
     * <p>Mirrors Python's {@code cancellation_safe_release_then_dispose}:
     * release runs before dispose inside one bounded task; if the caller is
     * cancelled, the cleanup still finishes within the budget and the
     * cancellation propagates to the caller.</p>
     *
     * @param releaseKvc release action, may be {@code null}
     * @param dispose dispose action, required
     * @param ownerId owner used for diagnostics
     * @param timeoutSeconds whole-cleanup budget in seconds
     * @return completion of the cleanup sequence
     */
    public static CompletableFuture<Void> cancellationSafeReleaseThenDispose(
            java.util.function.Supplier<CompletableFuture<Boolean>> releaseKvc,
            Runnable dispose,
            String ownerId,
            double timeoutSeconds) {
        CompletableFuture<Void> sequence = new CompletableFuture<>();
        CompletableFuture.runAsync(() -> {
            if (releaseKvc != null) {
                awaitRelease(releaseKvc, ownerId);
            }
            runDispose(dispose, ownerId);
        }).whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                sequence.completeExceptionally(throwable);
            } else {
                sequence.complete(null);
            }
        });
        long timeoutMillis = java.math.BigDecimal.valueOf(timeoutSeconds)
                .multiply(java.math.BigDecimal.valueOf(1000))
                .longValueExact();
        return orTimeout(sequence, timeoutMillis, ownerId);
    }

    private static void awaitRelease(
            java.util.function.Supplier<CompletableFuture<Boolean>> releaseKvc, String ownerId) {
        try {
            releaseKvc.get().join();
        } catch (java.util.concurrent.CancellationException | java.util.concurrent.CompletionException exception) {
            Loggers.SESSION.warning("KVC release failed for {0}: {1}", ownerId, exception.toString());
        }
    }

    private static void runDispose(Runnable dispose, String ownerId) {
        try {
            dispose.run();
        } catch (IllegalArgumentException | IllegalStateException
                | java.util.concurrent.CompletionException exception) {
            Loggers.SESSION.warning("runtime dispose failed for {0}: {1}", ownerId, exception.toString());
        }
    }

    /**
     * Default-budget variant of {@link #cancellationSafeReleaseThenDispose}.
     *
     * @param releaseKvc release action, may be {@code null}
     * @param dispose dispose action, required
     * @param ownerId owner used for diagnostics
     * @return completion of the cleanup sequence
     */
    public static CompletableFuture<Void> cancellationSafeReleaseThenDispose(
            java.util.function.Supplier<CompletableFuture<Boolean>> releaseKvc, Runnable dispose, String ownerId) {
        return cancellationSafeReleaseThenDispose(
                releaseKvc, dispose, ownerId, KVCacheConfig.KVC_TERMINAL_CLEANUP_TIMEOUT_SECONDS);
    }

    private static CompletableFuture<Void> orTimeout(
            CompletableFuture<Void> future, long timeoutMillis, String ownerId) {
        long effectiveTimeout = timeoutMillis <= 0 ? 1 : timeoutMillis;
        return future.orTimeout(effectiveTimeout, TimeUnit.MILLISECONDS).handle((ignored, throwable) -> {
            if (throwable instanceof java.util.concurrent.TimeoutException) {
                Loggers.SESSION.warning("KVC cleanup timed out for {0}", ownerId);
                return null;
            }
            if (throwable != null) {
                Loggers.SESSION.warning("KVC cleanup failed for {0}: {1}", ownerId, throwable.toString());
            }
            return null;
        });
    }

    /**
     * Snapshot helper for tests and diagnostics.
     *
     * @return copy of the recorded hook states keyed by owner
     */
    public static Map<Object, String> harnessHookSnapshot() {
        Map<Object, String> snapshot = new LinkedHashMap<>();
        HARNESS_SESSION_HOOKS.forEach((key, value) -> snapshot.put(key, value.parentSessionId()));
        return snapshot;
    }
}
