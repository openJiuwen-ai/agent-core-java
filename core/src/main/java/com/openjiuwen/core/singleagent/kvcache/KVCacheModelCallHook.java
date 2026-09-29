/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.kvcache;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.context.ContextWindow;
import com.openjiuwen.core.context.context.KVCacheManager;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.kvcache.KVCacheMetadata;
import com.openjiuwen.core.kvcache.KVCacheModelHook;
import com.openjiuwen.core.kvcache.KVCacheTypes;
import com.openjiuwen.core.kvcache.KVCacheIdentity;
import com.openjiuwen.core.session.AgentSessionApi;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Hooks for ReAct model calls and ContextWindow KVC invalidation.
 *
 * <p>Mirrors Python's {@code kv_cache_react_model_call_hook.py}: capability
 * resolution, lineage resolution, window-diff eviction, and invoke kwargs
 * construction. All operations are fail-open.</p>
 *
 * @since 0.1.16
 */
public class KVCacheModelCallHook {
    private static final System.Logger LOG = System.getLogger(KVCacheModelCallHook.class.getName());

    private volatile boolean isAffinityWarningLogged;

    /**
     * Resolved capability pair for one model call.
     *
     * @param isAffinityEnabled whether the agent config wants affinity
     * @param isAffinitySupported whether the model declares affinity capability
     * @since 0.1.16
     */
    public record KVCacheCallCapabilities(boolean isAffinityEnabled, boolean isAffinitySupported) {
        /**
         * Whether both configuration and model capability are present.
         *
         * @return true when affinity should be applied
         */
        public boolean isActive() {
            return isAffinityEnabled && isAffinitySupported;
        }
    }

    /**
     * Clear the one-time warning so a model switch can warn again.
     */
    public void resetWarnings() {
        isAffinityWarningLogged = false;
    }

    /**
     * Resolve whether this model call uses affinity.
     *
     * <p>Mirrors Python's {@code resolve_runtime}.</p>
     *
     * @param llm current model
     * @param isEnableKvCacheAffinity agent-level affinity switch
     * @return resolved capabilities
     */
    public KVCacheCallCapabilities resolveRuntime(Model llm, boolean isEnableKvCacheAffinity) {
        boolean isAffinitySupported = isEnableKvCacheAffinity && llm != null && llm.supportsKvCacheAffinity();
        KVCacheCallCapabilities runtime = new KVCacheCallCapabilities(
                isEnableKvCacheAffinity, isAffinitySupported);
        warnUnsupported(runtime);
        return runtime;
    }

    /**
     * Resolve who this call is and who its parent is.
     *
     * <p>Mirrors Python's {@code resolve_lineage}.</p>
     *
     * @param runtime resolved capabilities
     * @param session current session, may be {@code null}
     * @param fallbackSessionId runtime session id used when lineage is empty
     * @return (sessionId, parentSessionId); both {@code null} when inactive
     */
    public KVCacheMetadata.Lineage resolveLineage(
            KVCacheCallCapabilities runtime, AgentSessionApi session, String fallbackSessionId) {
        if (!runtime.isActive()) {
            return KVCacheMetadata.Lineage.EMPTY;
        }
        KVCacheMetadata.Lineage lineage = KVCacheMetadata.resolveSessionLineage(session);
        if (!lineage.isPresent()) {
            return new KVCacheMetadata.Lineage(fallbackSessionId, fallbackSessionId);
        }
        return lineage;
    }

    /**
     * Evict stale KV cache entries when the window changed non-append-only.
     *
     * <p>Mirrors Python's {@code handle_context_window_change}: append-only
     * windows keep every cached prefix valid; other changes evict the
     * invalidated half-open ranges via the affinity protocol.</p>
     *
     * @param runtime resolved capabilities
     * @param llm current model
     * @param contextWindow current window
     * @param previousWindow window previously sent to the LLM, may be {@code null}
     * @param sessionLineage resolved lineage
     * @return completion of the eviction; false when nothing was evicted
     */
    public CompletableFuture<Boolean> handleContextWindowChange(
            KVCacheCallCapabilities runtime, Model llm, ContextWindow contextWindow,
            ContextWindow previousWindow, KVCacheMetadata.Lineage sessionLineage) {
        if (!runtime.isActive()) {
            return CompletableFuture.completedFuture(false);
        }
        if (sessionLineage == null || !sessionLineage.isPresent()) {
            LOG.log(System.Logger.Level.WARNING,
                    "Skip Ascend KV cache window diff eviction because session_id is empty.");
            return CompletableFuture.completedFuture(false);
        }
        Optional<KVCacheManager.ReleaseDecision> decision = KVCacheManager.firstChangedDecision(
                previousWindow, contextWindow);
        if (decision.isEmpty() || !decision.get().shouldRelease()) {
            return CompletableFuture.completedFuture(false);
        }
        KVCacheManager.ReleaseDecision change = decision.get();
        boolean isMessageTarget = change.messagesReleasedIndex() != null;
        String sessionId = sessionLineage.sessionId();
        String parentSessionId = sessionLineage.parentSessionId();
        CompletableFuture<Boolean> eviction = isMessageTarget
                ? llm.evictKvc(sessionId, parentSessionId, messageRange(change, previousWindow))
                : llm.evictKvc(sessionId, parentSessionId, toolsRange(change, previousWindow));
        return eviction.handle((evicted, throwable) -> {
            if (throwable != null || !Boolean.TRUE.equals(evicted)) {
                LOG.log(System.Logger.Level.WARNING,
                        "Ascend KV cache window diff eviction failed or returned false; "
                                + "continue normal inference. session_id={0} target={1} error={2}",
                        sessionId, isMessageTarget ? "messages" : "tools",
                        throwable == null ? "false" : throwable.toString());
                return false;
            }
            return true;
        });
    }

