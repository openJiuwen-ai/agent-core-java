/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session;

/**
 * Session module constants.
 * 
 * <p>Contains timeout configurations, environment variable keys, and other session-related constants.
 * 
 * @author OpenJiuwen
 * @since 1.0.0
 */
public final class SessionConstants {
    
    private SessionConstants() {
        // Utility class, prevent instantiation
    }
    
    // ========== Timeout Configuration Keys ==========
    
    /**
     * Workflow execution timeout key.
     */
    public static final String WORKFLOW_EXECUTE_TIMEOUT = "_execute_timeout";
    
    /**
     * Workflow stream frame timeout key.
     */
    public static final String WORKFLOW_STREAM_FRAME_TIMEOUT = "_stream_frame_timeout";
    
    /**
     * Workflow stream first frame timeout key.
     */
    public static final String WORKFLOW_STREAM_FIRST_FRAME_TIMEOUT = "_stream_first_frame_timeout";
    
    /**
     * Component stream call timeout key.
     */
    public static final String COMP_STREAM_CALL_TIMEOUT_KEY = "_comp_stream_call_timeout";
    
    /**
     * Stream inputs generator timeout key.
     */
    public static final String STREAM_INPUT_GEN_TIMEOUT_KEY = "_stream_input_generator_timeout";
    
    // ========== End Component Template Configuration ==========
    
    /**
     * End component template render position timeout key.
     */
    public static final String END_COMP_TEMPLATE_RENDER_POSITION_TIMEOUT_KEY = "_end_comp_template_render_position_timeout";
    
    /**
     * End component template batch reader timeout key.
     */
    public static final String END_COMP_TEMPLATE_BATCH_READER_TIMEOUT_KEY = "_end_comp_template_branch_render_timeout";
    
    // ========== Loop Component Configuration ==========
    
    /**
     * Loop component max number limit key.
     */
    public static final String LOOP_NUMBER_MAX_LIMIT_KEY = "_loop_number_max_limit";
    
    /**
     * Loop component max number limit default value.
     */
    public static final int LOOP_NUMBER_MAX_LIMIT_DEFAULT = 1000;
    
    // ========== Checkpointer Control ==========
    
    /**
     * Force delete workflow state key.
     */
    public static final String FORCE_DEL_WORKFLOW_STATE_KEY = "_force_del_workflow_state";
    
    // ========== Environment Variable Keys ==========
    
    /**
     * Environment variable key for workflow execute timeout.
     */
    public static final String WORKFLOW_EXECUTE_TIMEOUT_ENV_KEY = "WORKFLOW_EXECUTE_TIMEOUT";
    
    /**
     * Environment variable key for workflow stream frame timeout.
     */
    public static final String WORKFLOW_STREAM_FRAME_TIMEOUT_ENV_KEY = "WORKFLOW_STREAM_FRAME_TIMEOUT";
    
    /**
     * Environment variable key for workflow stream first frame timeout.
     */
    public static final String WORKFLOW_STREAM_FIRST_FRAME_TIMEOUT_ENV_KEY = "WORKFLOW_STREAM_FIRST_FRAME_TIMEOUT";
    
    /**
     * Environment variable key for component stream call timeout.
     */
    public static final String COMP_STREAM_CALL_TIMEOUT_ENV_KEY = "COMP_STREAM_CALL_TIMEOUT";
    
    /**
     * Environment variable key for stream input generator timeout.
     */
    public static final String STREAM_INPUT_GEN_TIMEOUT_ENV_KEY = "STREAM_INPUT_GEN_TIMEOUT";
    
    /**
     * Environment variable key for loop number max limit.
     */
    public static final String LOOP_NUMBER_MAX_LIMIT_ENV_KEY = "LOOP_NUMBER_MAX_LIMIT";
    
    /**
     * Environment variable key for force delete workflow state.
     */
    public static final String FORCE_DEL_WORKFLOW_STATE_ENV_KEY = "FORCE_DEL_WORKFLOW_STATE";
}

