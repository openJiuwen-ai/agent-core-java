/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.legacy;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.session.AgentGroupSessionApi;

import java.util.List;

/**
 * Default GroupController — routes messages based on subscription.
 * <p>
 * Implements handleEvent() with standard message routing logic:
 * <ol>
 * <li>If receiver_id is specified: point-to-point sending</li>
 * <li>If receiver_id is not specified: broadcast based on subscriptions</li>
 * </ol>
 * <p>
 * Mirrors Python's {@code DefaultGroupController} in {@code multi_agent/legacy/group_controller.py}.
 * 
 * @deprecated Legacy controller for backward compatibility.
 * @since 0.1.7
 */
@Deprecated
public class DefaultGroupController extends BaseGroupController {
    /**
     * DefaultGroupController.
     * 
     * @param agentGroup agentGroup
     * @since 0.1.7
     */
    public DefaultGroupController(LegacyBaseGroup agentGroup) {
        super(agentGroup);
    }

    /**
     * DefaultGroupController.
     * 
     * @since 0.1.7
     */
    public DefaultGroupController() {
        super();
    }

    /**
     * handleEvent.
     * 
     * @param event event
     * @param session session
     * @return the result
     * @since 0.1.7
     */
    @Override
    protected Object handleEvent(GroupEvent event, AgentGroupSessionApi session) {
        if (event.getReceiverId() != null && !event.getReceiverId().isEmpty()) {
            // Point-to-point sending
            Loggers.MULTI_AGENT.info("DefaultGroupController: Routing message to receiver_id={}",
                    event.getReceiverId());
            return sendToAgent(event, event.getReceiverId(), session);
        } else {
            // Broadcast based on subscription relationships
            Loggers.MULTI_AGENT.info("DefaultGroupController: Broadcasting message with message_type={}",
                    event.getCustomEventType());
            List<Object> results = publish(event, session);
            // Return single result for single subscriber, list for multiple
            return results.size() == 1 ? results.get(0) : results;
        }
    }
}
