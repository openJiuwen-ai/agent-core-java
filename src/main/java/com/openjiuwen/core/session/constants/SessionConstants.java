/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.constants;

/**
 * SessionConstants
 */
public final class SessionConstants {
    public static final String WORKFLOW_EXECUTE_TIMEOUT = "_execute_timeout";
    public static final String WORKFLOW_STREAM_FRAME_TIMEOUT = "_stream_frame_timeout";
    public static final String WORKFLOW_STREAM_FIRST_FRAME_TIMEOUT = "_stream_first_frame_timeout";
    public static final String COMP_STREAM_CALL_TIMEOUT_KEY = "_comp_stream_call_timeout";
    public static final String STREAM_INPUT_GEN_TIMEOUT_KEY = "_stream_input_generator_timeout";
    public static final String END_COMP_TEMPLATE_RENDER_POSITION_TIMEOUT_KEY =
            "_end_comp_template_render_position_timeout";
    public static final String END_COMP_TEMPLATE_BATCH_READER_TIMEOUT_KEY =
            "_end_comp_template_branch_render_timeout";
    public static final String LOOP_NUMBER_MAX_LIMIT_KEY = "_loop_number_max_limit";
    public static final int LOOP_NUMBER_MAX_LIMIT_DEFAULT = 1000;
    public static final String FORCE_DEL_WORKFLOW_STATE_KEY = "_force_del_workflow_state";
    public static final String WORKFLOW_EXECUTE_TIMEOUT_ENV_KEY = "WORKFLOW_EXECUTE_TIMEOUT";
    public static final String WORKFLOW_STREAM_FRAME_TIMEOUT_ENV_KEY = "WORKFLOW_STREAM_FRAME_TIMEOUT";
    public static final String WORKFLOW_STREAM_FIRST_FRAME_TIMEOUT_ENV_KEY = "WORKFLOW_STREAM_FIRST_FRAME_TIMEOUT";
    public static final String COMP_STREAM_CALL_TIMEOUT_ENV_KEY = "COMP_STREAM_CALL_TIMEOUT";
    public static final String STREAM_INPUT_GEN_TIMEOUT_ENV_KEY = "STREAM_INPUT_GEN_TIMEOUT";
    public static final String LOOP_NUMBER_MAX_LIMIT_ENV_KEY = "LOOP_NUMBER_MAX_LIMIT";
    public static final String FORCE_DEL_WORKFLOW_STATE_ENV_KEY = "FORCE_DEL_WORKFLOW_STATE";

    private SessionConstants() {
    }
}
