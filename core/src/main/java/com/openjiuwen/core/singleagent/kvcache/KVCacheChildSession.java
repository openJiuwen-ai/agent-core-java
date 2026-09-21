/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.kvcache;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.kvcache.KVCacheMetadata;
import com.openjiuwen.core.kvcache.KVCacheTypes;
import com.openjiuwen.core.session.AgentSessionApi;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Build KVC lineage and Runtime inheritance for single-agent child Sessions.
 *
 * <p>Mirrors Python's
 * {@code openjiuwen/core/single_agent/kv_cache/kv_cache_child_session.py}.
 * The child inherits the parent's runtime and points its parent cache id at
 * the parent's provider-facing cache id, so Team branches with same-named
 * children do not collide.</p>
 *
 * @since 0.1.16
 */
public final class KVCacheChildSession {
    private KVCacheChildSession() {
    }

    /**
     * Child session inheritance bundle.
     *
     * @param envs child envs carrying the parent affinity override
     * @param parentSessionId provider-facing parent cache id
     * @param kvCacheRuntime the parent's shared runtime
     * @since 0.1.16
     */
    public record ChildSessionKwargs(Map<String, Object> envs, String parentSessionId,
            KVCacheTypes.KVCacheRuntimeProtocol kvCacheRuntime) {
    }

    /**
     * Build child session kwargs from the parent session.
     *
     * <p>Mirrors Python's {@code build_child_session_kwargs}: returns no
     * inheritance when the parent has no runtime (affinity OFF path stays
     * untouched).</p>
     *
     * @param parentSession parent product session
     * @return inheritance bundle, empty when affinity is not wired
     */
    public static java.util.Optional<ChildSessionKwargs> buildChildSessionKwargs(AgentSessionApi parentSession) {
        if (parentSession == null) {
            return java.util.Optional.empty();
        }
        KVCacheTypes.KVCacheRuntimeProtocol runtime = parentSession.getKvCacheRuntime().orElse(null);
        if (runtime == null) {
            return java.util.Optional.empty();
        }
        String runtimeSessionId = parentSession.getSessionId();
        String parentCacheId;
        try {
            KVCacheMetadata.Lineage lineage = KVCacheMetadata.resolveSessionLineage(parentSession);
            parentCacheId = lineage.isPresent() ? lineage.sessionId() : runtimeSessionId;
        } catch (IllegalArgumentException exception) {
            Loggers.SESSION.warning(
                    "KVC child lineage resolution failed; using runtime session: session_id={0} error={1}",
                    runtimeSessionId, exception.toString());
            parentCacheId = runtimeSessionId;
        }
        String resolvedParent = parentCacheId == null || parentCacheId.isBlank()
                ? runtimeSessionId
                : parentCacheId;
        Map<String, Object> childEnvs = new LinkedHashMap<>();
        if (parentSession.getEnvs() != null) {
            childEnvs.putAll(parentSession.getEnvs());
        }
        childEnvs.put(KVCacheMetadata.KV_CACHE_AFFINITY_PARENT_SESSION_ID_ENV, resolvedParent);
        return java.util.Optional.of(new ChildSessionKwargs(childEnvs, resolvedParent, runtime));
    }
}
