/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.rail;

/**
 * Interface for objects that can fire agent callback events.
 * Used to decouple AgentCallbackContext from BaseAgent.
 */
public interface AgentCallbackFirer {
    /**
     * Fire a callback event with the given context.
     *
     * @param event the event to fire
     * @param ctx   the callback context
     */
    void fireCallbackEvent(AgentCallbackEvent event, AgentCallbackContext ctx);
}
