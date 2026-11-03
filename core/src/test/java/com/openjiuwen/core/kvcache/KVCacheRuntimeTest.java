/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.kvcache;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Compatibility tests for {@link KVCacheRuntime}, mirroring the behavior
 * fixed by Python {@code tests/unit_tests/core/kv_cache/test_kv_cache_runtime.py}.
 */
class KVCacheRuntimeTest {
    private final List<String> calls = new ArrayList<>();
    private final CountDownLatch offloadStarted = new CountDownLatch(1);
    private CountDownLatch releaseOffload = new CountDownLatch(1);
    private KVCacheRuntime runtime;

    @AfterEach
    void tearDown() {
        releaseOffload.countDown();
        if (runtime != null) {
            runtime.close().join();
        }
    }

    private Object affinityModel() {
        return new Object();
    }

    /** A recording model whose kvc methods are visible via reflection. */
    public static class FakeModel {
        private final List<String> calls;
        private final CountDownLatch offloadStarted;
        private final CountDownLatch releaseOffload;

        public FakeModel(List<String> calls, CountDownLatch offloadStarted, CountDownLatch releaseOffload) {
            this.calls = calls;
            this.offloadStarted = offloadStarted;
            this.releaseOffload = releaseOffload;
        }

        /**
         * Recorded prefetch action invoked reflectively by the runtime.
         *
         * @param sessionId cache id
         * @param parentSessionId parent cache id
         * @param target action target
         * @param model model name
         * @return whether the prefetch succeeded
         */
        public boolean prefetchKvc(String sessionId, String parentSessionId, String target, String model) {
            calls.add("prefetch:" + sessionId);
            return true;
        }

        /**
         * Recorded offload action invoked reflectively by the runtime.
         *
         * @param sessionId cache id
         * @param parentSessionId parent cache id
         * @param target action target
         * @param model model name
         * @return whether the offload succeeded
         * @throws InterruptedException when the release latch await is interrupted
         */
        public boolean offloadKvc(String sessionId, String parentSessionId, String target, String model)
                throws InterruptedException {
            calls.add("offload:" + sessionId);
            offloadStarted.countDown();
            releaseOffload.await();
            return true;
        }

        /**
         * Recorded evict action invoked reflectively by the runtime.
         *
         * @param sessionId cache id
         * @param parentSessionId parent cache id
         * @param target action target
         * @param model model name
         * @return whether the eviction succeeded
         */
        public boolean evictKvc(String sessionId, String parentSessionId, String target, String model) {
            calls.add("evict:" + sessionId);
            return true;
        }
    }

    private KVCacheRuntime shortRuntime(FakeModel model) {
        releaseOffload = model.releaseOffload;
        return new KVCacheRuntime(new KVCacheConfig(0.5, 0.5, 0.5), null);
    }

    @Test
    void prepareReturnsFalseWithoutAnyBinding() {
        runtime = new KVCacheRuntime(new KVCacheConfig(0.5, 0.5, 0.5), null);
        boolean isPrepared = runtime.prepare(new KVCacheIdentity("history", "history")).join();

        assertThat(isPrepared).isFalse();
        assertThat(runtime.bindingCount()).isZero();
    }

    @Test
    void suspendSendsOneOffloadAndCoalescesDuplicates() throws Exception {
        FakeModel model = new FakeModel(calls, offloadStarted, releaseOffload);
        runtime = shortRuntime(model);
        KVCacheIdentity identity = new KVCacheIdentity("session", "session");
        KVCacheTypes.InferenceLease lease = runtime.beginInference(identity, model, null).join();
        runtime.endInference(lease, true).join();

        assertThat(runtime.suspend(identity).join()).isTrue();
        assertThat(offloadStarted.await(2, TimeUnit.SECONDS)).isTrue();

        // A duplicate suspend while the OFFLOAD is still queued coalesces into
        // the pending action and reports scheduled (Python returns True too).
        assertThat(runtime.suspend(identity).join()).isTrue();
        releaseOffload.countDown();

        assertThat(calls).containsExactly("offload:session");
    }

