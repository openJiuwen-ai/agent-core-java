/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.constants;

/**
 * Session module constants.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.constants}.
 * 
 * @since 0.1.7
 */
public final class SessionConstants {
    /**
     * SessionConstants.
     * 
     * @since 0.1.7
     */
    private SessionConstants() {
    }

    // ======================== Workflow Timeout Keys ========================

    /**
     * WORKFLOW_EXECUTE_TIMEOUT.
     * 
     * @since 0.1.7
     */
    public static final String WORKFLOW_EXECUTE_TIMEOUT = "_execute_timeout";

    /**
     * WORKFLOW_STREAM_FRAME_TIMEOUT.
     * 
     * @since 0.1.7
     */
    public static final String WORKFLOW_STREAM_FRAME_TIMEOUT = "_stream_frame_timeout";

    /**
     * WORKFLOW_STREAM_FIRST_FRAME_TIMEOUT.
     * 
     * @since 0.1.7
     */
    public static final String WORKFLOW_STREAM_FIRST_FRAME_TIMEOUT = "_stream_first_frame_timeout";

    /**
     * COMP_STREAM_CALL_TIMEOUT_KEY.
     * 
     * @since 0.1.7
     */
    public static final String COMP_STREAM_CALL_TIMEOUT_KEY = "_comp_stream_call_timeout";

    /**
     * STREAM_INPUT_GEN_TIMEOUT_KEY.
     * 
     * @since 0.1.7
     */
    public static final String STREAM_INPUT_GEN_TIMEOUT_KEY = "_stream_input_generator_timeout";

    /**
     * END_COMP_TEMPLATE_RENDER_POSITION_TIMEOUT_KEY.
     * 
     * @since 0.1.7
     */
    public static final String END_COMP_TEMPLATE_RENDER_POSITION_TIMEOUT_KEY =
        "_end_comp_template_render_position_timeout";

    /**
     * END_COMP_TEMPLATE_BATCH_READER_TIMEOUT_KEY.
     * 
     * @since 0.1.7
     */
    public static final String END_COMP_TEMPLATE_BATCH_READER_TIMEOUT_KEY = "_end_comp_template_branch_render_timeout";

    /**
     * LOOP_NUMBER_MAX_LIMIT_KEY.
     * 
     * @since 0.1.7
     */
    public static final String LOOP_NUMBER_MAX_LIMIT_KEY = "_loop_number_max_limit";

    /**
     * LOOP_NUMBER_MAX_LIMIT_DEFAULT.
     * 
     * @since 0.1.7
     */
    public static final int LOOP_NUMBER_MAX_LIMIT_DEFAULT = 1000;

    /**
     * FORCE_DEL_WORKFLOW_STATE_KEY.
     * 
     * @since 0.1.7
     */
    public static final String FORCE_DEL_WORKFLOW_STATE_KEY = "_force_del_workflow_state";

    /**
     * LOOP_ID.
     * 
     * @since 0.1.7
     */
    public static final String LOOP_ID = "_loop_id";

    /**
     * INDEX.
     * 
     * @since 0.1.7
     */
    public static final String INDEX = "_index";

    // ======================== Environment Variable Keys ========================

    /**
     * WORKFLOW_EXECUTE_TIMEOUT_ENV_KEY.
     * 
     * @since 0.1.7
     */
    public static final String WORKFLOW_EXECUTE_TIMEOUT_ENV_KEY = "WORKFLOW_EXECUTE_TIMEOUT";

    /**
     * WORKFLOW_STREAM_FRAME_TIMEOUT_ENV_KEY.
     * 
     * @since 0.1.7
     */
    public static final String WORKFLOW_STREAM_FRAME_TIMEOUT_ENV_KEY = "WORKFLOW_STREAM_FRAME_TIMEOUT";

    /**
     * WORKFLOW_STREAM_FIRST_FRAME_TIMEOUT_ENV_KEY.
     * 
     * @since 0.1.7
     */
    public static final String WORKFLOW_STREAM_FIRST_FRAME_TIMEOUT_ENV_KEY = "WORKFLOW_STREAM_FIRST_FRAME_TIMEOUT";

    /**
     * COMP_STREAM_CALL_TIMEOUT_ENV_KEY.
     * 
     * @since 0.1.7
     */
    public static final String COMP_STREAM_CALL_TIMEOUT_ENV_KEY = "COMP_STREAM_CALL_TIMEOUT";

    /**
     * STREAM_INPUT_GEN_TIMEOUT_ENV_KEY.
     * 
     * @since 0.1.7
     */
    public static final String STREAM_INPUT_GEN_TIMEOUT_ENV_KEY = "STREAM_INPUT_GEN_TIMEOUT";

    /**
     * LOOP_NUMBER_MAX_LIMIT_ENV_KEY.
     * 
     * @since 0.1.7
     */
    public static final String LOOP_NUMBER_MAX_LIMIT_ENV_KEY = "LOOP_NUMBER_MAX_LIMIT";

    /**
     * FORCE_DEL_WORKFLOW_STATE_ENV_KEY.
     * 
     * @since 0.1.7
     */
    public static final String FORCE_DEL_WORKFLOW_STATE_ENV_KEY = "FORCE_DEL_WORKFLOW_STATE";
}
