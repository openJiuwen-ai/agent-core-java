/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.constants;

/**
 * Mirrors Python's constant module in
 * {@code openjiuwen/core/common/constants/constant.py}.
 */
public final class Constant {
    public static final String USER_FIELDS = "userFields";
    public static final String QUERY = "query";
    public static final String SYSTEM_FIELDS = "systemFields";
    public static final String INTERACTION = "__interaction__";
    public static final String INTERACTIVE_INPUT = "__interactive_input__";
    public static final String INPUTS_KEY = "inputs";
    public static final String CONFIG_KEY = "config";
    public static final String END_FRAME = "all streaming outputs finish";
    public static final String END_NODE_STREAM = "end node stream";
    public static final String LOOP_ID = "__sys_loop_id";
    public static final String INDEX = "index";
    public static final String FINISH_INDEX = "finish_index";
    public static final int MAX_COLLECTION_SIZE = 100000;
    public static final int MAX_EXPRESSION_LENGTH = 5000;
    public static final int MAX_AST_DEPTH = 50;
    public static final int NESTED_LOOP_DEPTH = 1;

    private Constant() {
    }
}
