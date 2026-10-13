/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.kvcache;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.kvcache.KVCacheConfig;
import com.openjiuwen.core.kvcache.KVCacheMetadata;
import com.openjiuwen.core.kvcache.KVCacheRuntime;
import com.openjiuwen.core.session.AgentSession;

import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * Compatibility tests for {@link KVCacheChildSession}, mirroring Python's
 * {@code tests/unit_tests/core/single_agent/kv_cache/test_kv_cache_child_session.py}.
 *
 * <p>Python gates the hook on {@code enable_kv_cache_affinity}; Java's
 * equivalent OFF path is a parent session without a bound runtime, which
 * must stay a strict no-op.</p>
 */
class KVCacheChildSessionTest {
    private static KVCacheRuntime newRuntime() {
        return new KVCacheRuntime(new KVCacheConfig(0.5, 0.5, 0.5), null);
    }

    @Test
    void childSessionHookIsStrictNoopWhenAffinityDisabled() {
        // Affinity disabled == no runtime on the parent; the OFF path must not
        // even read envs or session ids.
        AgentSession parentSession = new AgentSession("parent", Map.of(), null);

        java.util.Optional<KVCacheChildSession.ChildSessionKwargs> kwargs =
                KVCacheChildSession.buildChildSessionKwargs(parentSession);

        assertThat(kwargs).isEmpty();
    }

    @Test
    void childSessionHookIsNoopForNullParent() {
        assertThat(KVCacheChildSession.buildChildSessionKwargs(null)).isEmpty();
    }

    @Test
    void childSessionHookInjectsParentLineageWhenEnabled() {
        KVCacheRuntime runtime = newRuntime();
        AgentSession parentSession = new AgentSession(
                "parent-session", Map.of("existing", "value"), null, null, true, Map.of(), runtime);

        java.util.Optional<KVCacheChildSession.ChildSessionKwargs> kwargs =
                KVCacheChildSession.buildChildSessionKwargs(parentSession);

        assertThat(kwargs).isPresent();
        assertThat(kwargs.get().envs()).contains(
                Map.entry("existing", "value"),
                Map.entry(KVCacheMetadata.KV_CACHE_AFFINITY_PARENT_SESSION_ID_ENV, "parent-session"));
        assertThat(kwargs.get().parentSessionId()).isEqualTo("parent-session");
        assertThat(kwargs.get().kvCacheRuntime()).isSameAs(runtime);
    }

    @Test
    void childSessionHookUsesTeamMemberCacheIdentity() {
        KVCacheRuntime runtime = newRuntime();
        AgentSession parentSession = new AgentSession(
                "product-session", Map.of("existing", "value"), null, null, true, Map.of(), runtime);
        parentSession.setTeamCacheScope("team-a", "member-a");

        java.util.Optional<KVCacheChildSession.ChildSessionKwargs> kwargs =
                KVCacheChildSession.buildChildSessionKwargs(parentSession);

        assertThat(kwargs).isPresent();
        assertThat(kwargs.get().envs()).contains(
                Map.entry("existing", "value"),
                Map.entry(KVCacheMetadata.KV_CACHE_AFFINITY_PARENT_SESSION_ID_ENV,
                        "team:product-session:team:team-a:member:member-a"));
        assertThat(kwargs.get().parentSessionId())
                .isEqualTo("team:product-session:team:team-a:member:member-a");
    }
}
