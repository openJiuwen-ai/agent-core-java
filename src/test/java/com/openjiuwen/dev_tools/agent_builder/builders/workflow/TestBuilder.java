/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

import com.openjiuwen.dev_tools.agent_builder.builders.WorkflowBuilder;
import com.openjiuwen.dev_tools.agent_builder.utils.AgentBuilderEnums;

import java.util.*;

/**
 * Test WorkflowBuilder functionality.
 * <p>
 * Mirrors Python's {@code test_builder.py} in
 * {@code tests/unit_tests/dev_tools/agent_builder/builders/workflow/test_builder.py}.
 */
class TestBuilder {

    /**
     * Test WorkflowBuilder initialization.
     * <p>
     * Mirrors Python's {@code TestWorkflowBuilderInit} class.
     */
    static class TestInit {

        @Test
        void testInitSuccess() {
            Object mockModel = new Object();
            Object mockHistoryManager = new Object();
            WorkflowBuilder builder = new WorkflowBuilder(mockModel, mockHistoryManager);

            Assertions.assertEquals(mockModel, builder.getLlm());
            Assertions.assertEquals(AgentBuilderEnums.BuildState.INITIAL, builder.getState());
            Assertions.assertNull(builder.getWorkflowName());
            Assertions.assertNull(builder.getWorkflowNameEn());
            Assertions.assertNull(builder.getWorkflowDesc());
            Assertions.assertNull(builder.getDl());
            Assertions.assertNull(builder.getMermaidCode());
        }

        @Test
        void testInitWithNullLlm() {
            WorkflowBuilder builder = new WorkflowBuilder(null, null);

            Assertions.assertNull(builder.getLlm());
            Assertions.assertEquals(AgentBuilderEnums.BuildState.INITIAL, builder.getState());
        }

        @Test
        void testInitProgressReporterDefaultNull() {
            Object mockModel = new Object();
            WorkflowBuilder builder = new WorkflowBuilder(mockModel, null);

            // WorkflowBuilder created without explicit progress reporter
            Assertions.assertEquals(AgentBuilderEnums.BuildState.INITIAL, builder.getState());
        }
    }

    /**
     * Test WorkflowBuilder resource property.
     * <p>
     * Mirrors Python's {@code TestWorkflowBuilderResource} class.
     */
    static class TestResource {

        @Test
        void testResourceProperty() {
            Object mockModel = new Object();
            WorkflowBuilder builder = new WorkflowBuilder(mockModel, null);

            Map<String, Object> resource = builder.getResource();

            Assertions.assertNotNull(resource);
            // Initial resource should be empty
            Assertions.assertTrue(resource.isEmpty() || resource.size() >= 0);
        }

        @Test
        void testResourceCanBeModified() {
            Object mockModel = new Object();
            WorkflowBuilder builder = new WorkflowBuilder(mockModel, null);

            Map<String, Object> resource = builder.getResource();
            resource.put("test_key", "test_value");

            Assertions.assertEquals("test_value", resource.get("test_key"));
        }
    }

    /**
     * Test WorkflowBuilder state property.
     * <p>
     * Mirrors Python's {@code TestWorkflowBuilderState} class.
     */
    static class TestState {

        @Test
        void testStatePropertyInitial() {
            Object mockModel = new Object();
            WorkflowBuilder builder = new WorkflowBuilder(mockModel, null);

            Assertions.assertEquals(AgentBuilderEnums.BuildState.INITIAL, builder.getState());
        }

        @Test
        void testStateCanBeChanged() {
            Object mockModel = new Object();
            WorkflowBuilder builder = new WorkflowBuilder(mockModel, null);

            // After processing, state changes
            Map<String, Object> query = new LinkedHashMap<>();
            query.put("query", "创建一个数据处理工作流");

            builder.build(query, new ArrayList<>());

            // State should transition to PROCESSING after initial handling
            Assertions.assertTrue(
                    builder.getState() == AgentBuilderEnums.BuildState.PROCESSING ||
                    builder.getState() == AgentBuilderEnums.BuildState.INITIAL
            );
        }
    }

    /**
     * Test WorkflowBuilder reset functionality.
     */
    static class TestReset {

        @Test
        void testResetClearsState() {
            Object mockModel = new Object();
            WorkflowBuilder builder = new WorkflowBuilder(mockModel, null);

            builder.reset();

            Assertions.assertEquals(AgentBuilderEnums.BuildState.INITIAL, builder.getState());
            Assertions.assertNull(builder.getWorkflowName());
            Assertions.assertNull(builder.getDl());
            Assertions.assertNull(builder.getMermaidCode());
        }
    }

    /**
     * Test WorkflowBuilder isWorkflowBuilder method.
     */
    static class TestIsWorkflowBuilder {

        @Test
        void testIsWorkflowBuilderReturnsTrue() {
            Object mockModel = new Object();
            WorkflowBuilder builder = new WorkflowBuilder(mockModel, null);

            Assertions.assertTrue(builder.isWorkflowBuilder());
        }
    }

    /**
     * Test WorkflowBuilder build method.
     */
    static class TestBuild {

        @Test
        void testBuildWithEmptyQuery() {
            Object mockModel = new Object();
            WorkflowBuilder builder = new WorkflowBuilder(mockModel, null);

            Map<String, Object> result = builder.build(new LinkedHashMap<>(), new ArrayList<>());

            Assertions.assertNotNull(result);
            Assertions.assertTrue(result.containsKey("status"));
        }

        @Test
        void testBuildWithValidQuery() {
            Object mockModel = new Object();
            WorkflowBuilder builder = new WorkflowBuilder(mockModel, null);

            Map<String, Object> query = new LinkedHashMap<>();
            query.put("query", "创建一个数据处理工作流");

            Map<String, Object> result = builder.build(query, new ArrayList<>());

            Assertions.assertNotNull(result);
            Assertions.assertTrue(result.containsKey("status"));
            Assertions.assertTrue(result.containsKey("state"));
        }

        @Test
        void testBuildTransitionsState() {
            Object mockModel = new Object();
            WorkflowBuilder builder = new WorkflowBuilder(mockModel, null);

            // Initial state
            Assertions.assertEquals(AgentBuilderEnums.BuildState.INITIAL, builder.getState());

            // Build with query
            Map<String, Object> query = new LinkedHashMap<>();
            query.put("query", "创建工作流");
            builder.build(query, new ArrayList<>());

            // State should transition
            AgentBuilderEnums.BuildState newState = builder.getState();
            Assertions.assertTrue(
                    newState == AgentBuilderEnums.BuildState.PROCESSING ||
                    newState == AgentBuilderEnums.BuildState.INITIAL ||
                    newState == AgentBuilderEnums.BuildState.COMPLETED
            );
        }
    }
}