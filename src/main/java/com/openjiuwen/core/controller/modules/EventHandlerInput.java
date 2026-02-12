// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.controller.modules;

import com.openjiuwen.core.controller.schema.Event;
import com.openjiuwen.core.session.Session;

import java.util.Objects;

/**
 * Input data model for event handlers.
 *
 * <p>Contains event and session information that is passed to event handlers.
 *
 * @author OpenJiuwen
 * @since 1.0.0
 */
public class EventHandlerInput {

    private final Event event;
    private final Session session;

    /**
     * Constructor.
     *
     * @param event   the event object
     * @param session the session object
     */
    public EventHandlerInput(Event event, Session session) {
        this.event = Objects.requireNonNull(event, "event must not be null");
        this.session = Objects.requireNonNull(session, "session must not be null");
    }

    /**
     * Gets the event.
     *
     * @return the event
     */
    public Event getEvent() {
        return event;
    }

    /**
     * Gets the session.
     *
     * @return the session
     */
    public Session getSession() {
        return session;
    }
}

