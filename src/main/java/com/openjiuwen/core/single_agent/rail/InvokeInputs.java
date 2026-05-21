/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.single_agent.rail;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Data for BEFORE/AFTER_INVOKE events.
 *
 * <p>Mirrors Python's {@code InvokeInputs} in
 * {@code openjiuwen.core.single_agent.rail.base}.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvokeInputs implements EventInputs {

    /** User query string. */
    private Object query;

    /** Optional conversation/session ID. */
    private String conversationId;

    /** Agent invoke result (filled after invoke). */
    private Map<String, Object> result;
}