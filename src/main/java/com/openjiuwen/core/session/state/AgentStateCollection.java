/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.state;

import java.util.HashMap;
import java.util.Map;

/**
 * State collection for agent sessions.
 * 
 * <p>Manages global state, agent state, and trace state for an agent.
 * 
 * @author OpenJiuwen
 * @since 1.0.0
 */
public class AgentStateCollection extends State {
    
    private final InMemoryStateLike globalState;
    private final InMemoryStateLike agentState;
    private final Map<String, Object> traceState;
    
    /**
     * Creates a new AgentStateCollection.
     */
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
        // No-op in agent state
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
        result.put(StateConstants.GLOBAL_STATE_KEY, globalState.getState());
        result.put(StateConstants.AGENT_STATE_KEY, agentState.getState());
        return result;
    }
    
    @Override
    public void setState(Map<String, Object> state) {
        if (state == null) {
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> globalStateData = (Map<String, Object>) state.get(StateConstants.GLOBAL_STATE_KEY);
        globalState.setState(globalStateData);
        
        @SuppressWarnings("unchecked")
        Map<String, Object> agentStateData = (Map<String, Object>) state.get(StateConstants.AGENT_STATE_KEY);
        agentState.setState(agentStateData);
    }
    
    /**
     * Gets the global state instance.
     * 
     * @return the global state instance
     */
    public InMemoryStateLike getGlobalStateInstance() {
        return globalState;
    }
    
    /**
     * Gets the global state as a State object.
     * 
     * @return the global state
     */
    public State getGlobalState() {
        return new State() {
            @Override
            public Object get(Object key) {
                if (key == null) {
                    return globalState.getState();
                }
                return globalState.get(key);
            }
            
            @Override
            public void update(Map<String, Object> data) {
                globalState.update(data);
            }
            
            @Override
            public void updateTrace(Object span) {}
            
            @Override
            public void updateGlobal(Map<String, Object> data) {
                globalState.update(data);
            }
            
            @Override
            public Object getGlobal(Object key) {
                return get(key);
            }
            
            @Override
            public Map<String, Object> getState() {
                return globalState.getState();
            }
            
            @Override
            public void setState(Map<String, Object> state) {
                globalState.setState(state);
            }
            
            @Override
            public Map<String, Object> getData() {
                return globalState.getState();
            }
        };
    }
    
    @Override
    public Map<String, Object> getData() {
        return getState();
    }
}

