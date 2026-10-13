/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.agentteams.kvcache;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.agentteams.kvcache.KVCacheCleanup;
import com.openjiuwen.core.kvcache.KVCacheConfig;
import com.openjiuwen.core.kvcache.KVCacheRuntime;
import com.openjiuwen.core.session.AgentSession;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Compatibility tests for {@link KVCacheCleanup}, mirroring Python's
 * {@code tests/unit_tests/agent_teams/kv_cache/test_kv_cache_harness_session_lifecycle_hook.py}.
 */
class KVCacheCleanupHarnessHookTest {
    private static AgentSession newSession() {
        return new AgentSession("worker", Map.of(), null, null, true, Map.of(),
                new KVCacheRuntime(new KVCacheConfig(0.5, 0.5, 0.5), null));
    }

    @Test
    void oneShotHookBindsParentAndReleasesSession() {
        Object harness = new Object();
        AgentSession session = newSession();

        assertThat(KVCacheCleanup.configureHarnessSessionHooks(
                harness, "product", true)).isTrue();
        KVCacheCleanup.onHarnessSessionCreated(harness, session);
        assertThat(session.getKvCacheRuntime()).isPresent();
        KVCacheCleanup.afterHarnessSessionFinished(harness, session).join();

        assertThat(session.getCacheIdentity().parentCacheId()).isEqualTo("product");
        // A released session no longer exposes its runtime (Python
        // get_kv_cache_runtime after release_kvc).
        assertThat(session.getKvCacheRuntime()).isEmpty();
        // The one-shot state is gone: a later finish returns false.
        assertThat(KVCacheCleanup.afterHarnessSessionFinished(harness, session).join())
                .isFalse();
    }

    @Test
    void configureRejectsBlankProductSessionIdAndNullHarness() {
        assertThat(KVCacheCleanup.configureHarnessSessionHooks(
                new Object(), null, true)).isFalse();
        assertThat(KVCacheCleanup.configureHarnessSessionHooks(
                new Object(), "  ", true)).isFalse();
        assertThat(KVCacheCleanup.configureHarnessSessionHooks(
                null, "product", true)).isFalse();
    }

    @Test
    void afterFinishIsNoopWithoutConfiguredHooks() {
        AgentSession session = newSession();

        Boolean isReleased = KVCacheCleanup.afterHarnessSessionFinished(
                new Object(), session).join();

        assertThat(isReleased).isFalse();
    }

    @Test
    void hookStateIsOneShotPerHarnessOwner() {
        Object harness = new Object();
        AgentSession first = newSession();

        assertThat(KVCacheCleanup.configureHarnessSessionHooks(
                harness, "product", true)).isTrue();
        KVCacheCleanup.onHarnessSessionCreated(harness, first);
        KVCacheCleanup.afterHarnessSessionFinished(harness, first).join();

        AgentSession second = newSession();
        KVCacheCleanup.onHarnessSessionCreated(harness, second);
        Boolean isSecondRelease = KVCacheCleanup.afterHarnessSessionFinished(
                harness, second).join();

        assertThat(first.getCacheIdentity().parentCacheId()).isEqualTo("product");
        assertThat(second.getCacheIdentity().parentCacheId()).isEqualTo("worker");
        assertThat(isSecondRelease).isFalse();
    }

    @Test
    void clearHarnessSessionHooksDropsState() {
        Object harness = new Object();

        assertThat(KVCacheCleanup.configureHarnessSessionHooks(
                harness, "product", false)).isTrue();
        KVCacheCleanup.clearHarnessSessionHooks(harness);

        AgentSession session = newSession();
        KVCacheCleanup.onHarnessSessionCreated(harness, session);

        // No hook state: the session keeps its self-pointing identity.
        assertThat(session.getCacheIdentity().parentCacheId()).isEqualTo(session.getSessionId());
    }

    @Test
    void releaseWithoutEvictKeepsHookStateUntilCleared() {
        AtomicBoolean isReleased = new AtomicBoolean(false);
        KVCacheRuntime runtime = new KVCacheRuntime(new KVCacheConfig(0.5, 0.5, 0.5), null) {
            @Override
            public CompletableFuture<Boolean> release(
                    com.openjiuwen.core.kvcache.KVCacheIdentity identity) {
                isReleased.set(true);
                return super.release(identity);
            }
        };
        AgentSession keeping = new AgentSession("keeper", Map.of(), null, null, true, Map.of(), runtime);

        Object harness = new Object();
        AgentSession session = newSession();

        assertThat(KVCacheCleanup.configureHarnessSessionHooks(
                harness, "product", false)).isTrue();
        KVCacheCleanup.onHarnessSessionCreated(harness, session);
        Boolean isReleasedFlag = KVCacheCleanup.afterHarnessSessionFinished(harness, keeping).join();

        assertThat(isReleasedFlag).isFalse();
        assertThat(isReleased).isFalse();
    }
}
