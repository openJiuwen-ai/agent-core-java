/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.checkpointer;

import com.openjiuwen.core.graph.store.Serializer;
import com.openjiuwen.core.graph.store.Serializer.TypedData;
import com.openjiuwen.core.graph.store.SerializerFactory;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.internal.AgentSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory storage for agent state.
 * 
 * <p>Stores serialized agent state blobs indexed by agent ID.
 * 
 * <p>对应 Python: agent-core/openjiuwen/core/session/checkpointer/agent_storage.py - AgentStorage
 *
 * @author OpenJiuwen
 * @since 1.0.0
 */
public class AgentStorage implements Storage {
    
    /**
     * Map of agent ID to serialized state blob.
     */
    private final Map<String, TypedData> stateBlobs = new ConcurrentHashMap<>();
    
    /**
     * Serializer for state data.
     */
    private final Serializer serde;
    
    /**
     * Creates a new AgentStorage with default serializer.
     */
    public AgentStorage() {
        this.serde = SerializerFactory.createSerializer("pickle");
    }
    
    @Override
    public void save(BaseSession session) {
        String agentId = getAgentId(session);
        if (agentId == null) {
            return;
        }
        Map<String, Object> state = session.getState().getState();
        TypedData stateBlob = serde.dumpsTyped(state);
        if (stateBlob != null && stateBlob.isValid()) {
            stateBlobs.put(agentId, stateBlob);
        }
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public void recover(BaseSession session, InteractiveInput inputs) {
        String agentId = getAgentId(session);
        if (agentId == null) {
            return;
        }
        TypedData stateBlob = stateBlobs.get(agentId);
        if (stateBlob == null) {
            return;
        }
        Map<String, Object> state = (Map<String, Object>) serde.loadsTyped(stateBlob);
        if (state != null) {
            session.getState().setState(state);
        }
    }
    
    @Override
    public void clear(String agentId) {
        stateBlobs.remove(agentId);
    }
    
    @Override
    public boolean exists(BaseSession session) {
        String agentId = getAgentId(session);
        if (agentId == null) {
            return false;
        }
        return stateBlobs.get(agentId) != null;
    }
    
    /**
     * Gets the agent ID from a session.
     *
     * @param session the base session
     * @return the agent ID, or null if not available
     */
    private String getAgentId(BaseSession session) {
        if (session instanceof AgentSession agentSession) {
            return agentSession.getAgentId();
        }
        return null;
    }
}
