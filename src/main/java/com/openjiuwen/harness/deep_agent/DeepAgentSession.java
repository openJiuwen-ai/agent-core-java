/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.deep_agent;

import com.openjiuwen.core.multitenant.TenantContext;
import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * DeepAgent product session. Delegates to the official {@link AgentSession} facade
 * so state, stream, and lifecycle share one kernel.
 *
 * @since 0.1.14
 */
public class DeepAgentSession implements AgentSessionApi {

    private final AgentSession facade;
    private Map<String, Object> envs;

    public DeepAgentSession(String sessionId) {
        this.facade = new AgentSession(sessionId, null, null);
    }

    public DeepAgentSession(String sessionId, Object config, AgentCard card) {
        this.facade = new AgentSession(sessionId, null, card);
    }

    public DeepAgentSession(String sessionId, Object config, AgentCard card, List<StreamMode> streamModes) {
        this.facade = new AgentSession(sessionId, null, card);
    }

    public DeepAgentSession(String sessionId, Object config, Object checkpointer, AgentCard card) {
        this.facade = new AgentSession(sessionId, null, card);
    }

    @Override
    public String getSessionId() {
        return facade.getSessionId();
    }

    @Override
    public Object getState(String key) {
        return facade.getState(key);
    }

    @Override
    public void updateState(Map<String, Object> data) {
        facade.updateState(data);
    }

    @Override
    public void writeStream(Object data) {
        facade.writeStream(data);
    }

    @Override
    public Iterator<Object> streamIterator() {
        return facade.streamIterator();
    }

    @Override
    public TenantContext getTenantContext() {
        return facade.getTenantContext();
    }

    @Override
    public DeepAgentSession withTenantContext(TenantContext ctx) {
        facade.withTenantContext(ctx);
        return this;
    }

    @Override
    public DeepAgentSession preRun(Map<String, Object> inputs) {
        facade.preRun(inputs == null ? Map.of() : Map.of("inputs", inputs));
        return this;
    }

    public void postRun() {
        facade.postRun();
    }

    @Override
    public void closeStream() {
        facade.closeStream();
    }

    @Override
    public void commit() {
        facade.commit();
    }

    public com.openjiuwen.core.session.internal.AgentSession getInner() {
        return facade.getInner();
    }

    public Map<String, Object> getEnvs() {
        return envs != null ? envs : facade.getEnvs();
    }

    public void setEnvs(Map<String, Object> envs) {
        this.envs = envs;
    }
}
