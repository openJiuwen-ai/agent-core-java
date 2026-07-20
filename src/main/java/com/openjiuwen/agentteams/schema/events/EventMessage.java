/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.schema.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Public class EventMessage used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class EventMessage implements CoordinationEvent {
    @Builder.Default
    private String eventType = "";
    @Builder.Default
    /**
     * LinkedHashMap<>.
     * 
     * @since 0.1.7
     */
    private Map<String, Object> payload = new LinkedHashMap<>();
    @Builder.Default
    private String senderId = "";

    @Override
    public String eventKey() {
        return eventType != null ? eventType : "";
    }
}
