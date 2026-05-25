/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.state.agent_state;

import java.util.HashMap;
import java.util.Map;

/**
 * Collection of agent states for checkpointing.
 * <p>
 * Mirrors Python's StateCollection in agent_state module.
 */
public class StateCollection {

    private Map<String, Object> states = new HashMap<>();
    private String sessionId;
    private String agentId;

    public StateCollection() {
    }

    public StateCollection(String sessionId, String agentId) {
        this.sessionId = sessionId;
        this.agentId = agentId;
    }

    public void putState(String key, Object value) {
        states.put(key, value);
    }

    public Object getState(String key) {
        return states.get(key);
    }

    public Map<String, Object> getAllStates() {
        return new HashMap<>(states);
    }

    public void clear() {
        states.clear();
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }
}