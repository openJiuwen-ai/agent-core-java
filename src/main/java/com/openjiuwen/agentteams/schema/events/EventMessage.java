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

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
/**
 * Public class EventMessage used by the Java parity implementation.
 *
 * @since 1.0
 */
@AllArgsConstructor
public class EventMessage {
    @Builder.Default
    private String eventType = "";
    @Builder.Default
    private Map<String, Object> payload = new LinkedHashMap<>();
    @Builder.Default
    private String senderId = "";
}
