/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.state.agent_state;

import com.openjiuwen.core.session.state.InMemoryStateLike;
import com.openjiuwen.core.session.state.State;

import java.util.HashMap;
import java.util.Map;

/**
 * Collection of agent states for checkpointing.
 * <p>
 * Mirrors Python's StateCollection in agent_state module.
 * <p>
 * Extends State interface for compatibility with session state management.
 */
public class StateCollection implements State {

    private InMemoryStateLike globalState;
    private InMemoryStateLike agentState;
    private Map<String, Object> traceState;
    private String sessionId;
    private String agentId;

    public StateCollection() {
        this.globalState = new InMemoryStateLike();
        this.agentState = new InMemoryStateLike();
        this.traceState = new HashMap<>();
    }

    public StateCollection(String sessionId, String agentId) {
        this();
        this.sessionId = sessionId;
        this.agentId = agentId;
    }

    // ---------------------------------------------------------------------------
    // RecoverableState methods
    // ---------------------------------------------------------------------------

    @Override
    public Map<String, Object> getState() {
        Map<String, Object> result = new HashMap<>();
        result.put(GLOBAL_STATE_KEY, globalState.getState());
        result.put(AGENT_STATE_KEY, agentState.getState());
        return result;
    }

    @Override
    public void setState(Map<String, Object> state) {
        if (state != null) {
            Object global = state.get(GLOBAL_STATE_KEY);
            if (global instanceof Map) {
                globalState.setState((Map<String, Object>) global);
            }
            Object agent = state.get(AGENT_STATE_KEY);
            if (agent instanceof Map) {
                agentState.setState((Map<String, Object>) agent);
            }
        }
    }

    // ---------------------------------------------------------------------------
    // State interface methods
    // ---------------------------------------------------------------------------

    @Override
    public Object getGlobal(Object key) {
        if (key == null) {
            return globalState.getState();
        }
        return globalState.get(key);
    }

    @Override
    public void updateGlobal(Map<String, Object> data) {
        globalState.update(data);
    }

    @Override
    public void updateTrace(Object span) {
        // Placeholder - trace state management
        // In Python, this is a no-op placeholder
    }

    @Override
    public void update(Map<String, Object> data) {
        agentState.update(data);
    }

    @Override
    public Object get(Object key) {
        if (key == null) {
            return agentState.getState();
        }
        return agentState.get(key);
    }

    @Override
    public Map<String, Object> dump() {
        Map<String, Object> result = new HashMap<>();
        result.put("global_state", globalState.getState());
        result.put("agent_state", agentState.getState());
        result.put("trace_state", traceState);
        return result;
    }

    // ---------------------------------------------------------------------------
    // Legacy methods (for backwards compatibility)
    // ---------------------------------------------------------------------------

    public void putState(String key, Object value) {
        Map<String, Object> data = new HashMap<>();
        data.put(key, value);
        agentState.update(data);
    }

    public Object getState(String key) {
        return agentState.get(key);
    }

    public Map<String, Object> getAllStates() {
        return agentState.getState();
    }

    public void clear() {
        globalState.setState(new HashMap<>());
        agentState.setState(new HashMap<>());
        traceState.clear();
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

    // ---------------------------------------------------------------------------
    // Python compatibility methods
    // ---------------------------------------------------------------------------

    /**
     * Get global state object for direct access.
     */
    public InMemoryStateLike getGlobalState() {
        return globalState;
    }
}