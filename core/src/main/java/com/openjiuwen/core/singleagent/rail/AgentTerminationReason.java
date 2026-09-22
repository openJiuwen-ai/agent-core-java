/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.rail;

/**
 * Terminal outcome of a ReAct agent invocation.
 *
 * @since 0.1.16
 */
public enum AgentTerminationReason {
    /** The model returned a final text response. */
    TEXT_TERMINATION,

    /** The configured ReAct iteration budget was exhausted. */
    MAX_ITERATIONS,

    /** A rail requested immediate completion. */
    FORCE_FINISH,

    /** Tool execution was suspended for external input. */
    TOOL_INTERRUPT,

    /** Model execution failed or produced no usable terminal response. */
    MODEL_ERROR,

    /** The invocation failed outside model execution. */
    ERROR
}
