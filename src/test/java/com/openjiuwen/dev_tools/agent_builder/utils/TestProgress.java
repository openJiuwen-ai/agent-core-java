/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.utils;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for progress utilities.
 * <p>
 * Mirrors Python's tests/unit_tests/dev_tools/agent_builder/utils/test_progress.py
 */
class TestProgress {

    @Nested
    class TestProgressStep {

        @Test
        void testProgressStepCreation() {
            ProgressStep step = new ProgressStep(
                    AgentBuilderEnums.ProgressStage.INITIALIZING,
                    AgentBuilderEnums.ProgressStatus.RUNNING,
                    "Starting build..."
            );

            assertEquals(AgentBuilderEnums.ProgressStage.INITIALIZING, step.getStage());
            assertEquals(AgentBuilderEnums.ProgressStatus.RUNNING, step.getStatus());
            assertEquals("Starting build...", step.getMessage());
            assertEquals(Map.of(), step.getDetails());
            assertNull(step.getDuration());
            assertNull(step.getError());
        }

        @Test
        void testProgressStepWithDetails() {
            Map<String, Object> details = Map.of("key", "value");
            ProgressStep step = new ProgressStep(
                    AgentBuilderEnums.ProgressStage.CLARIFYING,
                    AgentBuilderEnums.ProgressStatus.SUCCESS,
                    "Completed",
                    details
            );

            assertEquals(details, step.getDetails());
        }

        @Test
        void testProgressStepToDict() {
            ProgressStep step = new ProgressStep(
                    AgentBuilderEnums.ProgressStage.INITIALIZING,
                    AgentBuilderEnums.ProgressStatus.RUNNING,
                    "Starting",
                    Map.of("count", 1),
                    1.5,
                    null
            );

            Map<String, Object> result = step.toDict();
            assertEquals("initializing", result.get("stage"));
            assertEquals("running", result.get("status"));
            assertEquals("Starting", result.get("message"));
            assertEquals(Map.of("count", 1), result.get("details"));
            assertEquals(1.5, result.get("duration"));
            assertTrue(result.containsKey("timestamp"));
        }

        @Test
        void testProgressStepWithError() {
            ProgressStep step = new ProgressStep(
                    AgentBuilderEnums.ProgressStage.ERROR,
                    AgentBuilderEnums.ProgressStatus.FAILED,
                    "Failed",
                    Map.of(),
                    null,
                    "Test error"
            );

            assertEquals("Test error", step.getError());
            assertEquals("Test error", step.toDict().get("error"));
        }
    }

    @Nested
    class TestBuildProgress {

        @Test
        void testBuildProgressCreation() {
            BuildProgress progress = new BuildProgress(
                    "test_session",
                    "llm_agent",
                    AgentBuilderEnums.ProgressStage.INITIALIZING,
                    AgentBuilderEnums.ProgressStatus.PENDING,
                    "Initializing..."
            );

            assertEquals("test_session", progress.getSessionId());
            assertEquals("llm_agent", progress.getAgentType());
            assertEquals(AgentBuilderEnums.ProgressStage.INITIALIZING, progress.getCurrentStage());
            assertEquals(AgentBuilderEnums.ProgressStatus.PENDING, progress.getCurrentStatus());
            assertEquals(List.of(), progress.getSteps());
            assertEquals(0.0, progress.getOverallProgress());
        }

        @Test
        void testBuildProgressToDict() {
            BuildProgress progress = new BuildProgress(
                    "test_session",
                    "workflow",
                    AgentBuilderEnums.ProgressStage.COMPLETED,
                    AgentBuilderEnums.ProgressStatus.SUCCESS,
                    "Done"
            );

            Map<String, Object> result = progress.toDict();
            assertEquals("test_session", result.get("session_id"));
            assertEquals("workflow", result.get("agent_type"));
            assertEquals("completed", result.get("current_stage"));
            assertEquals("success", result.get("current_status"));
            assertEquals("Done", result.get("current_message"));
            assertTrue(result.containsKey("start_time"));
            assertTrue(result.containsKey("last_update_time"));
        }

        @Test
        void testBuildProgressWithSteps() {
            ProgressStep step1 = new ProgressStep(
                    AgentBuilderEnums.ProgressStage.INITIALIZING,
                    AgentBuilderEnums.ProgressStatus.SUCCESS,
                    "Init done"
            );
            ProgressStep step2 = new ProgressStep(
                    AgentBuilderEnums.ProgressStage.CLARIFYING,
                    AgentBuilderEnums.ProgressStatus.RUNNING,
                    "Clarifying..."
            );
            BuildProgress progress = new BuildProgress(
                    "test",
                    "llm_agent",
                    AgentBuilderEnums.ProgressStage.CLARIFYING,
                    AgentBuilderEnums.ProgressStatus.RUNNING,
                    "Processing",
                    List.of(step1, step2)
            );

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> steps = (List<Map<String, Object>>) progress.toDict().get("steps");
            assertEquals(2, steps.size());
        }
    }

