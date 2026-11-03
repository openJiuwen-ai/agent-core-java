/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.kvcache;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.session.AgentSession;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Compatibility tests for {@link KVCacheModelHook}, mirroring Python's
 * {@code tests/unit_tests/core/kv_cache/test_kv_cache_model_hook.py}.
 *
 * <p>Python drives the hook through the {@code with_session} contextvar;
 * Java passes the session explicitly, so the kwargs-identity gate, the
 * fail-open behavior, and the caller cancellation are the observable
 * contract under test.</p>
 */
class KVCacheModelHookTest {
    /** Mirrors Python's RecordingRuntime. */
    private static final class RecordingRuntime implements KVCacheTypes.KVCacheRuntimeProtocol {
        private final List<String> events = new ArrayList<>();

        @Override
        public CompletableFuture<Boolean> prepare(KVCacheIdentity identity) {
            return CompletableFuture.completedFuture(true);
        }

        @Override
        public CompletableFuture<Boolean> suspend(KVCacheIdentity identity) {
            return CompletableFuture.completedFuture(true);
        }

        @Override
        public CompletableFuture<Boolean> release(KVCacheIdentity identity) {
            return CompletableFuture.completedFuture(true);
        }

        @Override
        public CompletableFuture<KVCacheTypes.InferenceLease> beginInference(
                KVCacheIdentity identity, Object model, String modelName) {
            events.add("begin:" + identity.cacheId());
            return CompletableFuture.completedFuture(new KVCacheTypes.InferenceLease(null, null));
        }

        @Override
        public CompletableFuture<Void> endInference(KVCacheTypes.InferenceLease lease, boolean isSucceeded) {
            events.add("end:" + isSucceeded);
            return CompletableFuture.completedFuture(null);
        }
    }

    @Test
    void modelHookUsesCurrentSessionWithoutMutatingRequest() {
        RecordingRuntime runtime = new RecordingRuntime();
        AgentSession session = new AgentSession("session", Map.of(), null, null, true, Map.of(), runtime);
        Map<String, Object> requestKwargs = new LinkedHashMap<>();
        requestKwargs.put("session_id", "session");
        requestKwargs.put("parent_session_id", "session");

        KVCacheModelHook.RuntimeLease lease = KVCacheModelHook
                .begin(runtime, requestKwargs, session).join();
        KVCacheModelHook.end(lease, true).join();

        assertThat(requestKwargs).containsOnly(
                Map.entry("session_id", "session"), Map.entry("parent_session_id", "session"));
        assertThat(runtime.events).containsExactly("begin:session", "end:true");
        assertThat(lease.isPresent()).isTrue();
    }

    @Test
    void modelHookIsNoopWithoutAffinityRequestOrRuntime() {
        RecordingRuntime runtime = new RecordingRuntime();

        assertThat(KVCacheModelHook.begin(runtime, Map.of(), new AgentSession()).join().isPresent())
                .isFalse();
        assertThat(KVCacheModelHook.begin(null,
                Map.of("session_id", "session"), new AgentSession()).join().isPresent())
                .isFalse();
        assertThat(runtime.events).isEmpty();
    }

    @Test
    void modelHookIsNoopWhenRequestIdentityDoesNotMatchSession() {
        RecordingRuntime runtime = new RecordingRuntime();
        AgentSession session = new AgentSession("session", Map.of(), null, null, true, Map.of(), runtime);

        KVCacheModelHook.RuntimeLease lease = KVCacheModelHook.begin(runtime,
                Map.of("session_id", "other-session"), session).join();

        assertThat(lease.isPresent()).isFalse();
        assertThat(runtime.events).isEmpty();
    }

    @Test
    void modelHookRuntimeFailuresDoNotChangeModelFlow() {
        KVCacheTypes.KVCacheRuntimeProtocol brokenRuntime = new KVCacheTypes.KVCacheRuntimeProtocol() {
            @Override
            public CompletableFuture<Boolean> prepare(KVCacheIdentity identity) {
                return CompletableFuture.failedFuture(new IllegalStateException("binding failed"));
            }

            @Override
            public CompletableFuture<Boolean> suspend(KVCacheIdentity identity) {
                return CompletableFuture.failedFuture(new IllegalStateException("binding failed"));
            }

            @Override
            public CompletableFuture<Boolean> release(KVCacheIdentity identity) {
                return CompletableFuture.failedFuture(new IllegalStateException("binding failed"));
            }

            @Override
            public CompletableFuture<KVCacheTypes.InferenceLease> beginInference(
                    KVCacheIdentity identity, Object model, String modelName) {
                return CompletableFuture.failedFuture(new IllegalStateException("binding failed"));
            }

            @Override
            public CompletableFuture<Void> endInference(KVCacheTypes.InferenceLease lease,
                    boolean isSucceeded) {
                return CompletableFuture.failedFuture(new IllegalStateException("cleanup failed"));
            }
        };
        AgentSession session = new AgentSession("session", Map.of(), null, null, true, Map.of(),
                brokenRuntime);

        KVCacheModelHook.RuntimeLease lease = KVCacheModelHook.begin(brokenRuntime,
                Map.of("session_id", "session"), session).join();

        assertThat(lease.isPresent()).isFalse();
        KVCacheModelHook.end(KVCacheModelHook.RuntimeLease.of(brokenRuntime,
                new KVCacheTypes.InferenceLease(null, null)), true).join();
    }

    @Test
    void endIsNoopForEmptyAndNullHolders() {
        KVCacheModelHook.end(null, true).join();
        KVCacheModelHook.end(KVCacheModelHook.RuntimeLease.EMPTY, true).join();
    }

    @Test
    void modelHookPreservesCallerCancellation() {
        AtomicInteger beginCalls = new AtomicInteger();
        KVCacheTypes.KVCacheRuntimeProtocol waitingRuntime = new KVCacheTypes.KVCacheRuntimeProtocol() {
            @Override
            public CompletableFuture<Boolean> prepare(KVCacheIdentity identity) {
                return CompletableFuture.completedFuture(true);
            }

            @Override
            public CompletableFuture<Boolean> suspend(KVCacheIdentity identity) {
                return CompletableFuture.completedFuture(true);
            }

            @Override
            public CompletableFuture<Boolean> release(KVCacheIdentity identity) {
                return CompletableFuture.completedFuture(true);
            }

            @Override
            public CompletableFuture<KVCacheTypes.InferenceLease> beginInference(
                    KVCacheIdentity identity, Object model, String modelName) {
                beginCalls.incrementAndGet();
                return new CompletableFuture<>();
            }

            @Override
            public CompletableFuture<Void> endInference(KVCacheTypes.InferenceLease lease,
                    boolean isSucceeded) {
                return CompletableFuture.completedFuture(null);
            }
        };
        AgentSession session = new AgentSession("session", Map.of(), null, null, true, Map.of(),
                waitingRuntime);

        CompletableFuture<KVCacheModelHook.RuntimeLease> pending = KVCacheModelHook.begin(
                waitingRuntime, Map.of("session_id", "session"), session);
        pending.cancel(true);

        assertThat(pending).isCancelled();
        assertThat(beginCalls).hasValue(1);
    }
}
