/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.stream;

/**
 * Enum for stream output status codes.
 * 
 * @author OpenJiuwen
 * @since 1.0.0
 */
public enum StreamCode {
    
    /**
     * Stream start.
     */
    START(2000),
    
    /**
     * Workflow start component begins execution.
     */
    WORKFLOW_START(3000),
    
    /**
     * Workflow end component finished execution.
     */
    WORKFLOW_END(4000),
    
    /**
     * End marker for a component's stream, contains summary info.
     */
    MESSAGE_END(5000),
    
    /**
     * Partial content output.
     */
    PARTIAL_CONTENT(1206),
    
    /**
     * Final message indicator.
     */
    FINISH(0),
    
    /**
     * Error during stream processing.
     */
    ERROR(-1),
    
    /**
     * Controller agent handoff message.
     */
    CONTROLLER_AGENT_HANDOFF_MESSAGE(14000),
    
    /**
     * Controller agent interrupt message.
     */
    CONTROLLER_AGENT_INTERRUPT_MESSAGE(15000);
    
    private final int code;
    
    StreamCode(int code) {
        this.code = code;
    }
    
    /**
     * Gets the status code.
     * 
     * @return the code
     */
    public int getCode() {
        return code;
    }
}

