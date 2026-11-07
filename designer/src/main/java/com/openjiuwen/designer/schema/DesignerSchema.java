/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.designer.schema;

import java.util.Map;
import java.util.Set;

/**
 * Wire constants for the designer execution graph and run documents.
 *
 * @since 2026-11-06
 */
public final class DesignerSchema {

    /** Graph document schema version. */
    public static final String GRAPH_SCHEMA_VERSION = "designer-execution-graph.v1";

    /** Run document schema version. */
    public static final String RUN_SCHEMA_VERSION = "designer-execution-run.v1";

    /** Node modalities. Narrative roles belong to {@code config.pipeline}. */
    public static final Set<String> NODE_TYPES = Set.of("text", "table", "image", "video", "audio");

    /** Narrative pipeline roles. */
    public static final Set<String> PIPELINES =
            Set.of("brief", "character", "scene", "storyboard", "clip", "compose", "music", "speech");

    /** Pipeline to node modality. */
    public static final Map<String, String> PIPELINE_TO_TYPE = Map.of(
            "brief", "text",
            "character", "image",
            "scene", "image",
            "storyboard", "table",
            "clip", "video",
            "compose", "video",
            "music", "audio",
            "speech", "audio");

    /** Supported edge kinds. */
    public static final Set<String> EDGE_KINDS = Set.of("data");

    /** Supported graph sources. */
    public static final Set<String> GRAPH_SOURCES = Set.of("prompt", "manual");

    /** Node execution statuses. */
    public static final Set<String> NODE_STATUSES =
            Set.of("pending", "running", "completed", "failed", "cancelled");

    /** Run execution statuses. */
    public static final Set<String> RUN_STATUSES =
            Set.of("draft", "running", "paused", "completed", "failed", "cancelled");

    /** Node delegate modes. */
    public static final Set<String> DELEGATES = Set.of("handler", "subagent", "agent");

    /** Node interaction modes. */
    public static final Set<String> INTERACTION_MODES = Set.of("generate", "upload", "edit");

    /** Activity kinds emitted by worker and director stages. */
    public static final Set<String> ACTIVITY_KINDS = Set.of("thinking", "tool_call", "stage");

    /** Designer agent group name. */
    public static final String DESIGNER_AGENT_GROUP_NAME = "designer";

    /** Role used by uploaded reference nodes. */
    public static final String USER_REFERENCE_ROLE = "user_reference";

    /** Virtual leader node id. */
    public static final String LEADER_NODE_ID = "__leader__";

    /** Compose node id. */
    public static final String COMPOSE_NODE_ID = "n_compose";

    /** Maximum characters kept in one activity text. */
    public static final int ACTIVITY_TEXT_LIMIT = 120;

    /** Maximum activity tail entries. */
    public static final int ACTIVITY_TAIL_LIMIT = 8;

    /** Maximum activity log entries. */
    public static final int ACTIVITY_LOG_LIMIT = 20;

    /** Maximum node history entries. */
    public static final int NODE_HISTORY_LIMIT = 20;

    /** Maximum run error characters. */
    public static final int RUN_ERROR_LIMIT = 500;

    /** Maximum node error characters. */
    public static final int NODE_ERROR_LIMIT = 2000;

    /** Maximum characters inlined for one frozen node. */
    public static final int FROZEN_TEXT_LIMIT = 20000;

    /** Maximum run warnings. */
    public static final int RUN_WARNINGS_LIMIT = 8;

    private DesignerSchema() {
    }
}
