/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.multitenant.TenantContext;
import com.openjiuwen.core.runner.callback.AsyncCallbackFramework;
import com.openjiuwen.core.runner.callback.CallbackUtils;
import com.openjiuwen.core.runner.callback.DecoratorFramework;
import com.openjiuwen.core.runner.callback.SessionEvents;
import com.openjiuwen.core.session.config.Config;
import com.openjiuwen.core.session.interaction.SimpleAgentInteraction;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamWriterManager;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Public single-agent session facade.
 *
 */
public class AgentSession implements AgentSessionApi {

    private final String sessionId;
    private final Object card;
    private final com.openjiuwen.core.session.internal.AgentSession inner;
    private boolean preRunDone;
    private boolean postRunDone;
    private SimpleAgentInteraction interaction;
    private final boolean closeStreamOnPostRun;
    private final Map<String, Object> sourceMetadata;
    private TenantContext tenantContext;
    private final com.openjiuwen.core.kvcache.KVCacheTypes.KVCacheRuntimeProtocol kvCacheRuntime;
    private volatile boolean isKvcReleased;
    private String parentSessionIdBound;
    private String[] teamCacheScope;

    public AgentSession(String sessionId, Map<String, Object> envs, Object card,
                        StreamWriterManager streamWriterManager,
                        boolean closeStreamOnPostRun,
                        Map<String, Object> sourceMetadata) {
        this(sessionId, envs, card, streamWriterManager, closeStreamOnPostRun, sourceMetadata, null);
    }

    /**
     * Create a session with an optional shared KV cache runtime.
     *
     * <p>Mirrors Python's {@code Session(..., kv_cache_runtime=...)} and the
     * {@code parent_session_id} lineage slot.</p>
     *
     * @param sessionId session id
     * @param envs initial envs
     * @param card agent card
     * @param streamWriterManager stream writer manager
     * @param closeStreamOnPostRun whether postRun closes the stream
     * @param sourceMetadata owner metadata
     * @param kvCacheRuntime shared application KVC runtime, may be {@code null}
     * @since 0.1.16
     */
    public AgentSession(String sessionId, Map<String, Object> envs, Object card,
                        StreamWriterManager streamWriterManager,
                        boolean closeStreamOnPostRun,
                        Map<String, Object> sourceMetadata,
                        com.openjiuwen.core.kvcache.KVCacheTypes.KVCacheRuntimeProtocol kvCacheRuntime) {
        this.sessionId = sessionId == null ? UUID.randomUUID().toString() : sessionId;
        Config config = new Config();
        if (envs != null) {
            config.setEnvs(envs);
        }
        this.inner = new com.openjiuwen.core.session.internal.AgentSession(
                this.sessionId, config, card, streamWriterManager);
        this.card = card;
        this.closeStreamOnPostRun = closeStreamOnPostRun;
        this.sourceMetadata = sourceMetadata == null ? Map.of() : new LinkedHashMap<>(sourceMetadata);
        this.kvCacheRuntime = kvCacheRuntime;
    }

    public AgentSession(String sessionId, Map<String, Object> envs, Object card) {
        this(sessionId, envs, card, null, true, null);
    }

    public AgentSession() {
        this(null, null, null);
    }

    public static AgentSession createAgentSession(String sessionId, Map<String, Object> envs, Object card) {
        return new AgentSession(sessionId, envs, card);
    }

    @Override
    public String getSessionId() {
        return sessionId;
    }

    /**
     * Get the tenant context associated with this session.
     *
     * @return the tenant context, or null if not set
     * @since 0.1.7
     */
    @Override
    public TenantContext getTenantContext() {
        return tenantContext;
    }

    /**
     * Set the tenant context for this session (chain-style).
     *
     * @param ctx the tenant context to associate (nullable)
     * @return this session for chaining
     * @since 0.1.7
     */
    @Override
    public AgentSession withTenantContext(TenantContext ctx) {
        this.tenantContext = ctx;
        return this;
    }

