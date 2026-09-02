/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.rail;

/**
 * Interface for objects that can fire agent callback events.
 * Used to decouple AgentCallbackContext from BaseAgent.
 * 
 * @since 0.1.7
 */
public interface AgentCallbackFirer {
    /**
     * fireCallbackEvent.
     * 
     * @param event event
     * @param ctx ctx
     * @since 0.1.7
     */
    void fireCallbackEvent(AgentCallbackEvent event, AgentCallbackContext ctx);
}
