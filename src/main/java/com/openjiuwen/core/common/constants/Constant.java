// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.common.constants;

/**
 * System-level constants
 * 
 * <p>This class contains all constant values used across the agent-core system.
 * It includes IR keys, workflow constants, and safe limit constants.
 * 
 * @since 0.1.4
 */
public final class Constant {
    
    // Private constructor to prevent instantiation
    private Constant() {
        throw new AssertionError("Constant class cannot be instantiated");
    }
    
    // ==================== IR Keys ====================
    
    /** IR userFields key */
    public static final String USER_FIELDS = "userFields";
    
    /** Query field key */
    public static final String QUERY = "query";
    
    /** IR systemFields key */
    public static final String SYSTEM_FIELDS = "systemFields";
    
    // ==================== Workflow Constants ====================
    
    /** Interaction marker for workflow */
    public static final String INTERACTION = "__interaction__";
    
    /** Interactive input marker for dynamic interaction raised by nodes */
    public static final String INTERACTIVE_INPUT = "__interactive_input__";
    
    /** Inputs key for node configuration */
    public static final String INPUTS_KEY = "inputs";
    
    /** Config key for node configuration */
    public static final String CONFIG_KEY = "config";
    
    /** End frame marker indicating all streaming outputs finished */
    public static final String END_FRAME = "all streaming outputs finish";
    
    /** End node stream marker */
    public static final String END_NODE_STREAM = "end node stream";
    
    /** System loop ID key */
    public static final String LOOP_ID = "__sys_loop_id";
    
    /** Index key */
    public static final String INDEX = "index";
    
    /** Finish index key */
    public static final String FINISH_INDEX = "finish_index";
    
    // ==================== Safe Limit Constants ====================
    
    /** Maximum allowed collection size */
    public static final int MAX_COLLECTION_SIZE = 100000;
    
    /** Maximum allowed expression length */
    public static final int MAX_EXPRESSION_LENGTH = 5000;
    
    /** Maximum allowed AST depth */
    public static final int MAX_AST_DEPTH = 50;
    
    /** Maximum allowed nested loop depth (1 means no nesting) */
    public static final int NESTED_LOOP_DEPTH = 1;
}

