/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.interrupt;

import com.openjiuwen.core.foundation.llm.schema.ToolCall;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Persisted interrupted tool entry for resume support.
 * 
 * @since 0.1.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ToolInterruptEntry implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private ToolCall toolCall;
    private InterruptRequest request;

    /**
     * Final verdicts rails already issued for {@link #toolCall} during earlier replays, re-applied
     * on later replays without consulting those rails again.
     * 
     * @since 0.1.16
     */
    @Builder.Default
    private List<RailSettledDecision> settledDecisions = new ArrayList<>();
}
