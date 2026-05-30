/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.utils;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test enums functionality.
 * <p>
 * Mirrors Python's {@code test_enums.py} in
 * {@code tests/unit_tests/dev_tools/agent_builder/utils/test_enums.py}.
 *
 */
class TestEnums {

    /**
     * Test AgentType enum.
     * <p>
     * Mirrors Python's {@code TestAgentType} class.
     */
    @Nested
    class TestAgentType {

        @Test
        void testAgentTypeValues() {
            assertEquals("llm_agent", AgentBuilderEnums.AgentType.LLM_AGENT.getValue());
            assertEquals("workflow", AgentBuilderEnums.AgentType.WORKFLOW.getValue());
        }

        @Test
        void testAgentTypeIsStringEnum() {
            assertInstanceOf(String.class, AgentBuilderEnums.AgentType.LLM_AGENT.getValue());
            assertInstanceOf(String.class, AgentBuilderEnums.AgentType.WORKFLOW.getValue());
        }

        @Test
        void testAgentTypeFromString() {
            assertEquals(AgentBuilderEnums.AgentType.LLM_AGENT,
                    AgentBuilderEnums.AgentType.fromValue("llm_agent"));
            assertEquals(AgentBuilderEnums.AgentType.WORKFLOW,
                    AgentBuilderEnums.AgentType.fromValue("workflow"));
        }

        @Test
        void testAgentTypeInvalidValue() {
            assertThrows(IllegalArgumentException.class,
                    () -> AgentBuilderEnums.AgentType.fromValue("invalid_type"));
        }
    }

    /**
     * Test BuildState enum.
     * <p>
     * Mirrors Python's {@code TestBuildState} class.
     */
    @Nested
    class TestBuildState {

        @Test
        void testBuildStateValues() {
            assertEquals("initial", AgentBuilderEnums.BuildState.INITIAL.getValue());
            assertEquals("processing", AgentBuilderEnums.BuildState.PROCESSING.getValue());
            assertEquals("completed", AgentBuilderEnums.BuildState.COMPLETED.getValue());
        }

        @Test
        void testBuildStateIsStringEnum() {
            assertInstanceOf(String.class, AgentBuilderEnums.BuildState.INITIAL.getValue());
            assertInstanceOf(String.class, AgentBuilderEnums.BuildState.PROCESSING.getValue());
            assertInstanceOf(String.class, AgentBuilderEnums.BuildState.COMPLETED.getValue());
        }

        @Test
        void testBuildStateFromString() {
            assertEquals(AgentBuilderEnums.BuildState.INITIAL,
                    AgentBuilderEnums.BuildState.fromValue("initial"));
            assertEquals(AgentBuilderEnums.BuildState.PROCESSING,
                    AgentBuilderEnums.BuildState.fromValue("processing"));
            assertEquals(AgentBuilderEnums.BuildState.COMPLETED,
                    AgentBuilderEnums.BuildState.fromValue("completed"));
        }
    }

    /**
     * Test ProgressStage enum.
     * <p>
     * Mirrors Python's {@code TestProgressStage} class.
     */
    @Nested
    class TestProgressStage {

        @Test
        void testProgressStageValues() {
            assertEquals("initializing", AgentBuilderEnums.ProgressStage.INITIALIZING.getValue());
            assertEquals("clarifying", AgentBuilderEnums.ProgressStage.CLARIFYING.getValue());
            assertEquals("resource_retrieving", AgentBuilderEnums.ProgressStage.RESOURCE_RETRIEVING.getValue());
            assertEquals("completed", AgentBuilderEnums.ProgressStage.COMPLETED.getValue());
            assertEquals("error", AgentBuilderEnums.ProgressStage.ERROR.getValue());
            assertEquals("optimizing", AgentBuilderEnums.ProgressStage.OPTIMIZING.getValue());
            assertEquals("generating_config", AgentBuilderEnums.ProgressStage.GENERATING_CONFIG.getValue());
            assertEquals("transforming_dsl", AgentBuilderEnums.ProgressStage.TRANSFORMING_DSL.getValue());
            assertEquals("detecting_intention", AgentBuilderEnums.ProgressStage.DETECTING_INTENTION.getValue());
            assertEquals("generating_workflow_design",
                    AgentBuilderEnums.ProgressStage.GENERATING_WORKFLOW_DESIGN.getValue());
            assertEquals("generating_dl", AgentBuilderEnums.ProgressStage.GENERATING_DL.getValue());
            assertEquals("validating_dl", AgentBuilderEnums.ProgressStage.VALIDATING_DL.getValue());
            assertEquals("refining_dl", AgentBuilderEnums.ProgressStage.REFINING_DL.getValue());
            assertEquals("transforming_mermaid", AgentBuilderEnums.ProgressStage.TRANSFORMING_MERMAID.getValue());
            assertEquals("transforming_workflow_dsl",
                    AgentBuilderEnums.ProgressStage.TRANSFORMING_WORKFLOW_DSL.getValue());
        }
    }

    /**
     * Test ProgressStatus enum.
     * <p>
     * Mirrors Python's {@code TestProgressStatus} class.
     */
    @Nested
    class TestProgressStatus {

        @Test
        void testProgressStatusValues() {
            assertEquals("pending", AgentBuilderEnums.ProgressStatus.PENDING.getValue());
            assertEquals("running", AgentBuilderEnums.ProgressStatus.RUNNING.getValue());
            assertEquals("success", AgentBuilderEnums.ProgressStatus.SUCCESS.getValue());
            assertEquals("failed", AgentBuilderEnums.ProgressStatus.FAILED.getValue());
            assertEquals("warning", AgentBuilderEnums.ProgressStatus.WARNING.getValue());
        }
    }
}
