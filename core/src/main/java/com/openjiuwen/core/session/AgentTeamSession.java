/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session;

import com.openjiuwen.core.session.config.Config;
import com.openjiuwen.core.session.stream.OutputSchema;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Public agent-team session facade.
 *
 * <p>Mirrors Python's {@code Session} in
 * {@code openjiuwen/core/session/agent_team.py}.</p>
 */
public class AgentTeamSession implements AgentSessionApi {

    private final String sessionId;
    private final String teamId;
    private final com.openjiuwen.core.session.internal.AgentTeamSession inner;
    private boolean preRunDone;
    private boolean postRunDone;
    private final com.openjiuwen.core.kvcache.KVCacheTypes.KVCacheRuntimeProtocol kvCacheRuntime;
    private volatile boolean isKvcReleased;

    public AgentTeamSession(String sessionId, Map<String, Object> envs, String teamId) {
        this(sessionId, envs, teamId, null);
    }

    /**
     * Create a Team session with an optional shared KV cache runtime.
     *
     * <p>Mirrors Python's {@code Session(..., kv_cache_runtime=...)} in
     * {@code openjiuwen/core/session/agent_team.py}.</p>
     *
     * @param sessionId team session id
     * @param envs initial envs
     * @param teamId team id
     * @param kvCacheRuntime shared application KVC runtime, may be {@code null}
     * @since 0.1.16
     */
    public AgentTeamSession(String sessionId, Map<String, Object> envs, String teamId,
            com.openjiuwen.core.kvcache.KVCacheTypes.KVCacheRuntimeProtocol kvCacheRuntime) {
        this.sessionId = sessionId == null ? UUID.randomUUID().toString() : sessionId;
        this.teamId = teamId == null ? "agent_team" : teamId;
        Config config = new Config();
        if (envs != null) {
            config.setEnvs(envs);
        }
        this.inner = new com.openjiuwen.core.session.internal.AgentTeamSession(this.sessionId, this.teamId, config);
        this.kvCacheRuntime = kvCacheRuntime;
    }

    public AgentTeamSession() {
        this(null, null, "agent_team");
    }

    /**
     * Create an AgentTeam session factory alias with a shared KVC runtime.
     *
     * <p>Mirrors Python's {@code create_agent_team_session}.</p>
     *
     * @param sessionId team session id
     * @param envs initial envs
     * @param teamId team id
     * @param kvCacheRuntime shared application KVC runtime
     * @return the team session
     * @since 0.1.16
     */
    public static AgentTeamSession createAgentTeamSession(String sessionId, Map<String, Object> envs, String teamId,
            com.openjiuwen.core.kvcache.KVCacheTypes.KVCacheRuntimeProtocol kvCacheRuntime) {
        return new AgentTeamSession(sessionId, envs, teamId, kvCacheRuntime);
    }

    public static AgentTeamSession createAgentTeamSession(String sessionId, Map<String, Object> envs, String teamId) {
        return new AgentTeamSession(sessionId, envs, teamId);
    }

    @Override
    public String getSessionId() {
        return sessionId;
    }

    public Object getEnv(String key, Object defaultValue) {
        return inner.config().getEnv(key, defaultValue);
    }

    public String getTeamId() {
        return teamId;
    }

    public Map<String, Object> getEnvs() {
        return inner.config().getEnvs();
    }

    @Override
    public void updateState(Map<String, Object> data) {
        inner.state().updateGlobal(data);
    }

    public Object getState(Object key) {
        return inner.state().getGlobal(key);
    }

    @Override
    public Object getState(String key) {
        return getState((Object) key);
    }

    public Map<String, Object> dumpState() {
        return inner.state().dump();
    }

    @Override
    public void writeStream(Object data) {
        inner.streamWriterManager().getOutputWriter().write(normalizeOutput(tagTeamPayload(data)));
    }

    public void writeCustomStream(Object data) {
        inner.streamWriterManager().getCustomWriter().write(tagTeamPayload(data));
    }

    @Override
    public Iterator<Object> streamIterator() {
        return inner.streamWriterManager().streamIterator();
    }

    @Override
    public void closeStream() {
        inner.streamWriterManager().streamEmitter().close();
    }