    public Object getEnv(String key) {
        return inner.config().getEnv(key);
    }

    public Object getEnv(String key, Object defaultValue) {
        return inner.config().getEnv(key, defaultValue);
    }

    public Map<String, Object> getEnvs() {
        return inner.config().getEnvs();
    }

    public Object getAgentId() {
        return readCardProperty("getId", sessionId);
    }

    public Object getAgentName() {
        return readCardProperty("getName", "");
    }

    public Object getAgentDescription() {
        return readCardProperty("getDescription", "");
    }

    @Override
    public void updateState(Map<String, Object> data) {
        inner.state().updateGlobal(data);
    }

    @Override
    public Object getState(String key) {
        return getState((Object) key);
    }

    public Object getState(Object key) {
        return inner.state().getGlobal(key);
    }

    public Map<String, Object> dumpState() {
        return inner.state().dump();
    }

    @Override
    public void writeStream(Object data) {
        OutputSchema streamData = normalizeOutput(tagStreamPayload(data));
        triggerWriteStream(streamData);
        inner.streamWriterManager().getOutputWriter().write(streamData);
    }

    public void writeCustomStream(Object data) {
        Object streamData = tagStreamPayload(data);
        triggerWriteStream(streamData);
        inner.streamWriterManager().getCustomWriter().write(streamData);
    }

    @Override
    public Iterator<Object> streamIterator() {
        return inner.streamWriterManager().streamIterator();
    }

    @Override
    public void closeStream() {
        inner.streamWriterManager().streamEmitter().close();
        DecoratorFramework callbackFramework;
        try {
            callbackFramework = CallbackUtils.getCallbackFramework();
        } catch (IllegalStateException ignored) {
            callbackFramework = null;
        }
        if (callbackFramework instanceof AsyncCallbackFramework framework) {
            framework.unregisterEvent(sessionId + "write_stream");
        }
    }

    @Override
    public AgentSession preRun(Map<String, Object> kwargs) {
        if (preRunDone) {
            return this;
        }
        Map<String, Object> callbackKwargs = new LinkedHashMap<>();
        callbackKwargs.put("session_id", getSessionId());
        callbackKwargs.put("card", card);
        callbackKwargs.put("session", this);
        CallbackUtils.trigger(SessionEvents.AGENT_SESSION_CREATED, callbackKwargs);
        Object inputs = kwargs == null ? null : kwargs.get("inputs");
        if (inner.checkpointer() != null) {
            inner.checkpointer().preAgentExecute(inner, inputs);
        }
        preRunDone = true;
        return this;
    }

    @Override
    public void markPreRunDone() {
        this.preRunDone = true;
    }

    @Override
    public void markPostRunDone() {
        this.postRunDone = true;
    }

    @Override
    public boolean isPreRunDone() {
        return preRunDone;
    }

    @Override
    public boolean isPostRunDone() {
        return postRunDone;
    }

    @Override
    public void resetPostRunState() {
        this.postRunDone = false;
    }

    public AgentSession postRun() {
        if (postRunDone) {
            return this;
        }
        if (closeStreamOnPostRun) {
            closeStream();
        }
        commit();
        postRunDone = true;
        return this;
    }

    @Override
    public void commit() {
        if (inner.checkpointer() != null) {
            inner.checkpointer().postAgentExecute(inner);
        }
    }

    public WorkflowSession createWorkflowSession() {
        return new WorkflowSession(inner, getSessionId(), getEnvs());
    }

    public Object interact(Object value) {
        if (interaction == null) {
            interaction = new SimpleAgentInteraction(inner);
        }
        return interaction.waitUserInputs(value);
    }

    public com.openjiuwen.core.session.internal.AgentSession getInner() {
        return inner;
    }

