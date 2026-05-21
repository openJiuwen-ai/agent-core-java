/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.utils;

/**
 * Agent builder constants.
 * <p>
 * Contains workflow related constants, default configurations,
 * resource types, regex patterns, and limit constants.
 * <p>
 * Mirrors Python's {@code constants} in
 * {@code openjiuwen.dev_tools.agent_builder.utils.constants}.
 */
public final class AgentBuilderConstants {

    private AgentBuilderConstants() {
    }

    // ========== Workflow Related Constants ==========

    /**
     * Workflow request content prompt.
     */
    public static final String WORKFLOW_REQUEST_CONTENT =
            "Please provide your desired workflow description so I can generate "
            + "the corresponding flowchart for you. If unclear, you can reply 'unclear' "
            + "and I will plan the process for you.";

    /**
     * Workflow design response content header.
     */
    public static final String WORKFLOW_DESIGN_RESPONSE_CONTENT = "Workflow design content:\n";

    /**
     * Generate DL from design content prompt.
     */
    public static final String GENERATE_DL_FROM_DESIGN_CONTENT =
            "Please generate the corresponding Process Definition Language (DL) "
            + "description based on the following workflow design content:\n";

    /**
     * Modify DL content prompt.
     */
    public static final String MODIFY_DL_CONTENT =
            "Please correct the Process Definition Language (DL) based on the following error message:\n";

    // ========== Default Configuration ==========

    /**
     * Default maximum history size.
     */
    public static final int DEFAULT_MAX_HISTORY_SIZE = 50;

    /**
     * Default maximum retries.
     */
    public static final int DEFAULT_MAX_RETRIES = 3;

    /**
     * Default timeout in seconds.
     */
    public static final int DEFAULT_TIMEOUT = 30;

    // ========== Resource Types ==========

    /**
     * Plugin resource type.
     */
    public static final String RESOURCE_TYPE_PLUGIN = "plugin";

    /**
     * Knowledge resource type.
     */
    public static final String RESOURCE_TYPE_KNOWLEDGE = "knowledge";

    /**
     * Workflow resource type.
     */
    public static final String RESOURCE_TYPE_WORKFLOW = "workflow";

    // ========== Regex Patterns ==========

    /**
     * JSON extraction regex pattern.
     */
    public static final String JSON_EXTRACT_PATTERN = "```(?:json)?\\s*([\\s\\S]*?)\\s*```";

    // ========== API Related Constants ==========

    /**
     * API version.
     */
    public static final String API_VERSION = "v1";

    /**
     * API base path.
     */
    public static final String API_BASE_PATH = "/api/" + API_VERSION;

    // ========== Progress Related Constants ==========

    /**
     * Progress update interval in seconds.
     */
    public static final double PROGRESS_UPDATE_INTERVAL = 0.1;

    /**
     * Progress heartbeat interval in seconds.
     */
    public static final double PROGRESS_HEARTBEAT_INTERVAL = 30.0;

    // ========== Limit Constants ==========

    /**
     * Maximum query length.
     */
    public static final int MAX_QUERY_LENGTH = 5000;

    /**
     * Minimum query length.
     */
    public static final int MIN_QUERY_LENGTH = 1;

    /**
     * Maximum session ID length.
     */
    public static final int MAX_SESSION_ID_LENGTH = 255;

    /**
     * Maximum history size.
     */
    public static final int MAX_HISTORY_SIZE = 1000;

    /**
     * Minimum history size.
     */
    public static final int MIN_HISTORY_SIZE = 1;
}