/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.kvcache;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.kvcache.KVCacheMetadata;
import com.openjiuwen.core.session.AgentSessionApi;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * KVC lifecycle policy for DeepAgent subagents.
 *
 * <p>Mirrors Python's {@code openjiuwen/harness/kv_cache/kv_cache_subagent_lifecycle.py}.
 * Only {@code verification_agent} is sticky: its browser process/profile is
 * reused across TaskTool calls, so offload-and-keep saves a full warm-up.
 * Every other subagent and every failed worker is evicted so the previous
 * turn's tools/PageState cannot leak into unrelated calls.</p>
 *
 * @since 0.1.16
 */
public final class KVCacheSubagentLifecycle {
    private KVCacheSubagentLifecycle() {
    }

    /**
     * Whether the parent DeepAgent enables the affinity protocol.
     *
     * <p>Mirrors Python's {@code affinity_enabled(deep_agent)}: reads
     * {@code deep_config.enable_kv_cache_affinity} without inspecting
     * model/binding state, so the OFF path stays cheap.</p>
     *
     * @param deepAgent parent DeepAgent, may be {@code null}
     * @return true when the parent's config enables affinity
     */
    public static boolean affinityEnabled(Object deepAgent) {
        try {
            Object config = deepAgent == null ? null : deepAgent.getClass()
                    .getMethod("deepConfig").invoke(deepAgent);
            Object enabled = config == null ? null : config.getClass()
                    .getMethod("isEnableKvCacheAffinity").invoke(config);
            return Boolean.TRUE.equals(enabled);
        } catch (ReflectiveOperationException exception) {
            return false;
        }
    }

    /**
     * Whether this subagent type keeps its cache across calls.
     *
     * @param subagentType subagent type name
     * @return true only for {@code verification_agent}
     */
    public static boolean isStickySubagentType(String subagentType) {
        return subagentType != null && "verification_agent".equals(subagentType.strip());
    }

    /**
     * Return the provider-facing parent identity without changing OFF behavior.
     *
     * @param parentSession parent product session
     * @return parent cache id, or the runtime session id on failure
     */
    public static String resolveSubagentParentCacheId(AgentSessionApi parentSession) {
        String runtimeSessionId = parentSession == null || parentSession.getSessionId() == null
                ? ""
                : parentSession.getSessionId().strip();
        try {
            KVCacheMetadata.Lineage lineage = KVCacheMetadata.resolveSessionLineage(parentSession);
            if (lineage.isPresent() && !lineage.sessionId().isBlank()) {
                return lineage.sessionId();
            }
        } catch (IllegalArgumentException exception) {
            Loggers.SESSION.warning("[HarnessKVC] parent lineage resolution failed; using runtime session: "
                    + "session_id={0} error={1}", runtimeSessionId, exception.toString());
        }
        return runtimeSessionId;
    }

    /**
     * Disambiguate Team-member children while keeping runtime ids path-safe.
     *
     * <p>Mirrors Python's {@code scope_sub_session_id}: when the parent cache
     * id differs from the runtime parent id, a short sha256 digest of the
     * cache id is appended so same-named sub-sessions of different Team
     * branches cannot collide on the same cache key.</p>
     *
     * @param subSessionId original child session id
     * @param runtimeParentSessionId runtime parent session id
     * @param parentCacheId provider-facing parent cache id
     * @return scoped child session id
     */
    public static String scopeSubSessionId(
            String subSessionId, String runtimeParentSessionId, String parentCacheId) {
        if (parentCacheId == null || parentCacheId.isBlank() || parentCacheId.equals(runtimeParentSessionId)) {
            return subSessionId;
        }
        return subSessionId + "_scope_" + shortDigest(parentCacheId);
    }

    private static String shortDigest(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(value.getBytes(StandardCharsets.UTF_8))).substring(0, 12);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest unavailable", exception);
        }
    }

    /**
     * Resolve the sub-session id for one spawn, preferring the caller metadata.
     *
     * <p>Mirrors Python's {@code resolve_sub_session_id}: an explicit
     * {@code sub_session_id} in the task metadata wins; otherwise the id is
     * derived from the parent session and the task id so resume paths stay
     * deterministic.</p>
     *
     * @param taskId spawning task id
     * @param parentSessionId parent session id
     * @param metadata task metadata map, may be {@code null}
     * @return the resolved sub-session id
     */
    public static String resolveSubSessionId(String taskId, String parentSessionId,
            Map<String, Object> metadata) {
        if (metadata != null) {
            Object explicit = metadata.get("sub_session_id");
            if (explicit != null && !String.valueOf(explicit).isBlank()) {
                return String.valueOf(explicit);
            }
        }
        String safeTaskId = taskId == null || taskId.isBlank() ? "unknown" : taskId.strip();
        String safeParent = parentSessionId == null ? "" : parentSessionId;
        return safeParent + "_sub_" + safeTaskId;
    }

    /**
     * Create a child Session that shares its parent's application KVC runtime.
     *
     * <p>Mirrors Python's {@code create_subagent_session}: the child points
     * its lineage at {@code parentCacheId} and borrows the parent's runtime,
     * so its model calls carry the affinity hint under the same scheduler.</p>
     *
     * @param parentSession parent product session
     * @param subSessionId child session id
     * @param parentCacheId provider-facing parent cache id
     * @param card child agent card, may be {@code null}
     * @return the child session sharing the parent's runtime
     */
    public static AgentSessionApi createSubagentSession(AgentSessionApi parentSession,
            String subSessionId, String parentCacheId, Object card) {
        if (parentSession == null) {
            throw new IllegalArgumentException("parentSession is required");
        }
        com.openjiuwen.core.session.AgentSession child = new com.openjiuwen.core.session.AgentSession(
                subSessionId,
                parentSession.getEnvs(),
                card,
                null,
                true,
                Map.of(),
                parentSession.getKvCacheRuntime().orElse(null)
        );
        child.bindParentSessionId(parentCacheId);
        return child;
    }

    /**
     * Prefetch a sticky subagent's cache when it enters.
     *
     * @param session subagent session
     * @param subagentType subagent type name
     * @return completion of the prefetch
     */
    public static CompletableFuture<Boolean> prepareSubagent(AgentSessionApi session, String subagentType) {
        if (!isStickySubagentType(subagentType)) {
            return CompletableFuture.completedFuture(false);
        }
        return session.prepareKvc();
    }

    /**
     * Offload resumable successful workers; evict terminal/failed workers.
     *
     * @param session subagent session
     * @param subagentType subagent type name
     * @param isSucceeded whether the subagent completed successfully
     * @return completion of the cleanup
     */
    public static CompletableFuture<Boolean> finishSubagent(
            AgentSessionApi session, String subagentType, boolean isSucceeded) {
        if (isSucceeded && isStickySubagentType(subagentType)) {
            return session.suspendKvc();
        }
        return session.releaseKvc();
    }

    /**
     * Evict one subagent's cache unconditionally.
     *
     * @param session subagent session
     * @return completion of the eviction
     */
    public static CompletableFuture<Boolean> evictSubagent(AgentSessionApi session) {
        return session.releaseKvc();
    }
}