    /**
     * Bind the Team scope used to derive this member session's cache id.
     *
     * <p>Mirrors Python's {@code Session.set_team_cache_scope}.</p>
     *
     * @param teamId team id
     * @param agentId member agent id
     * @since 0.1.16
     */
    public void setTeamCacheScope(String teamId, String agentId) {
        if (teamId == null || teamId.isBlank() || agentId == null || agentId.isBlank()) {
            return;
        }
        this.teamCacheScope = new String[] {teamId, agentId};
    }

    /**
     * Bind this child to one product session before it starts running.
     *
     * <p>Mirrors Python's {@code Session.bind_parent_session_id}: lineage is
     * immutable once set; rebinding the same parent is idempotent, moving a
     * live child to another parent is an ownership error.</p>
     *
     * @param parentSessionId product session id
     * @since 0.1.16
     */
    public void bindParentSessionId(String parentSessionId) {
        String normalized = parentSessionId == null ? "" : parentSessionId.strip();
        if (normalized.isEmpty()) {
            return;
        }
        if (parentSessionIdBound == null) {
            parentSessionIdBound = normalized;
            return;
        }
        if (!parentSessionIdBound.equals(normalized)) {
            throw new IllegalStateException(
                    "Session parent is already bound to " + parentSessionIdBound
                            + "; cannot rebind to " + normalized);
        }
    }

    @Override
    public com.openjiuwen.core.kvcache.KVCacheIdentity getCacheIdentity() {
        if (teamCacheScope != null) {
            String cacheId = com.openjiuwen.core.kvcache.KVCacheMetadata.teamMemberCacheIdentity(
                    sessionId, teamCacheScope[0], teamCacheScope[1]);
            return new com.openjiuwen.core.kvcache.KVCacheIdentity(
                    cacheId, parentSessionIdBound == null ? sessionId : parentSessionIdBound);
        }
        Object sourceAgentId = sourceMetadata.get("source_agent_id");
        Object sourceTeamId = sourceMetadata.get("source_team_id");
        if (sourceTeamId instanceof String teamId && !teamId.isBlank()
                && sourceAgentId instanceof String agentId && !agentId.isBlank()) {
            String cacheId = com.openjiuwen.core.kvcache.KVCacheMetadata.teamMemberCacheIdentity(
                    sessionId, teamId, agentId);
            return new com.openjiuwen.core.kvcache.KVCacheIdentity(cacheId, sessionId);
        }
        String cacheId = envValue(com.openjiuwen.core.kvcache.KVCacheMetadata.KV_CACHE_AFFINITY_SESSION_ID_ENV);
        String parentCacheId =
                envValue(com.openjiuwen.core.kvcache.KVCacheMetadata.KV_CACHE_AFFINITY_PARENT_SESSION_ID_ENV);
        boolean hasCacheId = !cacheId.isBlank();
        boolean hasParentCacheId = !parentCacheId.isBlank();
        if (hasCacheId || hasParentCacheId || parentSessionIdBound != null) {
            String resolvedCacheId = hasCacheId ? cacheId : sessionId;
            String resolvedParent = hasParentCacheId
                    ? parentCacheId
                    : parentSessionIdBound == null ? resolvedCacheId : parentSessionIdBound;
            return new com.openjiuwen.core.kvcache.KVCacheIdentity(resolvedCacheId, resolvedParent);
        }
        return new com.openjiuwen.core.kvcache.KVCacheIdentity(sessionId, sessionId);
    }

    private String envValue(String key) {
        Object value = inner.config().getEnv(key);
        return value == null ? "" : String.valueOf(value).strip();
    }

    @Override
    public java.util.Optional<com.openjiuwen.core.kvcache.KVCacheTypes.KVCacheRuntimeProtocol> getKvCacheRuntime() {
        return isKvcReleased
                ? java.util.Optional.empty()
                : java.util.Optional.ofNullable(kvCacheRuntime);
    }

    @Override
    public java.util.concurrent.CompletableFuture<Boolean> prepareKvc() {
        return invokeKvc("prepare");
    }

