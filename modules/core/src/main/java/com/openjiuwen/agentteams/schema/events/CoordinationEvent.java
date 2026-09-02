/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.schema.events;

/**
 * Marker interface for all events handled by the coordination dispatcher.
 *
 * <p>Mirrors Python {@code CoordinationEvent = Union[InnerEventMessage, EventMessage]}.
 * Implemented by {@link EventMessage} (cross-process transport events) and
 * {@code com.openjiuwen.agentteams.agent.coordination.InnerEventMessage}
 * (coordination-internal events). The dispatcher uses {@link #eventKey()} as
 * the framework registration key.
 *
 * @since 2026/7/9
 */
public interface CoordinationEvent {
    /**
     * String key used to dispatch this event to registered callbacks.
     *
     * <p>For inner events this is {@code InnerEventType.getValue()}; for
     * transport events it is {@code EventMessage.getEventType()}.
     *
     * @return the event key, never {@code null}
     */
    String eventKey();
}
