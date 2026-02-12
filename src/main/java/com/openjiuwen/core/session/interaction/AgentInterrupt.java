/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.interaction;

/**
 * Exception thrown when an agent requires user interaction to continue.
 * 
 * <p>This exception is used to signal that the agent workflow should be paused
 * and the current state should be checkpointed for later resumption.
 * 
 * @author OpenJiuwen
 * @since 1.0.0
 */
public class AgentInterrupt extends Exception {
    
    private static final long serialVersionUID = 1L;
    
    private final String interruptMessage;
    
    /**
     * Creates a new AgentInterrupt without a message.
     */
    public AgentInterrupt() {
        this(null);
    }
    
    /**
     * Creates a new AgentInterrupt with the given message.
     * 
     * @param message the interrupt message describing what input is needed
     */
    public AgentInterrupt(String message) {
        super(message);
        this.interruptMessage = message;
    }
    
    /**
     * Gets the interrupt message.
     * 
     * @return the message describing what input is needed
     */
    public String getInterruptMessage() {
        return interruptMessage;
    }
}

