/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.pregel;

/**
 * Mirrors Python's constant module in
 * {@code openjiuwen/core/graph/pregel/constants.py}.
 */
public final class PregelConstants {
    public static final String START = "__start__";
    public static final String END = "__end__";
    public static final int MAX_RECURSIVE_LIMIT = 10000;
    public static final String TASK_STATUS_INTERRUPT = "__interrupt__";
    public static final String TASK_STATUS_ERROR = "__error__";
    public static final String NS_SEPARATOR = ":";
    public static final String NS_REPLACE_CHAR = "#";
    public static final String NS = "ns";
    public static final String PARENT_NS = "parent_ns";
    public static final String SESSION_ID = "session_id";
    public static final String RECURSION_LIMIT = "recursion_limit";

    private PregelConstants() {
    }
}
