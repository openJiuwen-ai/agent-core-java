/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.controller.modules;

import com.openjiuwen.core.controller.schema.Event;
import com.openjiuwen.core.session.AgentSessionApi;

/**
 * Input data model for event handlers.
 * <p>
 * Contains event and session information that is passed to event handlers.
 * <p>
 * Mirrors Python's {@code EventHandlerInput(BaseModel)}.
 */
public class EventHandlerInput {

    private final Event event;
    private final AgentSessionApi session;

    public EventHandlerInput(Event event, AgentSessionApi session) {
        this.event = event;
        this.session = session;
    }

    public Event getEvent() {
        return event;
    }

    public AgentSessionApi getSession() {
        return session;
    }
}
