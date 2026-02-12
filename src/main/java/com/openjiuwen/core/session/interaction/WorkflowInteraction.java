/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.interaction;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.graph.pregel.GraphInterrupt;
import com.openjiuwen.core.graph.pregel.Interrupt;
import com.openjiuwen.core.session.internal.NodeSession;
import com.openjiuwen.core.session.state.CommitState;
import com.openjiuwen.core.session.state.State;
import com.openjiuwen.core.session.state.WorkflowStateCollection;
import com.openjiuwen.core.session.stream.OutputStreamWriter;
import com.openjiuwen.core.session.stream.StreamWriterManager;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Workflow interaction handler with state management and streaming support.
 * 
 * <p>This class extends BaseInteraction to provide interaction management
 * for workflow sessions, including:
 * <ul>
 *   <li>Workflow state-based input management</li>
 *   <li>Stream output for user prompts</li>
 *   <li>Graph interrupt handling for workflow execution</li>
 * </ul>
 * 
 * @author OpenJiuwen
 * @since 1.0.0
 */
public class WorkflowInteraction extends BaseInteraction {
    
    private final NodeSession session;
    private final String nodeId;
    
    /**
     * Creates a new WorkflowInteraction.
     * 
     * <p>This constructor extracts and clears any pre-existing interactive input
     * from the workflow state.
     * 
     * @param session the node session
     */
    public WorkflowInteraction(NodeSession session) {
        super(new WorkflowStateAccessor(session), extractWorkflowInteractiveInput(session));
        this.session = session;
        this.nodeId = session.getExecutableId();
    }
    
    /**
     * Extracts and clears interactive input from workflow state.
     * 
     * @param session the node session
     * @return the workflow interactive input, or null if not found
     */
    private static Object extractWorkflowInteractiveInput(NodeSession session) {
        State state = session.getState();
        if (!(state instanceof CommitState commitState)) {
            return null;
        }
        
        Object workflowInteractiveInput = commitState.getWorkflowState(Constant.INTERACTIVE_INPUT);
        if (workflowInteractiveInput != null) {
            commitState.updateAndCommitWorkflowState(
                Map.of(Constant.INTERACTIVE_INPUT, (Object) null)
            );
        }
        return workflowInteractiveInput;
    }
    
    /**
     * Waits for user inputs.
     * 
     * <p>If interactive inputs are already available in the workflow state,
     * returns the next input immediately. Otherwise, commits component state,
     * sends the interaction output to the stream, and throws a GraphInterrupt.
     * 
     * @param value the value to present to the user
     * @return a CompletableFuture that completes with the user's input, or
     *         completes exceptionally with GraphInterrupt if waiting is needed
     */
    @Override
    public CompletableFuture<Object> waitUserInputs(Object value) {
        // Check if we have a pre-loaded input
        Object res = getNextInteractiveInput();
        if (res != null) {
            return CompletableFuture.completedFuture(res);
        }
        
        // Commit component and IO state
        State state = session.getState();
        if (state instanceof WorkflowStateCollection workflowState) {
            workflowState.commitCmp();
        }
        
        // Create interaction output
        InteractionOutput payload = new InteractionOutput(nodeId, value);
        
        // Write to stream if available
        StreamWriterManager writerManager = session.getStreamWriterManager();
        CompletableFuture<Void> writeFuture;
        if (writerManager != null) {
            OutputStreamWriter outputWriter = writerManager.getOutputWriter();
            // Convert to Map for writing
            Map<String, Object> outputData = new java.util.HashMap<>();
            outputData.put("type", Constant.INTERACTION);
            outputData.put("index", idx);
            outputData.put("payload", payload);
            writeFuture = outputWriter.write(outputData);
        } else {
            writeFuture = CompletableFuture.completedFuture(null);
        }
        
        // After writing, throw GraphInterrupt
        return writeFuture.thenCompose(ignored -> {
            // Create the OutputSchema-like structure for the interrupt
            Map<String, Object> interruptValue = new java.util.HashMap<>();
            interruptValue.put("type", Constant.INTERACTION);
            interruptValue.put("index", idx);
            interruptValue.put("payload", payload);
            
            Interrupt interrupt = new Interrupt(interruptValue);
            CompletableFuture<Object> future = new CompletableFuture<>();
            future.completeExceptionally(new GraphInterrupt(interrupt));
            return future;
        });
    }
    
    /**
     * Processes the user's latest input.
     * 
     * <p>If the latest interactive input is already cached, returns it and clears
     * the cache. Otherwise, writes the interaction request to the stream and
     * throws a resumable GraphInterrupt.
     * 
     * @param value the value to present to the user
     * @return a CompletableFuture that completes when processing is done
     */
    @Override
    public CompletableFuture<Void> userLatestInput(Object value) {
        // Check if we have the latest interactive input cached
        if (latestInteractiveInput != null) {
            latestInteractiveInput = null;
            return CompletableFuture.completedFuture(null);
        }
        
        // Write to stream if available
        StreamWriterManager writerManager = session.getStreamWriterManager();
        CompletableFuture<Void> writeFuture;
        if (writerManager != null) {
            OutputStreamWriter outputWriter = writerManager.getOutputWriter();
            // Create a tuple-like payload (nodeId, value)
            Object[] tuplePayload = new Object[] { nodeId, value };
            Map<String, Object> outputData = new java.util.HashMap<>();
            outputData.put("type", Constant.INTERACTION);
            outputData.put("index", idx);
            outputData.put("payload", tuplePayload);
            writeFuture = outputWriter.write(outputData);
        } else {
            writeFuture = CompletableFuture.completedFuture(null);
        }
        
        // After writing, throw resumable GraphInterrupt
        return writeFuture.thenCompose(ignored -> {
            // Create the OutputSchema-like structure for the interrupt
            Object[] tuplePayload = new Object[] { nodeId, null };
            Map<String, Object> interruptValue = new java.util.HashMap<>();
            interruptValue.put("type", Constant.INTERACTION);
            interruptValue.put("index", idx);
            interruptValue.put("payload", tuplePayload);
            
            // Create resumable interrupt with namespace
            Interrupt interrupt = new Interrupt(interruptValue);
            // TODO: Add resumable=true and ns=nodeId support to Interrupt class
            // For now, we just create a GraphInterrupt
            CompletableFuture<Void> future = new CompletableFuture<>();
            future.completeExceptionally(new GraphInterrupt(interrupt));
            return future;
        });
    }
    
    /**
     * State accessor for workflow session.
     */
    private static class WorkflowStateAccessor implements SessionStateAccessor {
        
        private final NodeSession session;
        
        WorkflowStateAccessor(NodeSession session) {
            this.session = session;
        }
        
        @Override
        public Object get(String key) {
            return session.getState().get(key);
        }
        
        @Override
        public void update(Map<String, Object> data) {
            session.getState().update(data);
        }
    }
}

