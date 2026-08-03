/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.utils;

/**
 * Agent builder enums.
 * <p>
 * Mirrors Python's {@code enums} in
 * {@code openjiuwen/dev_tools/agent_builder/utils/enums.py}.
 */
public final class AgentBuilderEnums {

    private AgentBuilderEnums() {
    }

    public enum AgentType {
        LLM_AGENT("llm_agent"),
        WORKFLOW("workflow");

        private final String value;

        AgentType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public static AgentType fromValue(String value) {
            for (AgentType item : values()) {
                if (item.value.equals(value)) {
                    return item;
                }
            }
            throw new IllegalArgumentException("Unknown AgentType: " + value);
        }
    }

    public enum BuildState {
        INITIAL("initial"),
        PROCESSING("processing"),
        COMPLETED("completed");

        private final String value;

        BuildState(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public static BuildState fromValue(String value) {
            for (BuildState item : values()) {
                if (item.value.equals(value)) {
                    return item;
                }
            }
            throw new IllegalArgumentException("Unknown BuildState: " + value);
        }
    }

    public enum ProgressStage {
        INITIALIZING("initializing"),
        CLARIFYING("clarifying"),
        RESOURCE_RETRIEVING("resource_retrieving"),
        COMPLETED("completed"),
        ERROR("error"),
        OPTIMIZING("optimizing"),
        GENERATING_CONFIG("generating_config"),
        TRANSFORMING_DSL("transforming_dsl"),
        DETECTING_INTENTION("detecting_intention"),
        GENERATING_WORKFLOW_DESIGN("generating_workflow_design"),
        GENERATING_DL("generating_dl"),
        VALIDATING_DL("validating_dl"),
        REFINING_DL("refining_dl"),
        TRANSFORMING_MERMAID("transforming_mermaid"),
        TRANSFORMING_WORKFLOW_DSL("transforming_workflow_dsl");

        private final String value;

        ProgressStage(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    public enum ProgressStatus {
        PENDING("pending"),
        RUNNING("running"),
        SUCCESS("success"),
        FAILED("failed"),
        WARNING("warning");

        private final String value;

        ProgressStatus(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}
