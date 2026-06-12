/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.session.state;

import java.util.HashMap;
import java.util.Map;

/**
 * Agent state collection managing global and agent state partitions.
 * <p>
 * Mirrors Python's {@code StateCollection} in
 * {@code openjiuwen/core/session/state/agent_state.py}.
 * </p>
 */
public class AgentStateCollection implements State {

    private final InMemoryStateLike globalState;
    private final InMemoryStateLike agentState;
    private final Map<String, Object> traceState;

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
        // Python implementation is a no-op.
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
        Object global = state.get(State.GLOBAL_STATE_KEY);
        if (global instanceof Map<?, ?> globalMap) {
            globalState.setState(castMap(globalMap));
        }
        Object agent = state.get(State.AGENT_STATE_KEY);
        if (agent instanceof Map<?, ?> agentMap) {
            agentState.setState(castMap(agentMap));
        }
    }

    @Override
    public Map<String, Object> dump() {
        Map<String, Object> result = new HashMap<>();
        result.put("global_state", globalState.getState());
        result.put("agent_state", agentState.getState());
        result.put("trace_state", traceState);
        return result;
    }

    public InMemoryStateLike getGlobalStateLike() {
        return globalState;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }
}
