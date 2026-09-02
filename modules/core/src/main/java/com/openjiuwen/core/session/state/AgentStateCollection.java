/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.state;

import java.util.HashMap;
import java.util.Map;

/**
 * Agent state collection managing global and agent state partitions.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.state.agent_state.StateCollection}.
 * 
 * @since 0.1.7
 */
public class AgentStateCollection implements State {
    private final InMemoryStateLike globalState;
    private final InMemoryStateLike agentState;
    private Map<String, Object> traceState;

    /**
     * AgentStateCollection.
     * 
     * @since 0.1.7
     */
    public AgentStateCollection() {
        this.globalState = new InMemoryStateLike();
        this.agentState = new InMemoryStateLike();
        this.traceState = new HashMap<>();
    }

    /**
     * get.
     * 
     * @param key key
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Object get(Object key) {
        if (key == null) {
            return agentState.getState();
        }
        return agentState.get(key);
    }

    /**
     * update.
     * 
     * @param data data
     * @since 0.1.7
     */
    @Override
    public void update(Map<String, Object> data) {
        agentState.update(data);
    }

    /**
     * updateTrace.
     * 
     * @param span span
     * @since 0.1.7
     */
    @Override
    public void updateTrace(Object span) {
        // Placeholder for trace updates
    }

    /**
     * updateGlobal.
     * 
     * @param data data
     * @since 0.1.7
     */
    @Override
    public void updateGlobal(Map<String, Object> data) {
        globalState.update(data);
    }

    /**
     * getGlobal.
     * 
     * @param key key
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Object getGlobal(Object key) {
        if (key == null) {
            return globalState.getState();
        }
        return globalState.get(key);
    }

    /**
     * getState.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Map<String, Object> getState() {
        Map<String, Object> result = new HashMap<>();
        result.put(State.GLOBAL_STATE_KEY, globalState.getState());
        result.put(State.AGENT_STATE_KEY, agentState.getState());
        return result;
    }

    /**
     * setState.
     * 
     * @param state state
     * @since 0.1.7
     */
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
     * 
     * @return the result
     * @since 0.1.7
     */
    public InMemoryStateLike getGlobalStateLike() {
        return globalState;
    }

    /**
     * dump.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Map<String, Object> dump() {
        Map<String, Object> result = new HashMap<>();
        result.put("global_state", globalState.getState());
        result.put("agent_state", agentState.getState());
        result.put("trace_state", traceState);
        return result;
    }
}
