/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.deep_agent;

import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.internal.AgentSession;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DeepAgent-specific session wrapper that implements AgentSessionApi
 * and provides the additional methods needed by DeepAgent.
 *
 * @since 0.1.14
 */
public class DeepAgentSession implements AgentSessionApi {

    private final AgentSession inner;
    private Map<String, Object> envs;
    private Map<String, Object> preRunData;

    public DeepAgentSession(String sessionId) {
        this.inner = new AgentSession(sessionId);
    }

    public DeepAgentSession(String sessionId, Object config, AgentCard card) {
        this.inner = new AgentSession(sessionId, null, card, null);
    }

    public DeepAgentSession(String sessionId, Object config, AgentCard card, List<StreamMode> streamModes) {
        this.inner = new AgentSession(sessionId, null, card, null);
    }

    public DeepAgentSession(String sessionId, Object config, Object checkpointer, AgentCard card) {
        this.inner = new AgentSession(sessionId, null, null, card, null);
    }

    // --- AgentSessionApi interface ---

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
    public void writeStream(Object data) {
        com.openjiuwen.core.session.stream.OutputSchema streamData =
                data instanceof com.openjiuwen.core.session.stream.OutputSchema os ? os
                        : new com.openjiuwen.core.session.stream.OutputSchema("message", 0, data);
        inner.streamWriterManager().getOutputWriter().write(streamData);
    }

    @Override
    public Iterator<Object> streamIterator() {
        return inner.streamWriterManager().streamIterator();
    }

    // --- DeepAgent-specific methods ---

    public void preRun(Map<String, Object> inputs) {
        this.preRunData = inputs != null ? new LinkedHashMap<>(inputs) : new LinkedHashMap<>();
    }

    public void postRun() {
        this.preRunData = null;
    }

    public AgentSession getInner() {
        return inner;
    }

    public Map<String, Object> getEnvs() {
        return envs;
    }

    public void setEnvs(Map<String, Object> envs) {
        this.envs = envs;
    }
}
