/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.pregel;

/**
 * Constants for the Pregel graph execution engine.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.graph.pregel.constants}.
 */
public final class PregelConstants {

    private PregelConstants() {
    }

    /** Virtual start node identifier. */
    public static final String START = "__start__";

    /** Virtual end node identifier. */
    public static final String END = "__end__";

    /** Default maximum recursion (super-step) limit. */
    public static final int MAX_RECURSIVE_LIMIT = 10000;

    /** Task status for interrupted execution. */
    public static final String TASK_STATUS_INTERRUPT = "__interrupt__";

    /** Task status for failed execution. */
    public static final String TASK_STATUS_ERROR = "__error__";

    /** Namespace separator used in config paths. */
    public static final String NS_SEPARATOR = ":";

    /** Replacement character for namespace separator in keys. */
    public static final String NS_REPLACE_CHAR = "#";

    /** Config key for namespace. */
    public static final String NS = "ns";

    /** Config key for parent namespace. */
    public static final String PARENT_NS = "parent_ns";

    /** Config key for session ID. */
    public static final String SESSION_ID = "session_id";

    /** Config key for recursion limit. */
    public static final String RECURSION_LIMIT = "recursion_limit";
}
