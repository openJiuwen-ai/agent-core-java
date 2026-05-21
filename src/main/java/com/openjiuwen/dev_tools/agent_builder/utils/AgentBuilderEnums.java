/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.utils;

/**
 * Agent builder enums.
 * <p>
 * Mirrors Python's {@code enums} in
 * {@code openjiuwen.dev_tools.agent_builder.utils.enums}.
 */
public final class AgentBuilderEnums {

    private AgentBuilderEnums() {
    }

    public enum AgentType {
        LLM_AGENT, WORKFLOW
    }

    public enum BuildState {
        INITIAL, PROCESSING, COMPLETED
    }

    public enum ProgressStage {
        INITIALIZING, CLARIFYING, RESOURCE_RETRIEVING, COMPLETED, ERROR, OPTIMIZING,
        GENERATING_CONFIG, TRANSFORMING_DSL,
        DETECTING_INTENTION, GENERATING_WORKFLOW_DESIGN, GENERATING_DL,
        VALIDATING_DL, REFINING_DL, TRANSFORMING_MERMAID, TRANSFORMING_WORKFLOW_DSL
    }

    public enum ProgressStatus {
        PENDING, RUNNING, SUCCESS, FAILED, WARNING
    }
}