    @Test
    void prepareWaitsForOffloadThenIssuesPrefetchOnly() throws Exception {
        FakeModel model = new FakeModel(calls, offloadStarted, releaseOffload);
        runtime = shortRuntime(model);
        KVCacheIdentity identity = new KVCacheIdentity("session", "session");
        KVCacheTypes.InferenceLease lease = runtime.beginInference(identity, model, null).join();
        runtime.endInference(lease, true).join();

        assertThat(runtime.suspend(identity).join()).isTrue();
        assertThat(offloadStarted.await(2, TimeUnit.SECONDS)).isTrue();

        AtomicBoolean done = new AtomicBoolean(false);
        CompletableFuture.runAsync(() -> {
            runtime.prepare(identity).join();
            done.set(true);
        });
        assertThat(done.get()).isFalse();

        releaseOffload.countDown();
        assertThat(runtime.prepare(identity).join()).isTrue();
        assertThat(calls).containsExactly("offload:session", "prefetch:session");
    }

    @Test
    void releaseIsTerminalAndPreparesReportFalse() {
        FakeModel model = new FakeModel(calls, offloadStarted, releaseOffload);
        runtime = shortRuntime(model);
        model.releaseOffload.countDown();
        KVCacheIdentity identity = new KVCacheIdentity("session", "session");
        KVCacheTypes.InferenceLease lease = runtime.beginInference(identity, model, null).join();
        runtime.endInference(lease, true).join();

        assertThat(runtime.release(identity).join()).isTrue();
        assertThat(calls).contains("evict:session");
        assertThat(runtime.prepare(identity).join()).isFalse();
        assertThat(runtime.bindingCount()).isZero();
    }

    @Test
    void releasedSessionIdCanBeRegisteredByNewSession() {
        FakeModel model = new FakeModel(calls, offloadStarted, releaseOffload);
        runtime = shortRuntime(model);
        model.releaseOffload.countDown();
        KVCacheIdentity identity = new KVCacheIdentity("session", "session");
        KVCacheTypes.InferenceLease lease = runtime.beginInference(identity, model, null).join();
        runtime.endInference(lease, true).join();
        assertThat(runtime.release(identity).join()).isTrue();

        KVCacheTypes.InferenceLease newLease = runtime.beginInference(identity, model, null).join();

        assertThat(newLease).isNotNull();
        runtime.endInference(newLease, true).join();
    }

    @Test
    void failedOffloadDisablesLaterOffloadWithoutBlockingInference() throws Exception {
        CountDownLatch failingOffloadStarted = new CountDownLatch(1);
        CountDownLatch immediateRelease = new CountDownLatch(0);
        FakeModel failingModel = new FakeModel(calls, failingOffloadStarted, immediateRelease) {
            @Override
            public boolean offloadKvc(String sessionId, String parentSessionId, String target, String model) {
                calls.add("offload:" + sessionId);
                failingOffloadStarted.countDown();
                return false;
            }
        };
        runtime = shortRuntime(failingModel);
        KVCacheIdentity identity = new KVCacheIdentity("session", "session");
        KVCacheTypes.InferenceLease lease = runtime.beginInference(identity, failingModel, null).join();
        runtime.endInference(lease, true).join();

        assertThat(runtime.suspend(identity).join()).isTrue();
        assertThat(failingOffloadStarted.await(2, TimeUnit.SECONDS)).isTrue();

        KVCacheTypes.InferenceLease next = runtime.beginInference(identity, failingModel, null).join();
        assertThat(next).isNotNull();
        runtime.endInference(next, true).join();
        assertThat(runtime.suspend(identity).join()).isFalse();
        assertThat(calls.stream().filter(call -> call.startsWith("offload")).count()).isEqualTo(1);
    }

    @Test
    void historicalSessionUsesFallbackBinding() {
        FakeModel fallback = new FakeModel(calls, offloadStarted, releaseOffload);
        fallback.releaseOffload.countDown();
        runtime = new KVCacheRuntime(new KVCacheConfig(0.5, 0.5, 0.5), () -> fallback);
        KVCacheIdentity identity = new KVCacheIdentity("history", "history");

        assertThat(runtime.prepare(identity).join()).isTrue();
        assertThat(calls).containsExactly("prefetch:history");
        assertThat(runtime.release(identity).join()).isTrue();
        assertThat(calls.get(calls.size() - 1)).isEqualTo("evict:history");
    }

    @Test
    void ambiguousFallbackNeverGuessesWhenProviderReturnsNull() {
        runtime = new KVCacheRuntime(new KVCacheConfig(0.5, 0.5, 0.5), () -> null);
        KVCacheIdentity identity = new KVCacheIdentity("session-b", "session-b");

        assertThat(runtime.prepare(identity).join()).isFalse();
        assertThat(calls).isEmpty();
    }