    @Nested
    class TestProgressReporter {

        @Test
        void testProgressReporterCreation() {
            ProgressReporter reporter = new ProgressReporter("session_123", "llm_agent");

            assertEquals("session_123", reporter.getSessionId());
            assertEquals("llm_agent", reporter.getAgentType());
            assertEquals(AgentBuilderEnums.ProgressStage.INITIALIZING, reporter.getProgress().getCurrentStage());
            assertEquals(AgentBuilderEnums.ProgressStatus.PENDING, reporter.getProgress().getCurrentStatus());
        }

        @Test
        void testAddCallback() {
            ProgressReporter reporter = new ProgressReporter("session_123", "llm_agent");
            List<String> callbackCalled = new ArrayList<>();

            reporter.addCallback(progress -> callbackCalled.add(progress.getSessionId()));
            reporter.startStage(AgentBuilderEnums.ProgressStage.CLARIFYING, "Test message");

            assertEquals(1, callbackCalled.size());
            assertEquals("session_123", callbackCalled.get(0));
        }

        @Test
        void testRemoveCallback() {
            ProgressReporter reporter = new ProgressReporter("session_123", "llm_agent");
            List<Integer> callbackCalled = new ArrayList<>();
            Consumer<BuildProgress> callback = progress -> callbackCalled.add(1);

            reporter.addCallback(callback);
            reporter.removeCallback(callback);
            reporter.startStage(AgentBuilderEnums.ProgressStage.CLARIFYING, "Test message");

            assertEquals(0, callbackCalled.size());
        }

        @Test
        void testStartStage() {
            ProgressReporter reporter = new ProgressReporter("session_123", "llm_agent");
            reporter.startStage(
                    AgentBuilderEnums.ProgressStage.CLARIFYING,
                    "Clarifying requirements...",
                    Map.of("query_length", 100),
                    20.0
            );

            assertEquals(AgentBuilderEnums.ProgressStage.CLARIFYING, reporter.getProgress().getCurrentStage());
            assertEquals(AgentBuilderEnums.ProgressStatus.RUNNING, reporter.getProgress().getCurrentStatus());
            assertEquals("Clarifying requirements...", reporter.getProgress().getCurrentMessage());
            assertEquals(20.0, reporter.getProgress().getOverallProgress());
        }

        @Test
        void testCompleteStage() {
            ProgressReporter reporter = new ProgressReporter("session_123", "llm_agent");
            reporter.startStage(AgentBuilderEnums.ProgressStage.CLARIFYING, "Clarifying...");
            reporter.completeStage("Clarification complete", Map.of("found", 5));

            assertEquals(AgentBuilderEnums.ProgressStatus.SUCCESS, reporter.getProgress().getCurrentStatus());
        }

        @Test
        void testFailStage() {
            ProgressReporter reporter = new ProgressReporter("session_123", "llm_agent");
            reporter.startStage(AgentBuilderEnums.ProgressStage.CLARIFYING, "Clarifying...");
            reporter.failStage("Test error", "Error occurred");

            assertEquals(AgentBuilderEnums.ProgressStatus.FAILED, reporter.getProgress().getCurrentStatus());
            assertEquals("Test error", reporter.getProgress().getError());
        }

        @Test
        void testComplete() {
            ProgressReporter reporter = new ProgressReporter("session_123", "llm_agent");
            reporter.startStage(AgentBuilderEnums.ProgressStage.COMPLETED, "Finishing...");
            reporter.complete("Build completed successfully");

            assertEquals(AgentBuilderEnums.ProgressStage.COMPLETED, reporter.getProgress().getCurrentStage());
            assertEquals(AgentBuilderEnums.ProgressStatus.SUCCESS, reporter.getProgress().getCurrentStatus());
            assertEquals(100.0, reporter.getProgress().getOverallProgress());
        }

        @Test
        void testMultipleStages() {
            ProgressReporter reporter = new ProgressReporter("session_123", "llm_agent");

            reporter.startStage(AgentBuilderEnums.ProgressStage.INITIALIZING, "Init...");
            assertEquals(AgentBuilderEnums.ProgressStage.INITIALIZING, reporter.getProgress().getCurrentStage());

            reporter.completeStage("Init done");
            reporter.startStage(AgentBuilderEnums.ProgressStage.CLARIFYING, "Clarifying...");
            assertEquals(AgentBuilderEnums.ProgressStage.CLARIFYING, reporter.getProgress().getCurrentStage());

            reporter.completeStage("Clarify done");
            reporter.startStage(AgentBuilderEnums.ProgressStage.GENERATING_CONFIG, "Generating...");
            assertEquals(AgentBuilderEnums.ProgressStage.GENERATING_CONFIG, reporter.getProgress().getCurrentStage());
        }
    }
}
