/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.interaction;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.session.internal.AgentSession;
import com.openjiuwen.core.session.stream.OutputStreamWriter;
import com.openjiuwen.core.session.stream.StreamWriterManager;

import java.util.concurrent.CompletableFuture;

/**
 * Agent interaction handler with state management and streaming support.
 * 
 * <p>This class extends BaseInteraction to provide full interaction management
 * for agent sessions, including:
 * <ul>
 *   <li>State-based input management</li>
 *   <li>Stream output for user prompts</li>
 *   <li>Checkpointing on interruption</li>
 * </ul>
 * 
 * @author OpenJiuwen
 * @since 1.0.0
 */
public class AgentInteraction extends BaseInteraction {
    
    private final AgentSession agentSession;
    
    /**
     * Creates a new AgentInteraction.
     * 
     * @param agentSession the agent session
     */
    public AgentInteraction(AgentSession agentSession) {
        super(new AgentStateAccessor(agentSession));
        this.agentSession = agentSession;
    }
    
    /**
     * Waits for user inputs.
     * 
     * <p>If interactive inputs are already available in the session state,
     * returns the next input immediately. Otherwise, triggers checkpointing,
     * sends the interaction output to the stream, and throws an AgentInterrupt.
     * 
     * @param value the value to present to the user
     * @return a CompletableFuture that completes with the user's input, or
     *         completes exceptionally with AgentInterrupt if waiting is needed
     */
    @Override
    public CompletableFuture<Object> waitUserInputs(Object value) {
        // Check if we have a pre-loaded input
        Object inputs = getNextInteractiveInput();
        if (inputs != null) {
            return CompletableFuture.completedFuture(inputs);
        }
        
        // No input available, need to interrupt and wait for user
        return agentSession.getCheckpointer()
            .interruptAgentExecute(agentSession)
            .thenCompose(v -> {
                // Create interaction output
                InteractionOutput payload = new InteractionOutput(
                    agentSession.getAgentId(), 
                    value
                );
                
                // Write to stream if available
                StreamWriterManager writerManager = agentSession.getStreamWriterManager();
                CompletableFuture<Void> writeFuture;
                if (writerManager != null) {
                    OutputStreamWriter outputWriter = writerManager.getOutputWriter();
                    // Convert OutputSchema to Map for writing
                    java.util.Map<String, Object> outputData = new java.util.HashMap<>();
                    outputData.put("type", Constant.INTERACTION);
                    outputData.put("index", idx);
                    outputData.put("payload", payload);
                    writeFuture = outputWriter.write(outputData);
                } else {
                    writeFuture = CompletableFuture.completedFuture(null);
                }
                
                // After writing, throw AgentInterrupt
                return writeFuture.thenCompose(ignored -> {
                    CompletableFuture<Object> future = new CompletableFuture<>();
                    future.completeExceptionally(new AgentInterrupt());
                    return future;
                });
            });
    }
    
    /**
     * State accessor for agent session.
     */
    private static class AgentStateAccessor implements SessionStateAccessor {
        
        private final AgentSession agentSession;
        
        AgentStateAccessor(AgentSession agentSession) {
            this.agentSession = agentSession;
        }
        
        @Override
        public Object get(String key) {
            return agentSession.getState().get(key);
        }
        
        @Override
        public void update(java.util.Map<String, Object> data) {
            agentSession.getState().update(data);
        }
    }
}

