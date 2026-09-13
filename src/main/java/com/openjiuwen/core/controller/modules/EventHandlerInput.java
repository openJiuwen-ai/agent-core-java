/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.controller.modules;

import com.openjiuwen.core.controller.schema.Event;
import com.openjiuwen.core.session.AgentSessionApi;

/**
 * Input data model for event handlers.
 * <p>
 * Contains event and session information that is isPassed to event handlers.
 * <p>
 * Mirrors Python's {@code EventHandlerInput(BaseModel)}.
 * 
 * @since 0.1.7
 */
public class EventHandlerInput {
    private final Event event;
    private final AgentSessionApi session;

    /**
     * EventHandlerInput.
     * 
     * @param event event
     * @param session session
     * @since 0.1.7
     */
    public EventHandlerInput(Event event, AgentSessionApi session) {
        this.event = event;
        this.session = session;
    }

    /**
     * getEvent.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Event getEvent() {
        return event;
    }

    /**
     * getSession.
     * 
     * @return the result
     * @since 0.1.7
     */
    public AgentSessionApi getSession() {
        return session;
    }
}