    @Test
    void teamRootOffloadSendsOneRootActionPerDomain() throws Exception {
        FakeModel model = new FakeModel(calls, offloadStarted, releaseOffload);
        runtime = shortRuntime(model);
        model.releaseOffload.countDown();
        KVCacheIdentity childA = new KVCacheIdentity("team/agent-a", "team");
        KVCacheIdentity childB = new KVCacheIdentity("team/agent-b", "team");
        for (KVCacheIdentity identity : List.of(childA, childB)) {
            KVCacheTypes.InferenceLease lease = runtime.beginInference(identity, model, null).join();
            runtime.endInference(lease, true).join();
        }

        assertThat(runtime.suspend(new KVCacheIdentity("team", "team")).join()).isTrue();
        assertThat(offloadStarted.await(2, TimeUnit.SECONDS)).isTrue();

        assertThat(calls.stream().filter(call -> call.startsWith("offload")).count()).isEqualTo(1);
    }

    @Test
    void closeIsIdempotentAndClearsBindings() {
        FakeModel model = new FakeModel(calls, offloadStarted, releaseOffload);
        runtime = shortRuntime(model);
        model.releaseOffload.countDown();
        KVCacheIdentity identity = new KVCacheIdentity("session", "session");
        KVCacheTypes.InferenceLease lease = runtime.beginInference(identity, model, null).join();
        runtime.endInference(lease, true).join();

        runtime.close().join();
        runtime.close().join();

        assertThat(runtime.isClosed()).isTrue();
        assertThat(runtime.bindingCount()).isZero();
    }

    @Test
    void bindingResidencyTransitionsOnInferenceOutcome() {
        FakeModel model = new FakeModel(calls, offloadStarted, releaseOffload);
        runtime = shortRuntime(model);
        KVCacheIdentity identity = new KVCacheIdentity("session", "session");
        KVCacheTypes.InferenceLease lease = runtime.beginInference(identity, model, null).join();

        runtime.endInference(lease, true).join();
        assertThat(runtime.isResidency("session", BindingState.Residency.RESIDENT)).isTrue();

        KVCacheTypes.InferenceLease failing = runtime.beginInference(identity, model, null).join();
        runtime.endInference(failing, false).join();
        assertThat(runtime.isResidency("session", BindingState.Residency.UNKNOWN)).isTrue();
    }

    @Test
    void invalidIdentityIsRejectedEverywhere() {
        runtime = new KVCacheRuntime(new KVCacheConfig(0.5, 0.5, 0.5), null);
        assertThat(runtime.beginInference(new KVCacheIdentity("", "x"), affinityModel(), null).join()).isNull();
        assertThat(runtime.beginInference(new KVCacheIdentity("x", ""), affinityModel(), null).join()).isNull();
        assertThat(runtime.prepare(new KVCacheIdentity(" ", "x")).join()).isFalse();
        assertThat(runtime.suspend(new KVCacheIdentity("x", " ")).join()).isFalse();
    }

    @Test
    void completedOffloadIsNotSentAgainWhileStillOffloaded() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch immediateRelease = new CountDownLatch(0);
        FakeModel immediateModel = new FakeModel(calls, started, immediateRelease) {
            @Override
            public boolean offloadKvc(String sessionId, String parentSessionId, String target, String model) {
                calls.add("offload:" + sessionId);
                started.countDown();
                return true;
            }
        };
        runtime = shortRuntime(immediateModel);
        KVCacheIdentity identity = new KVCacheIdentity("session", "session");
        KVCacheTypes.InferenceLease lease = runtime.beginInference(identity, immediateModel, null).join();
        runtime.endInference(lease, true).join();

        assertThat(runtime.suspend(identity).join()).isTrue();
        assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();

        // Let the completed OFFLOAD drain so its pending slot is cleared and
        // the scope settles in BLOCKED with residency OFFLOADED.
        for (int i = 0; i < 20 && runtime.pendingTaskCount() > 0; i++) {
            Thread.sleep(10);
        }

        // While the scope stays BLOCKED and fully offloaded, a duplicate
        // suspend is an idempotent skip (Python returns False too).
        assertThat(runtime.suspend(identity).join()).isFalse();
        assertThat(calls.stream().filter(call -> call.startsWith("offload")).count()).isEqualTo(1);
    }
}
