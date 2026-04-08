/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.common.constants;

/**
 * Global constants used across the agent-core framework.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.common.constants.constant} module.</p>
 */
public final class Constant {

    private Constant() {
        // Utility class — no instantiation
    }

    // ======================== IR Fields ========================

    /** IR userFields key */
    public static final String USER_FIELDS = "userFields";

    public static final String QUERY = "query";

    /** IR systemFields key */
    public static final String SYSTEM_FIELDS = "systemFields";

    // ======================== Workflow ========================

    /** Workflow interaction marker */
    public static final String INTERACTION = "__interaction__";

    /** Dynamic interaction input raised by nodes */
    public static final String INTERACTIVE_INPUT = "__interactive_input__";

    public static final String INPUTS_KEY = "inputs";

    public static final String CONFIG_KEY = "config";

    public static final String END_FRAME = "all streaming outputs finish";

    public static final String END_NODE_STREAM = "end node stream";

    public static final String LOOP_ID = "__sys_loop_id";

    public static final String INDEX = "index";

    public static final String FINISH_INDEX = "finish_index";

    // ======================== Safe Limit Constants ========================

    /** Maximum collection size for safety */
    public static final int MAX_COLLECTION_SIZE = 100_000;

    /** Maximum expression length for safety */
    public static final int MAX_EXPRESSION_LENGTH = 5_000;

    /** Maximum AST depth for safety */
    public static final int MAX_AST_DEPTH = 50;

    /** Nested loop depth limit */
    public static final int NESTED_LOOP_DEPTH = 1;
}
