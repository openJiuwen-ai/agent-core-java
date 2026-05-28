/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.single_agent.interrupt;

import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Common interruption state fields.
 *
 * <p>Mirrors Python's {@code BaseInterruptionState} in
 * {@code openjiuwen.core.single_agent.interrupt.state}.</p>
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class BaseInterruptionState {

    /** The AI message that triggered the interruption. */
    protected AssistantMessage aiMessage;

    /** The iteration number when interruption occurred. */
    protected int iteration;

    /** The original user query. */
    @Builder.Default
    protected String originalQuery = "";
}