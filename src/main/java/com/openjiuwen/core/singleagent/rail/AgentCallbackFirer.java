/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.rail;

/**
 * Interface for firing callback events from within agent code.
 *
 * <p>Implemented by {@link com.openjiuwen.core.singleagent.BaseAgent} so that
 * callback context objects can delegate event firing back to the agent.</p>
 *
 * @since 0.1.7
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
