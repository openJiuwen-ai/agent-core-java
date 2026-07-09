/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.interrupt;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Persisted single-agent interruption state.
 * 
 * @since 0.1.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolInterruptionState implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * INTERRUPTION_KEY.
     * 
     * @since 0.1.7
     */
    public static final String INTERRUPTION_KEY = "__react_agent_interruption__";

    /**
     * RESUME_USER_INPUT_KEY.
     * 
     * @since 0.1.7
     */
    public static final String RESUME_USER_INPUT_KEY = "_resume_user_input";

    private int iteration;

    @Builder.Default
    /**
     * ArrayList<ToolInterruptEntry>.
     * 
     * @since 0.1.7
     */
    private List<ToolInterruptEntry> interruptedTools = new ArrayList<ToolInterruptEntry>();

    private String originalQuery;
}
