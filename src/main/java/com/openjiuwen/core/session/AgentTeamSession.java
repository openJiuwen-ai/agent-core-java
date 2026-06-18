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

    public AgentTeamSession(String sessionId, Map<String, Object> envs, String teamId) {
        this.sessionId = sessionId == null ? UUID.randomUUID().toString() : sessionId;
        this.teamId = teamId == null ? "agent_team" : teamId;
        Config config = new Config();
        if (envs != null) {
            config.setEnvs(envs);
        }
        this.inner = new com.openjiuwen.core.session.internal.AgentTeamSession(this.sessionId, this.teamId, config);
    }

    public AgentTeamSession() {
        this(null, null, "agent_team");
    }

    public static AgentTeamSession createAgentTeamSession(String sessionId, Map<String, Object> envs, String teamId) {
        return new AgentTeamSession(sessionId, envs, teamId);
    }

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

    public void writeStream(Object data) {
        inner.streamWriterManager().getOutputWriter().write(normalizeOutput(tagTeamPayload(data)));
    }

    public void writeCustomStream(Object data) {
        inner.streamWriterManager().getCustomWriter().write(tagTeamPayload(data));
    }

    public Iterator<Object> streamIterator() {
        return inner.streamWriterManager().streamIterator();
    }

    public void closeStream() {
        inner.streamWriterManager().streamEmitter().close();
    }

    public AgentTeamSession preRun(Map<String, Object> kwargs) {
        if (preRunDone) {
            return this;
        }
        Object inputs = kwargs == null ? null : kwargs.get("inputs");
        if (inner.checkpointer() instanceof com.openjiuwen.core.session.checkpointer.Checkpointer checkpointer) {
            checkpointer.preAgentTeamExecute(inner, inputs);
        }
        preRunDone = true;
        return this;
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

    public void commit() {
        if (inner.checkpointer() instanceof com.openjiuwen.core.session.checkpointer.Checkpointer checkpointer) {
            checkpointer.postAgentTeamExecute(inner);
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
        Object resolvedCard = card == null ? new SimpleAgentCard(agentId == null ? "team_agent" : agentId) : card;
        LinkedHashMap<String, Object> sourceMetadata = new LinkedHashMap<>();
        sourceMetadata.put("source_agent_id", readCardId(resolvedCard));
        sourceMetadata.put("source_team_id", teamId);
        return new AgentSession(
                sessionId,
                getEnvs(),
                resolvedCard,
                shareStreamWriter ? inner.streamWriterManager() : null,
                false,
                sourceMetadata
        );
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
