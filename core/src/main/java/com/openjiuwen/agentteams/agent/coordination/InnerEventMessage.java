/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.agent.coordination;

import com.openjiuwen.agentteams.schema.events.CoordinationEvent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Internal event message, isolated from cross-process {@code EventMessage}.
 *
 * <p>Mirrors Python {@code InnerEventMessage} (pydantic model). Generated
 * inside the coordination layer — by the {@code EventBus} poll timers and by
 * {@code CoordinationKernel.enqueueUserInput} — and consumed by the
 * {@code EventDispatcher}. Never crosses a process boundary.
 *
 * @since 2026/7/9
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InnerEventMessage implements CoordinationEvent {
    private InnerEventType eventType;
    @Builder.Default
    private Map<String, Object> payload = new LinkedHashMap<>();

    /**
     * Return the event key derived from the event type value.
     *
     * @return the event type value string, or empty string when the type is {@code null}
     * @since 0.1.7
     */
    @Override
    public String eventKey() {
        return eventType != null ? eventType.getValue() : "";
    }
}