    @Override
    public AgentTeamSession preRun(Map<String, Object> kwargs) {
        if (preRunDone) {
            return this;
        }
        Object inputs = kwargs == null ? null : kwargs.get("inputs");
        if (inner.checkpointer() != null) {
            inner.checkpointer().preAgentTeamExecute(inner, inputs);
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

    public AgentTeamSession postRun() {
        if (postRunDone) {
            return this;
        }
        closeStream();
        commit();
        postRunDone = true;
        return this;
    }

    @Override
    public void commit() {
        if (inner.checkpointer() != null) {
            inner.checkpointer().postAgentTeamExecute(inner);
        }
    }

    public void flushCheckpoint() {
        commit();
    }

    public AgentSession createAgentSession() {
        return createAgentSession(null, null, true);
    }

    public AgentSession createAgentSession(Object card, String agentId) {
        return createAgentSession(card, agentId, true);
    }

    public AgentSession createAgentSession(Object card, String agentId, boolean shareStreamWriter) {
        return createAgentSession(card, agentId, shareStreamWriter, null);
    }

    /**
     * Create a member child session sharing this Team session's KVC runtime.
     *
     * <p>Mirrors Python's {@code create_agent_session} on the Team Session:
     * the child receives the shared runtime, source metadata, and a
     * {@code team:{session}:team:{team}:member:{agent}} cache scope so member
     * cache keys cannot collide.</p>
     *
     * @param card member agent card
     * @param agentId member agent id
     * @param isShareStreamWriter whether to share the team stream writer
     * @param memberName optional member name recorded in metadata
     * @return the child session
     * @since 0.1.16
     */
    public AgentSession createAgentSession(Object card, String agentId, boolean isShareStreamWriter,
            String memberName) {
        Object resolvedCard = card == null ? new SimpleAgentCard(agentId == null ? "team_agent" : agentId) : card;
        LinkedHashMap<String, Object> sourceMetadata = new LinkedHashMap<>();
        sourceMetadata.put("source_agent_id", readCardId(resolvedCard));
        sourceMetadata.put("source_team_id", teamId);
        if (memberName != null && !memberName.isBlank()) {
            sourceMetadata.put("source_member_name", memberName);
        }
        AgentSession child = new AgentSession(
                sessionId,
                getEnvs(),
                resolvedCard,
                isShareStreamWriter ? inner.streamWriterManager() : null,
                false,
                sourceMetadata,
                getKvCacheRuntime().orElse(null)
        );
        child.setTeamCacheScope(teamId, readCardId(resolvedCard));
        return child;
    }

    /**
     * Return the root identity shared by this Team session.
     *
     * <p>Mirrors Python's {@code agent_team.Session.get_cache_identity}: the
     * team session is its own cache root.</p>
     *
     * @return self-pointing lineage identity
     * @since 0.1.16
     */
    @Override
    public com.openjiuwen.core.kvcache.KVCacheIdentity getCacheIdentity() {
        return new com.openjiuwen.core.kvcache.KVCacheIdentity(sessionId, sessionId);
    }

    /**
     * Return the process-local KVC runtime while this Team session is live.
     *
     * @return runtime, empty when released or never wired
     * @since 0.1.16
     */
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
        com.openjiuwen.core.kvcache.KVCacheTypes.KVCacheRuntimeProtocol runtime =
                "release".equals(operation) ? kvCacheRuntime : getKvCacheRuntime().orElse(null);
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
                com.openjiuwen.core.common.logging.Loggers.SESSION.warning(
                        "Team KVC {0} failed; continue normal flow: {1}", operation, throwable.toString());
                return false;
            }
            return Boolean.TRUE.equals(result);
        });
    }

    public com.openjiuwen.core.session.internal.AgentTeamSession getInner() {
        return inner;
    }

    private Object tagTeamPayload(Object data) {
        if (data instanceof Map<?, ?> map) {
            LinkedHashMap<String, Object> tagged = mapToStringKeyMap(map);
            tagged.put("source_team_id", teamId);
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
            taggedPayload.put("source_team_id", teamId);
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

    private static String readCardId(Object card) {
        try {
            Object value = card.getClass().getMethod("getId").invoke(card);
            return value == null ? "team_agent" : String.valueOf(value);
        } catch (ReflectiveOperationException ignored) {
            return "team_agent";
        }
    }

    private static final class SimpleAgentCard {
        private final String id;

        private SimpleAgentCard(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return id;
        }
    }
}
