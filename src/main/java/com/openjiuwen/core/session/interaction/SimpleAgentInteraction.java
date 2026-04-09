/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.session.interaction;

import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.checkpointer.Checkpointer;

/**
 * Simple agent interaction that interrupts via checkpointer.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.interaction.interaction.SimpleAgentInteraction}.
 */
public class SimpleAgentInteraction {

    private final BaseSession agentSession;

    public SimpleAgentInteraction(BaseSession agentSession) {
        this.agentSession = agentSession;
    }

    /**
     * Wait for user inputs by interrupting agent execution.
     *
     * @param message the interrupt message
     */
    public void waitUserInputs(String message) {
        Object checkpointer = agentSession.checkpointer();
        if (checkpointer instanceof Checkpointer typedCheckpointer) {
            typedCheckpointer.interruptAgentExecute(agentSession);
        }
        throw new AgentInterrupt(message);
    }
}
