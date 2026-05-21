/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow;

import com.openjiuwen.dev_tools.agent_builder.builders.BaseAgentBuilder;
import com.openjiuwen.dev_tools.agent_builder.builders.WorkflowBuilder;
import com.openjiuwen.dev_tools.agent_builder.utils.AgentBuilderEnums;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * System tests for Workflow builder module.
 * <p>
 * Mirrors Python's {@code test_workflow_integration.py} in
 * {@code tests.system_tests.dev_tools.agent_builder.builders.workflow}.
 */
class TestWorkflowIntegration {

    private WorkflowBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new WorkflowBuilder(null);
    }

    @Nested
    class TestWorkflowBuilderIntegration {

        @Test
        void workflowBuilderInitialization() {
            assertThat(builder.getState()).isEqualTo(AgentBuilderEnums.BuildState.INITIAL);
        }

        @Test
        void workflowBuilderIsBaseAgentBuilder() {
            assertThat(builder).isInstanceOf(BaseAgentBuilder.class);
        }

        @Test
        void workflowBuilderBuildInInitialState() {
            Map<String, Object> result = builder.build(Map.of("query", "create workflow"), List.of());
            assertThat(result).containsKey("status");
        }

        @Test
        void workflowBuilderStateTransitionsToProcessing() {
            Map<String, Object> result = builder.build(Map.of("query", "test"), List.of());
            assertThat(builder.getState()).isEqualTo(AgentBuilderEnums.BuildState.PROCESSING);
        }
    }

    @Nested
    class TestCycleCheckerIntegration {

        @Test
        void noCycleInLinearWorkflow() {
            Map<String, List<String>> graph = new LinkedHashMap<>();
            graph.put("start", List.of("process"));
            graph.put("process", List.of("end"));
            graph.put("end", List.of());
            assertThat(CycleChecker.hasCycle(graph)).isFalse();
        }

        @Test
        void cycleInWorkflow() {
            Map<String, List<String>> graph = new LinkedHashMap<>();
            graph.put("start", List.of("process"));
            graph.put("process", List.of("start"));
            assertThat(CycleChecker.hasCycle(graph)).isTrue();
        }
    }

    @Nested
    class TestIntentionDetectorIntegration {

        private IntentionDetector detector;

        @BeforeEach
        void setUp() {
            detector = new IntentionDetector();
        }

        @Test
        void detectCreateIntention() {
            assertThat(detector.detect("创建工作流")).isEqualTo(IntentionDetector.Intention.CREATE_WORKFLOW);
        }

        @Test
        void detectUnknownIntention() {
            assertThat(detector.detect("随便聊聊")).isEqualTo(IntentionDetector.Intention.UNKNOWN);
        }
    }
}
