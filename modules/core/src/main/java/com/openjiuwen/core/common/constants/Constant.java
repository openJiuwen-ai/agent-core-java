/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.constants;

/**
 * Global constants used across the agent-core framework.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.common.constants.constant} module.
 * </p>
 * 
 * @since 0.1.7
 */
public final class Constant {
    /**
     * Constant.
     * 
     * @since 0.1.7
     */
    private Constant() {
        // Utility class — no instantiation
    }

    // ======================== IR Fields ========================

    /**
     * USER_FIELDS.
     * 
     * @since 0.1.7
     */
    public static final String USER_FIELDS = "userFields";

    /**
     * QUERY.
     * 
     * @since 0.1.7
     */
    public static final String QUERY = "query";

    /**
     * SYSTEM_FIELDS.
     * 
     * @since 0.1.7
     */
    public static final String SYSTEM_FIELDS = "systemFields";

    /**
     * INTERACTION.
     * 
     * @since 0.1.7
     */
    public static final String INTERACTION = "__interaction__";

    /**
     * INTERACTIVE_INPUT.
     * 
     * @since 0.1.7
     */
    public static final String INTERACTIVE_INPUT = "__interactive_input__";

    /**
     * INPUTS_KEY.
     * 
     * @since 0.1.7
     */
    public static final String INPUTS_KEY = "inputs";

    /**
     * CONFIG_KEY.
     * 
     * @since 0.1.7
     */
    public static final String CONFIG_KEY = "config";

    /**
     * END_FRAME.
     * 
     * @since 0.1.7
     */
    public static final String END_FRAME = "all streaming outputs finish";

    /**
     * END_NODE_STREAM.
     * 
     * @since 0.1.7
     */
    public static final String END_NODE_STREAM = "end node stream";

    /**
     * LOOP_ID.
     * 
     * @since 0.1.7
     */
    public static final String LOOP_ID = "__sys_loop_id";

    /**
     * INDEX.
     * 
     * @since 0.1.7
     */
    public static final String INDEX = "index";

    /**
     * FINISH_INDEX.
     * 
     * @since 0.1.7
     */
    public static final String FINISH_INDEX = "finish_index";

    // ======================== Safe Limit Constants ========================

    /**
     * MAX_COLLECTION_SIZE.
     * 
     * @since 0.1.7
     */
    public static final int MAX_COLLECTION_SIZE = 100_000;

    /**
     * MAX_EXPRESSION_LENGTH.
     * 
     * @since 0.1.7
     */
    public static final int MAX_EXPRESSION_LENGTH = 5_000;

    /**
     * MAX_AST_DEPTH.
     * 
     * @since 0.1.7
     */
    public static final int MAX_AST_DEPTH = 50;

    /**
     * NESTED_LOOP_DEPTH.
     * 
     * @since 0.1.7
     */
    public static final int NESTED_LOOP_DEPTH = 1;
}
