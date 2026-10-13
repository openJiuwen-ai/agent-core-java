/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.kvcache;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Regression tests for the runtime-to-model reflection bridge: management
 * actions must reach {@code Model} shaped methods (a {@code KvCacheRange}
 * parameter and a {@code CompletableFuture<Boolean>} outcome) instead of the
 * Python-style string signatures.
 */
class KVCacheRuntimeActionBridgeTest {
    @Test
    void releasePassesSessionRangeToKvCacheRangeSignature() {
        List<String> calls = new ArrayList<>();
        List<AgentHint.KvCacheRange> ranges = new ArrayList<>();
        RangeSignatureFakeModel model = new RangeSignatureFakeModel(calls, ranges);
        KVCacheRuntime runtime = new KVCacheRuntime(new KVCacheConfig(0.5, 0.5, 0.5), () -> model);

        boolean isReleased = runtime.release(new KVCacheIdentity("session", "session")).join();

        assertThat(isReleased).isTrue();
        assertThat(calls).containsExactly("evict:session");
        assertThat(ranges).hasSize(1);
        AgentHint.KvCacheRange range = ranges.get(0);
        assertThat(range.target()).isEqualTo("session");
        assertThat(range.msgStart()).isNull();
        assertThat(range.msgEnd()).isNull();
        assertThat(range.shouldIncludeTools()).isFalse();
    }

    @Test
    void prepareUnwrapsCompletableFutureOutcome() {
        FutureOutcomeFakeModel model = new FutureOutcomeFakeModel();
        KVCacheRuntime runtime = new KVCacheRuntime(new KVCacheConfig(0.5, 0.5, 0.5), () -> model);

        boolean isPrepared = runtime.prepare(new KVCacheIdentity("session", "session")).join();

        assertThat(isPrepared).isTrue();
        assertThat(model.isPrefetched()).isTrue();
    }

    /**
     * Fake model shaped like {@code Model.evictKvc}: a {@code KvCacheRange}
     * parameter.
     */
    private static final class RangeSignatureFakeModel {
        private final List<String> calls;
        private final List<AgentHint.KvCacheRange> ranges;

        private RangeSignatureFakeModel(List<String> calls, List<AgentHint.KvCacheRange> ranges) {
            this.calls = calls;
            this.ranges = ranges;
        }

        /**
         * Record the eviction request, mirroring {@code Model.evictKvc}.
         *
         * @param sessionId cache id sent by the runtime
         * @param parentSessionId parent cache id sent by the runtime
         * @param range eviction range resolved by the runtime
         * @return whether the fake eviction succeeded
         */
        public boolean evictKvc(String sessionId, String parentSessionId, AgentHint.KvCacheRange range) {
            calls.add("evict:" + sessionId);
            ranges.add(range);
            return true;
        }
    }

    /**
     * Fake model shaped like {@code Model.prefetchKvc}: a future outcome.
     */
    private static final class FutureOutcomeFakeModel {
        private boolean isPrefetched;

        /**
         * Record the prefetch request, mirroring {@code Model.prefetchKvc}.
         *
         * @param sessionId cache id sent by the runtime
         * @param parentSessionId parent cache id sent by the runtime
         * @return completed future reporting success
         */
        public CompletableFuture<Boolean> prefetchKvc(String sessionId, String parentSessionId) {
            isPrefetched = true;
            return CompletableFuture.completedFuture(true);
        }

        /**
         * Whether a prefetch request reached this fake.
         *
         * @return true when a prefetch request reached this fake
         */
        public boolean isPrefetched() {
            return isPrefetched;
        }
    }
}