    private static com.openjiuwen.core.kvcache.AgentHint.KvCacheRange messageRange(
            KVCacheManager.ReleaseDecision change, ContextWindow previousWindow) {
        return new com.openjiuwen.core.kvcache.AgentHint.KvCacheRange(
                "messages",
                change.messagesReleasedIndex(),
                previousWindow == null ? null : previousWindow.getMessages().size(),
                change.toolsReleasedIndex(),
                previousWindow == null ? null : previousWindow.getTools().size(),
                change.toolsReleasedIndex() != null);
    }

    private static com.openjiuwen.core.kvcache.AgentHint.KvCacheRange toolsRange(
            KVCacheManager.ReleaseDecision change, ContextWindow previousWindow) {
        return new com.openjiuwen.core.kvcache.AgentHint.KvCacheRange(
                "tools",
                null,
                null,
                change.toolsReleasedIndex(),
                previousWindow == null ? null : previousWindow.getTools().size(),
                false);
    }

    /**
     * Build the identity kwargs that both encode the hint and drive runtime
     * accounting.
     *
     * <p>Mirrors Python's {@code build_invoke_kwargs}.</p>
     *
     * @param runtime resolved capabilities
     * @param llm current model
     * @param session current session, may be {@code null}
     * @param sessionLineage resolved lineage
     * @return kwargs map; empty when affinity inactive
     */
    public Map<String, Object> buildInvokeKwargs(
            KVCacheCallCapabilities runtime, Model llm, AgentSessionApi session,
            KVCacheMetadata.Lineage sessionLineage) {
        Map<String, Object> extraKwargs = new LinkedHashMap<>();
        if (!runtime.isActive() || llm == null) {
            return extraKwargs;
        }
        if (!sessionLineage.isPresent()) {
            return extraKwargs;
        }
        extraKwargs.putAll(llm.buildKvCacheAffinityInvokeKwargs(
                sessionLineage.sessionId(), sessionLineage.parentSessionId()));
        return extraKwargs;
    }

    /**
     * Acquire the inference lease right before the provider call.
     *
     * <p>Mirrors Python's {@code KVCacheModelHook.begin} gate: kwargs must
     * carry the same {@code session_id} as the lineage identity.</p>
     *
     * @param runtime resolved capabilities
     * @param session current session, may be {@code null}
     * @param invokeKwargs kwargs about to be sent
     * @return lease holder, empty when accounting is skipped
     */
    public CompletableFuture<KVCacheModelHook.RuntimeLease> beginInferenceLease(
            KVCacheCallCapabilities runtime, AgentSessionApi session, Map<String, Object> invokeKwargs) {
        if (!runtime.isActive() || session == null) {
            return CompletableFuture.completedFuture(KVCacheModelHook.RuntimeLease.EMPTY);
        }
        KVCacheTypes.KVCacheRuntimeProtocol kvRuntime = session.getKvCacheRuntime().orElse(null);
        if (kvRuntime == null) {
            return CompletableFuture.completedFuture(KVCacheModelHook.RuntimeLease.EMPTY);
        }
        KVCacheIdentity identity = session.getCacheIdentity();
        Object hintSessionId = invokeKwargs.get("session_id");
        if (hintSessionId == null || !String.valueOf(hintSessionId).equals(identity.cacheId())) {
            return CompletableFuture.completedFuture(KVCacheModelHook.RuntimeLease.EMPTY);
        }
        return kvRuntime.beginInference(identity, invokeKwargs.get("__openjiuwen_kvc_model"), null)
                .handle((lease, throwable) -> {
                    if (throwable != null) {
                        LOG.log(System.Logger.Level.WARNING,
                                "KVC inference admission failed; continue inference: {0}", throwable.toString());
                        return KVCacheModelHook.RuntimeLease.EMPTY;
                    }
                    return lease == null
                            ? KVCacheModelHook.RuntimeLease.EMPTY
                            : KVCacheModelHook.RuntimeLease.of(kvRuntime, lease);
                });
    }

    private void warnUnsupported(KVCacheCallCapabilities runtime) {
        if (runtime.isAffinityEnabled() && !runtime.isAffinitySupported() && !isAffinityWarningLogged) {
            Loggers.LLM.warning(
                    "KVCacheAffinityConfig.enable_kv_cache_affinity is True, but the current LLM does not "
                            + "support Ascend KV cache affinity; agent_hint will not take effect.");
            isAffinityWarningLogged = true;
        }
    }
}
