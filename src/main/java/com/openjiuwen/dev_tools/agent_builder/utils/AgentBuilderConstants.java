/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.utils;

/**
 * Agent builder constants.
 * <p>
 * Mirrors Python's {@code openjiuwen/dev_tools/agent_builder/utils/constants.py}.
 */
public final class AgentBuilderConstants {

    public static final String WORKFLOW_REQUEST_CONTENT =
            "Please provide your desired workflow description so I can generate "
                    + "the corresponding flowchart for you. If unclear, you can reply 'unclear' "
                    + "and I will plan the process for you.";
    public static final String WORKFLOW_DESIGN_RESPONSE_CONTENT = "Workflow design content:\n";
    public static final String GENERATE_DL_FROM_DESIGN_CONTENT =
            "Please generate the corresponding Process Definition Language (DL) "
                    + "description based on the following workflow design content:\n";
    public static final String MODIFY_DL_CONTENT =
            "Please correct the Process Definition Language (DL) based on the following error message:\n";
    public static final int DEFAULT_MAX_HISTORY_SIZE = 50;
    public static final int DEFAULT_MAX_RETRIES = 3;
    public static final int DEFAULT_TIMEOUT = 30;
    public static final String RESOURCE_TYPE_PLUGIN = "plugin";
    public static final String RESOURCE_TYPE_KNOWLEDGE = "knowledge";
    public static final String RESOURCE_TYPE_WORKFLOW = "workflow";
    public static final String JSON_EXTRACT_PATTERN = "```(?:json)?\\s*([\\s\\S]*?)\\s*```";
    public static final String API_VERSION = "v1";
    public static final String API_BASE_PATH = "/api/" + API_VERSION;
    public static final double PROGRESS_UPDATE_INTERVAL = 0.1d;
    public static final double PROGRESS_HEARTBEAT_INTERVAL = 30.0d;
    public static final int MAX_QUERY_LENGTH = 5000;
    public static final int MIN_QUERY_LENGTH = 1;
    public static final int MAX_SESSION_ID_LENGTH = 255;
    public static final int MAX_HISTORY_SIZE = 1000;
    public static final int MIN_HISTORY_SIZE = 1;

    private AgentBuilderConstants() {
    }
}
