/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.state;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Agent state collection managing global and agent state partitions.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.state.agent_state.StateCollection}.
 */
public class AgentStateCollection implements State {

    private final InMemoryStateLike globalState;
    private final InMemoryStateLike agentState;
    private Map<String, Object> traceState;

    public AgentStateCollection() {
        this.globalState = new InMemoryStateLike();
        this.agentState = new InMemoryStateLike();
        this.traceState = new HashMap<>();
    }

    @Override
    public Object get(Object key) {
        if (key == null) {
            return agentState.getState();
        }
        return agentState.get(key);
    }

    @Override
    public void update(Map<String, Object> data) {
        agentState.update(data);
    }

    @Override
    public void updateTrace(Object span) {
        // Placeholder for trace updates
    }

    @Override
    public void updateGlobal(Map<String, Object> data) {
        globalState.update(data);
    }

    @Override
    public Object getGlobal(Object key) {
        if (key == null) {
            return globalState.getState();
        }
        return globalState.get(key);
    }

    @Override
    public Map<String, Object> getState() {
        Map<String, Object> result = new HashMap<>();
        result.put(State.GLOBAL_STATE_KEY, globalState.getState());
        result.put(State.AGENT_STATE_KEY, agentState.getState());
        return result;
    }

    @Override
    public void setState(Map<String, Object> state) {
        if (state == null) {
            return;
        }
        Object gs = state.get(State.GLOBAL_STATE_KEY);
        if (gs instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> gsMap = (Map<String, Object>) gs;
            globalState.setState(gsMap);
        }
        Object as = state.get(State.AGENT_STATE_KEY);
        if (as instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> asMap = (Map<String, Object>) as;
            agentState.setState(asMap);
        }
    }

    /**
     * Get the internal global state object.
     */
    public InMemoryStateLike getGlobalStateLike() {
        return globalState;
    }

    @Override
    public Map<String, Object> dump() {
        Map<String, Object> result = new HashMap<>();
        result.put("global_state", globalState.getState());
        result.put("agent_state", agentState.getState());
        result.put("trace_state", traceState);
        return result;
    }
}
