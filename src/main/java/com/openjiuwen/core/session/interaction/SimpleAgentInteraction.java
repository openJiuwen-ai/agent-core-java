/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.interaction;

import com.openjiuwen.core.session.internal.AgentSession;

import java.util.concurrent.CompletableFuture;

/**
 * Simple agent interaction handler for basic user interaction scenarios.
 * 
 * <p>This class provides a simplified way to request user input by interrupting
 * agent execution. It doesn't manage interaction state or support resumption.
 * 
 * <p>对应 Python: agent-core/openjiuwen/core/session/interaction/interaction.py - SimpleAgentInteraction
 * 
 * @author OpenJiuwen
 * @since 1.0.0
 */
public class SimpleAgentInteraction {
    
    private final AgentSession agentSession;
    
    /**
     * Creates a new SimpleAgentInteraction.
     * 
     * @param agentSession the agent session
     */
    public SimpleAgentInteraction(AgentSession agentSession) {
        this.agentSession = agentSession;
    }
    
    /**
     * Waits for user inputs by interrupting the agent execution.
     * 
     * <p>This method will trigger the checkpointer to save the current state
     * and then complete exceptionally with an AgentInterrupt exception.
     * 
     * @param message the message to display to the user
     * @return a CompletableFuture that completes exceptionally with AgentInterrupt
     */
    public CompletableFuture<Object> waitUserInputs(String message) {
        return agentSession.getCheckpointer()
            .interruptAgentExecute(agentSession)
            .thenCompose(v -> {
                // Complete exceptionally with AgentInterrupt
                CompletableFuture<Object> future = new CompletableFuture<>();
                future.completeExceptionally(new AgentInterrupt(message));
                return future;
            });
    }
}

