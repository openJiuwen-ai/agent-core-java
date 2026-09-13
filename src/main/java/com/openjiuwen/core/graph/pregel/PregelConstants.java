/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.pregel;

/**
 * Constants for the Pregel graph execution engine.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.graph.pregel.constants}.
 * 
 * @since 0.1.7
 */
public final class PregelConstants {
    /**
     * PregelConstants.
     * 
     * @since 0.1.7
     */
    private PregelConstants() {
    }

    /**
     * START.
     * 
     * @since 0.1.7
     */
    public static final String START = "__start__";

    /**
     * END.
     * 
     * @since 0.1.7
     */
    public static final String END = "__end__";

    /**
     * MAX_RECURSIVE_LIMIT.
     * 
     * @since 0.1.7
     */
    public static final int MAX_RECURSIVE_LIMIT = 10000;

    /**
     * TASK_STATUS_INTERRUPT.
     * 
     * @since 0.1.7
     */
    public static final String TASK_STATUS_INTERRUPT = "__interrupt__";

    /**
     * TASK_STATUS_ERROR.
     * 
     * @since 0.1.7
     */
    public static final String TASK_STATUS_ERROR = "__error__";

    /**
     * NS_SEPARATOR.
     * 
     * @since 0.1.7
     */
    public static final String NS_SEPARATOR = ":";

    /**
     * NS_REPLACE_CHAR.
     * 
     * @since 0.1.7
     */
    public static final String NS_REPLACE_CHAR = "#";

    /**
     * NS.
     * 
     * @since 0.1.7
     */
    public static final String NS = "ns";

    /**
     * PARENT_NS.
     * 
     * @since 0.1.7
     */
    public static final String PARENT_NS = "parent_ns";

    /**
     * SESSION_ID.
     * 
     * @since 0.1.7
     */
    public static final String SESSION_ID = "session_id";

    /**
     * RECURSION_LIMIT.
     * 
     * @since 0.1.7
     */
    public static final String RECURSION_LIMIT = "recursion_limit";
}
