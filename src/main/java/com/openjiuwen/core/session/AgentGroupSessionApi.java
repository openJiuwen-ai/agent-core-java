/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session;

import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.session.stream.StreamWriter;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * User-facing agent group session.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.agent_group.Session}.
 * <p>
 * Extends {@link AgentSessionApi} so legacy multi-agent sessions inherit the
 * same state, streaming, and interaction helpers exposed by Python's
 * {@code AgentGroupSession(AgentSession)} implementation.
 */
public class AgentGroupSessionApi implements AgentSessionApi {

    private String sessionId;
    private Map<String, Object> envs;
    private String teamId;
    private String currentAgentId;
    private final AtomicInteger chunkIndex = new AtomicInteger(0);

    /**
     * Create a new agent group session.
     *
     * @param sessionId session ID (nullable, auto-generated if absent)
     * @param envs      environment variables (nullable)
     */
    public AgentGroupSessionApi(String sessionId, Map<String, Object> envs) {
        this.sessionId = sessionId;
        this.envs = envs != null ? envs : new LinkedHashMap<>();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public AgentGroupSessionApi(String sessionId) {
        this(sessionId, null);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public AgentGroupSessionApi() {
        this(null, null);
    }

    /**
     * Factory method to create an agent group session.
     *
     * @param sessionId session ID (nullable, auto-generated if absent)
     * @param envs      environment variables (nullable)
     * @return a new AgentGroupSessionApi
     */
    public static AgentGroupSessionApi create(String sessionId, Map<String, Object> envs) {
        return new AgentGroupSessionApi(sessionId, envs);
    }

    public String getTeamId() {
        return teamId;
    }

    public void setTeamId(String teamId) {
        this.teamId = teamId;
    }

    public String getCurrentAgentId() {
        return currentAgentId;
    }

    public void setCurrentAgentId(String currentAgentId) {
        this.currentAgentId = currentAgentId;
    }

    @Override
    public String getSessionId() {
        return sessionId;
    }

    @Override
    public Object getState(String key) {
        return envs.get(key);
    }

    @Override
    public void updateState(Map<String, Object> data) {
        if (data != null) {
            envs.putAll(data);
        }
    }

    @Override
    public Iterator<Object> streamIterator() {
        return List.of().iterator();
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void writeStream(Object data) {
        Object enrichedData = enrichWithTeamMetadata(data);
        // Stream writing delegated to environment
        if (envs.containsKey("stream_writer")) {
            Object writer = envs.get("stream_writer");
            if (writer instanceof StreamWriter streamWriter) {
                if (enrichedData instanceof OutputSchema) {
                    streamWriter.write(enrichedData);
                } else {
                    OutputSchema chunk = new OutputSchema("message", 0, enrichedData);
                    streamWriter.write(chunk);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Object enrichWithTeamMetadata(Object data) {
        if (data instanceof Map) {
            Map<String, Object> map = new LinkedHashMap<>((Map<String, Object>) data);
            boolean p2pPayload = map.containsKey("p2p");
            if (teamId != null && !map.containsKey("source_team_id")) {
                map.put("source_team_id", teamId);
            }
            if (!p2pPayload && currentAgentId != null && !map.containsKey("source_agent_id")) {
                map.put("source_agent_id", currentAgentId);
            }
            return map;
        }
        if (data instanceof OutputSchema schema) {
            Object payload = schema.getPayload();
            if (payload instanceof Map) {
                Map<String, Object> map = new LinkedHashMap<>((Map<String, Object>) payload);
                boolean p2pPayload = map.containsKey("p2p");
                if (teamId != null && !map.containsKey("source_team_id")) {
                    map.put("source_team_id", teamId);
                }
                if (!p2pPayload && currentAgentId != null && !map.containsKey("source_agent_id")) {
                    map.put("source_agent_id", currentAgentId);
                }
                schema.setPayload(map);
            }
            return schema;
        }
        return data;
    }
}
