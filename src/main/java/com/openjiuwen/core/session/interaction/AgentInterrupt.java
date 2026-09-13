/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.interaction;

/**
 * Exception thrown when an agent execution is interrupted for user interaction.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.interaction.base.AgentInterrupt}.
 * 
 * @since 0.1.7
 */
public class AgentInterrupt extends RuntimeException {
    /**
     * AgentInterrupt.
     * 
     * @since 0.1.7
     */
    public AgentInterrupt() {
        super();
    }

    /**
     * AgentInterrupt.
     * 
     * @param message message
     * @since 0.1.7
     */
    public AgentInterrupt(String message) {
        super(message);
    }
}