    @Override
    public java.util.concurrent.CompletableFuture<Boolean> suspendKvc() {
        return invokeKvc("suspend");
    }

    @Override
    public java.util.concurrent.CompletableFuture<Boolean> releaseKvc() {
        if (isKvcReleased) {
            return java.util.concurrent.CompletableFuture.completedFuture(false);
        }
        isKvcReleased = true;
        return invokeKvc("release");
    }

    private java.util.concurrent.CompletableFuture<Boolean> invokeKvc(String operation) {
        com.openjiuwen.core.kvcache.KVCacheTypes.KVCacheRuntimeProtocol runtime;
        if ("release".equals(operation)) {
            runtime = kvCacheRuntime;
        } else {
            runtime = getKvCacheRuntime().orElse(null);
        }
        if (runtime == null) {
            return java.util.concurrent.CompletableFuture.completedFuture(false);
        }
        com.openjiuwen.core.kvcache.KVCacheIdentity identity = getCacheIdentity();
        java.util.concurrent.CompletableFuture<Boolean> action = switch (operation) {
            case "prepare" -> runtime.prepare(identity);
            case "suspend" -> runtime.suspend(identity);
            default -> runtime.release(identity);
        };
        return action.handle((result, throwable) -> {
            if (throwable != null) {
                Loggers.SESSION.warning("KVC {0} failed; continue normal flow: {1}",
                        operation, throwable.toString());
                return false;
            }
            return Boolean.TRUE.equals(result);
        });
    }

    private Object tagStreamPayload(Object data) {
        if (sourceMetadata.isEmpty()) {
            return data;
        }
        if (data instanceof Map<?, ?> map) {
            LinkedHashMap<String, Object> tagged = mapToStringKeyMap(map);
            tagged.putAll(sourceMetadata);
            return tagged;
        }
        if (data instanceof OutputSchema outputSchema) {
            Object payload = outputSchema.getPayload();
            LinkedHashMap<String, Object> taggedPayload;
            if (payload instanceof Map<?, ?> map) {
                taggedPayload = mapToStringKeyMap(map);
            } else {
                taggedPayload = new LinkedHashMap<>();
                taggedPayload.put("value", payload);
            }
            taggedPayload.putAll(sourceMetadata);
            return new OutputSchema(outputSchema.getType(), outputSchema.getIndex(), taggedPayload);
        }
        return data;
    }

    private static OutputSchema normalizeOutput(Object data) {
        if (data instanceof OutputSchema outputSchema) {
            return outputSchema;
        }
        if (data instanceof Map<?, ?> map) {
            if (map.keySet().containsAll(java.util.Set.of("type", "index", "payload"))) {
                return new OutputSchema(
                        dataToString(map.get("type")),
                        dataToInt(map.get("index")),
                        map.get("payload")
                );
            }
            return new OutputSchema("message", 0, mapToStringKeyMap(map));
        }
        return new OutputSchema("message", 0, data);
    }

    private void triggerWriteStream(Object streamData) {
        LinkedHashMap<String, Object> kwargs = new LinkedHashMap<>();
        kwargs.put("data", streamData);
        CallbackUtils.trigger(sessionId + "write_stream", kwargs);
    }

    private static LinkedHashMap<String, Object> mapToStringKeyMap(Map<?, ?> map) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private static String dataToString(Object data) {
        return data == null ? null : String.valueOf(data);
    }

    private static int dataToInt(Object data) {
        if (data instanceof Number number) {
            return number.intValue();
        }
        if (data != null) {
            return Integer.parseInt(String.valueOf(data));
        }
        return 0;
    }

    private Object readCardProperty(String getterName, Object defaultValue) {
        if (card == null) {
            return defaultValue;
        }
        try {
            Object value = card.getClass().getMethod(getterName).invoke(card);
            return value == null ? defaultValue : value;
        } catch (ReflectiveOperationException ignored) {
            return defaultValue;
        }
    }
}
