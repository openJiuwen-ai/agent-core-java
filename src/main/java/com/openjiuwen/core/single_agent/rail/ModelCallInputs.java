/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.single_agent.rail;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Data for BEFORE/AFTER_MODEL_CALL events.
 *
 * <p>Mirrors Python's {@code ModelCallInputs} in
 * {@code openjiuwen.core.single_agent.rail.base}.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelCallInputs implements EventInputs {

    /** Messages to be sent to LLM. */
    private List<Object> messages;

    /** Tools available for the call. */
    private List<Object> tools;

    /** Model context. */
    private Object modelContext;

    /** Response from LLM. */
    private Object response;
}