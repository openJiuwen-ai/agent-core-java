/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session;

import com.openjiuwen.core.multitenant.TenantContext;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamWriter;

import java.util.LinkedHashMap;
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
 * 
 * @since 0.1.7
 */
public class AgentGroupSessionApi extends AgentSessionApi {
    private String teamId;
    private String currentAgentId;

    /**
     * AtomicInteger.
     * 
     * @since 0.1.7
     */
    private final AtomicInteger chunkIndex = new AtomicInteger(0);

    /**
     * Create a new agent group session.
     * 
     * @param sessionId session ID (nullable, auto-generated if absent)
     * @param envs environment variables (nullable)
     * @since 0.1.7
     */
    public AgentGroupSessionApi(String sessionId, Map<String, Object> envs) {
        super(sessionId, envs, null);
    }

    /**
     * AgentGroupSessionApi.
     * 
     * @param sessionId sessionId
     * @since 0.1.7
     */
    public AgentGroupSessionApi(String sessionId) {
        this(sessionId, null);
    }

    @Override
    public AgentGroupSessionApi withTenantContext(TenantContext ctx) {
        super.withTenantContext(ctx);
        return this;
    }

    /**
     * AgentGroupSessionApi.
     * 
     * @since 0.1.7
     */
    public AgentGroupSessionApi() {
        this(null, null);
    }

    /**
     * Factory method to create an agent group session.
     * 
     * @param sessionId session ID (nullable, auto-generated if absent)
     * @param envs environment variables (nullable)
     * @return a new AgentGroupSessionApi
     * @since 0.1.7
     */
    public static AgentGroupSessionApi create(String sessionId, Map<String, Object> envs) {
        return new AgentGroupSessionApi(sessionId, envs);
    }

    /**
     * getTeamId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getTeamId() {
        return teamId;
    }

    /**
     * setTeamId.
     * 
     * @param teamId teamId
     * @since 0.1.7
     */
    public void setTeamId(String teamId) {
        this.teamId = teamId;
    }

    /**
     * getCurrentAgentId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getCurrentAgentId() {
        return currentAgentId;
    }

    /**
     * setCurrentAgentId.
     * 
     * @param currentAgentId currentAgentId
     * @since 0.1.7
     */
    public void setCurrentAgentId(String currentAgentId) {
        this.currentAgentId = currentAgentId;
    }

    /**
     * writeStream.
     * 
     * @param data data
     * @since 0.1.7
     */
    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void writeStream(Object data) {
        Object enrichedData = enrichWithTeamMetadata(data);
        StreamWriter<?> writer = getInner().streamWriterManager().getOutputWriter();
        if (writer != null) {
            if (enrichedData instanceof OutputSchema) {
                writer.write(enrichedData);
            } else {
                OutputSchema chunk = new OutputSchema("message", 0, enrichedData);
                writer.write(chunk);
            }
        }
    }

    @SuppressWarnings("unchecked")
    /**
     * enrichWithTeamMetadata.
     * 
     * @param data data
     * @return the result
     * @since 0.1.7
     */
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
