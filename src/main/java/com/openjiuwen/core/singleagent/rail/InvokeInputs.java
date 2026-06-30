/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.rail;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Data for BEFORE/AFTER_INVOKE events.
 *
 * <p>Before: query + conversationId filled.
 * After: result also filled.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvokeInputs implements EventInputs {
    private String query;
    private Object queryPayload;
    private String conversationId;
    private Map<String, Object> result;
}
