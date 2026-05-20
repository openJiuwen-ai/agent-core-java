/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
/**
 * Public class InnerEventMessage used by the Java parity implementation.
 *
 * @since 1.0
 */
@AllArgsConstructor
public class InnerEventMessage {
    private InnerEventType eventType;
    @Builder.Default
    private Map<String, Object> payload = new LinkedHashMap<>();
}
