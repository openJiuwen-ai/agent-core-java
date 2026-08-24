/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session;

import com.openjiuwen.core.multitenant.TenantContext;
import com.openjiuwen.core.session.stream.OutputSchema;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Public agent-group session facade. Same product layer as {@link AgentSession}.
 */
public class AgentGroupSession implements AgentSessionApi {

    private final AgentSession inner;
    private String teamId;
    private final ThreadLocal<String> currentAgentId = new ThreadLocal<>();
    private TenantContext tenantContext;

    public AgentGroupSession(String sessionId, Map<String, Object> envs) {
        this.inner = new AgentSession(
                sessionId == null || sessionId.isBlank() ? UUID.randomUUID().toString() : sessionId,
                envs,
                null);
    }

    public AgentGroupSession(String sessionId) {
        this(sessionId, null);
    }

    public AgentGroupSession() {
        this(null, null);
    }

    public static AgentGroupSession create(String sessionId, Map<String, Object> envs) {
        return new AgentGroupSession(sessionId, envs);
    }

    public com.openjiuwen.core.session.internal.AgentSession getInner() {
        return inner.getInner();
    }

    @Override
    public AgentGroupSession preRun(Map<String, Object> kwargs) {
        inner.preRun(kwargs);
        return this;
    }

    @Override
    public void closeStream() {
        inner.closeStream();
    }

    @Override
    public void commit() {
        inner.commit();
    }

    @Override
    public TenantContext getTenantContext() {
        return tenantContext;
    }

    @Override
    public AgentGroupSession withTenantContext(TenantContext ctx) {
        this.tenantContext = ctx;
        inner.withTenantContext(ctx);
        return this;
    }

    public String getTeamId() {
        return teamId;
    }

    public void setTeamId(String teamId) {
        this.teamId = teamId;
    }

    public String getCurrentAgentId() {
        return currentAgentId.get();
    }

    public void setCurrentAgentId(String currentAgentId) {
        if (currentAgentId == null) {
            this.currentAgentId.remove();
        } else {
            this.currentAgentId.set(currentAgentId);
        }
    }

    public Map<String, Object> getEnvs() {
        return inner.getEnvs();
    }

    @Override
    public String getSessionId() {
        return inner.getSessionId();
    }

    @Override
    public Object getState(String key) {
        return inner.getState(key);
    }

    @Override
    public void updateState(Map<String, Object> data) {
        inner.updateState(data);
    }

    @Override
    public Iterator<Object> streamIterator() {
        return inner.streamIterator();
    }

    @Override
    public void writeStream(Object data) {
        inner.writeStream(enrichWithTeamMetadata(data));
    }

    @SuppressWarnings("unchecked")
    private Object enrichWithTeamMetadata(Object data) {
        String agentId = currentAgentId.get();
        if (data instanceof Map) {
            Map<String, Object> map = new LinkedHashMap<>((Map<String, Object>) data);
            boolean hasP2pPayload = map.containsKey("p2p");
            if (teamId != null && !map.containsKey("source_team_id")) {
                map.put("source_team_id", teamId);
            }
            if (!hasP2pPayload && agentId != null && !map.containsKey("source_agent_id")) {
                map.put("source_agent_id", agentId);
            }
            return map;
        }
        if (data instanceof OutputSchema schema) {
            Object payload = schema.getPayload();
            if (payload instanceof Map) {
                Map<String, Object> map = new LinkedHashMap<>((Map<String, Object>) payload);
                boolean hasP2pPayload = map.containsKey("p2p");
                if (teamId != null && !map.containsKey("source_team_id")) {
                    map.put("source_team_id", teamId);
                }
                if (!hasP2pPayload && agentId != null && !map.containsKey("source_agent_id")) {
                    map.put("source_agent_id", agentId);
                }
                schema.setPayload(map);
            }
            return schema;
        }
        return data;
    }
}
