/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.session.interaction;

/**
 * Exception thrown when an agent execution is interrupted for user interaction.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.interaction.base.AgentInterrupt}.
 */
public class AgentInterrupt extends RuntimeException {

    public AgentInterrupt() {
        super();
    }

    public AgentInterrupt(String message) {
        super(message);
    }
}
