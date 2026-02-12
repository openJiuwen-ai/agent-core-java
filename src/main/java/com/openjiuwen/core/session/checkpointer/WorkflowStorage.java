/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.checkpointer;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.graph.store.Serializer;
import com.openjiuwen.core.graph.store.Serializer.TypedData;
import com.openjiuwen.core.graph.store.SerializerFactory;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.internal.NodeSession;
import com.openjiuwen.core.session.internal.WorkflowSession;
import com.openjiuwen.core.session.state.CommitState;
import com.openjiuwen.core.session.state.State;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory storage for workflow state.
 * 
 * <p>Stores serialized workflow state blobs and state updates indexed by workflow ID.
 * 
 * <p>对应 Python: agent-core/openjiuwen/core/session/checkpointer/workflow_storage.py - WorkflowStorage
 *
 * @author OpenJiuwen
 * @since 1.0.0
 */
public class WorkflowStorage implements Storage {
    
    /**
     * Map of workflow ID to serialized state blob.
     */
    private final Map<String, TypedData> stateBlobs = new ConcurrentHashMap<>();
    
    /**
     * Map of workflow ID to serialized state updates blob.
     */
    private final Map<String, TypedData> stateUpdatesBlobs = new ConcurrentHashMap<>();
    
    /**
     * Serializer for state data.
     */
    private final Serializer serde;
    
    /**
     * Creates a new WorkflowStorage with default serializer.
     */
    public WorkflowStorage() {
        this.serde = SerializerFactory.createSerializer("pickle");
    }
    
    @Override
    public void save(BaseSession session) {
        String workflowId = getWorkflowId(session);
        if (workflowId == null) {
            return;
        }
        
        // Save state
        Map<String, Object> state = session.getState().getState();
        TypedData stateBlob = serde.dumpsTyped(state);
        if (stateBlob != null && stateBlob.isValid()) {
            stateBlobs.put(workflowId, stateBlob);
        }
        
        // Save updates
        State sessionState = session.getState();
        if (sessionState instanceof CommitState commitState) {
            Map<String, Object> updates = commitState.getUpdates();
            TypedData updatesBlob = serde.dumpsTyped(updates);
            if (updatesBlob != null && updatesBlob.isValid()) {
                stateUpdatesBlobs.put(workflowId, updatesBlob);
            }
        }
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public void recover(BaseSession session, InteractiveInput inputs) {
        String workflowId = getWorkflowId(session);
        if (workflowId == null) {
            return;
        }
        
        // Recover state
        TypedData stateBlob = stateBlobs.get(workflowId);
        if (stateBlob != null && !"empty".equals(stateBlob.type())) {
            Map<String, Object> state = (Map<String, Object>) serde.loadsTyped(stateBlob);
            if (state != null) {
                session.getState().setState(state);
            }
        }
        
        // Process interactive input
        if (inputs != null) {
            processInteractiveInput(session, inputs);
        }
        
        // Recover updates
        TypedData stateUpdatesBlob = stateUpdatesBlobs.get(workflowId);
        if (stateUpdatesBlob != null) {
            Map<String, Object> stateUpdates = (Map<String, Object>) serde.loadsTyped(stateUpdatesBlob);
            State sessionState = session.getState();
            if (sessionState instanceof CommitState commitState && stateUpdates != null) {
                commitState.setUpdates(stateUpdates);
            }
        }
    }
    
    /**
     * Processes interactive input during recovery.
     *
     * @param session the session
     * @param inputs the interactive input
     */
    @SuppressWarnings("unchecked")
    private void processInteractiveInput(BaseSession session, InteractiveInput inputs) {
        State state = session.getState();
        
        if (inputs.hasRawInputs()) {
            // Raw inputs - update and commit workflow state
            if (state instanceof CommitState commitState) {
                commitState.updateAndCommitWorkflowState(
                    Map.of(Constant.INTERACTIVE_INPUT, inputs.getRawInputs())
                );
            }
        } else {
            // User inputs by node ID
            Map<String, Object> userInputs = inputs.getUserInputs();
            for (Map.Entry<String, Object> entry : userInputs.entrySet()) {
                String nodeId = entry.getKey();
                Object value = entry.getValue();
                
                NodeSession nodeSession = new NodeSession(session, nodeId, null);
                Object existingInput = nodeSession.getState().get(Constant.INTERACTIVE_INPUT);
                
                if (existingInput instanceof List) {
                    List<Object> inputList = new ArrayList<>((List<Object>) existingInput);
                    inputList.add(value);
                    nodeSession.getState().update(Map.of(Constant.INTERACTIVE_INPUT, inputList));
                } else {
                    List<Object> inputList = new ArrayList<>();
                    inputList.add(value);
                    nodeSession.getState().update(Map.of(Constant.INTERACTIVE_INPUT, inputList));
                }
            }
            state.commit();
        }
    }
    
    @Override
    public void clear(String workflowId) {
        stateBlobs.remove(workflowId);
        stateUpdatesBlobs.remove(workflowId);
    }
    
    @Override
    public boolean exists(BaseSession session) {
        String workflowId = getWorkflowId(session);
        if (workflowId == null) {
            return false;
        }
        TypedData stateBlob = stateBlobs.get(workflowId);
        return stateBlob != null && !"empty".equals(stateBlob.type());
    }
    
    /**
     * Gets the workflow ID from a session.
     *
     * @param session the base session
     * @return the workflow ID, or null if not available
     */
    private String getWorkflowId(BaseSession session) {
        if (session instanceof WorkflowSession workflowSession) {
            return workflowSession.getWorkflowId();
        }
        return null;
    }
}
