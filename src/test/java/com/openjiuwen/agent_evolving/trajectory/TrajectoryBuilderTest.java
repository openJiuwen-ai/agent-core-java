/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.trajectory;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link TrajectoryBuilder}.
 * <p>
 * Mirrors Python's {@code tests/unit_tests/agent_evolving/trajectory/test_builder.py}.
 * </p>
 */
class TrajectoryBuilderTest {

    private TrajectoryStep makeStep(StepKind kind, Object detail, Object error) {
        return TrajectoryStep.builder()
                .kind(kind != null ? kind.value() : "llm")
                .detail(detail)
                .error(error)
                .meta(new HashMap<>())
                .build();
    }

    @Test
    void builderInitialization() {
        TrajectoryBuilder builder = TrajectoryBuilder.builder()
                .sessionId("session_123")
                .source("online")
                .caseId("case_456")
                .build();

        assertEquals("session_123", builder.getSessionId());
        assertEquals("online", builder.getSource());
        assertEquals("case_456", builder.getCaseId());
        assertTrue(builder.getSteps().isEmpty());
        assertEquals(0, builder.getCost().get("input_tokens"));
        assertEquals(0, builder.getCost().get("output_tokens"));
    }

    @Test
    void builderWithoutCaseId() {
        TrajectoryBuilder builder = TrajectoryBuilder.builder()
                .sessionId("session_123")
                .source("offline")
                .build();

        assertNull(builder.getCaseId());
    }

    @Test
    void builderRejectsNonPositiveMaxSteps() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> TrajectoryBuilder.builder().sessionId("s1").source("online").maxSteps(0).build());

        assertEquals("max_steps must be >= 1", error.getMessage());
    }

    @Test
    void recordSingleStep() {
        TrajectoryBuilder builder = TrajectoryBuilder.builder()
                .sessionId("s1")
                .source("online")
                .build();

        TrajectoryStep step = makeStep(StepKind.LLM, null, null);
        builder.recordStep(step);

        assertEquals(1, builder.getSteps().size());
        assertEquals(StepKind.LLM, builder.getSteps().getFirst().getKindEnum());
    }

    @Test
    void recordMultipleSteps() {
        TrajectoryBuilder builder = TrajectoryBuilder.builder()
                .sessionId("s1")
                .source("online")
                .build();

        TrajectoryStep step1 = makeStep(StepKind.LLM, null, null);
        TrajectoryStep step2 = makeStep(StepKind.TOOL, null, null);
        TrajectoryStep step3 = makeStep(StepKind.LLM, null, null);

        builder.recordStep(step1);
        builder.recordStep(step2);
        builder.recordStep(step3);

        assertEquals(3, builder.getSteps().size());
        assertEquals(StepKind.LLM, builder.getSteps().get(0).getKindEnum());
        assertEquals(StepKind.TOOL, builder.getSteps().get(1).getKindEnum());
        assertEquals(StepKind.LLM, builder.getSteps().get(2).getKindEnum());
    }

    @Test
    void recordStepRetainsOnlyRecentWindowWhenMaxStepsSet() {
        TrajectoryBuilder builder = TrajectoryBuilder.builder()
                .sessionId("s1")
                .source("online")
                .maxSteps(2)
                .build();

        builder.recordStep(TrajectoryStep.builder().kind("llm").meta(Map.of("idx", 1)).build());
        builder.recordStep(TrajectoryStep.builder().kind("tool").meta(Map.of("idx", 2)).build());
        builder.recordStep(TrajectoryStep.builder().kind("llm").meta(Map.of("idx", 3)).build());

        assertEquals(2, builder.getSteps().size());
        assertEquals(2, builder.getSteps().get(0).getMeta().get("idx"));
        assertEquals(3, builder.getSteps().get(1).getMeta().get("idx"));
    }

    @Test
    void buildReturnsTrajectory() {
        TrajectoryBuilder builder = TrajectoryBuilder.builder()
                .sessionId("session_123")
                .source("online")
                .caseId("case_456")
                .build();

        TrajectoryStep step = makeStep(StepKind.LLM, null, null);
        builder.recordStep(step);

        Trajectory trajectory = builder.build();

        assertEquals("session_123", trajectory.getSessionId());
        assertEquals("online", trajectory.getSource());
        assertEquals("case_456", trajectory.getCaseId());
        assertEquals(1, trajectory.getSteps().size());
        assertNotNull(trajectory.getExecutionId());
    }

    @Test
    void buildWithEmptySteps() {
        TrajectoryBuilder builder = TrajectoryBuilder.builder()
                .sessionId("s1")
                .source("online")
                .build();

        Trajectory trajectory = builder.build();

        assertTrue(trajectory.getSteps().isEmpty());
        assertNull(trajectory.getCost());
    }

    @Test
    void costAccumulationFromLlmDetail() {
        TrajectoryBuilder builder = TrajectoryBuilder.builder()
                .sessionId("s1")
                .source("online")
                .build();

        Map<String, Object> usage = new HashMap<>();
        usage.put("prompt_tokens", 10);
        usage.put("completion_tokens", 5);

        LLMCallDetail detail = LLMCallDetail.builder()
                .model("gpt-4")
                .messages(List.of(Map.of("role", "user", "content", "hi")))
                .usage(usage)
                .build();

        TrajectoryStep step = TrajectoryStep.builder()
                .kind("llm")
                .detail(detail)
                .meta(new HashMap<>())
                .build();

        builder.recordStep(step);

        Trajectory trajectory = builder.build();

        assertNotNull(trajectory.getCost());
        assertEquals(10, trajectory.getCost().get("input_tokens"));
        assertEquals(5, trajectory.getCost().get("output_tokens"));
    }

    @Test
    void costAccumulationMultipleLlmSteps() {
        TrajectoryBuilder builder = TrajectoryBuilder.builder()
                .sessionId("s1")
                .source("online")
                .build();

        Map<String, Object> usage1 = new HashMap<>();
        usage1.put("prompt_tokens", 10);
        usage1.put("completion_tokens", 5);

        Map<String, Object> usage2 = new HashMap<>();
        usage2.put("prompt_tokens", 20);
        usage2.put("completion_tokens", 10);

        LLMCallDetail detail1 = LLMCallDetail.builder()
                .model("gpt-4")
                .messages(List.of(Map.of("role", "user", "content", "hi")))
                .usage(usage1)
                .build();

        LLMCallDetail detail2 = LLMCallDetail.builder()
                .model("gpt-4")
                .messages(List.of(Map.of("role", "user", "content", "hello")))
                .usage(usage2)
                .build();

        builder.recordStep(TrajectoryStep.builder().kind("llm").detail(detail1).meta(new HashMap<>()).build());
        builder.recordStep(makeStep(StepKind.TOOL, null, null));
        builder.recordStep(TrajectoryStep.builder().kind("llm").detail(detail2).meta(new HashMap<>()).build());

        Trajectory trajectory = builder.build();

        assertNotNull(trajectory.getCost());
        assertEquals(30, trajectory.getCost().get("input_tokens"));
        assertEquals(15, trajectory.getCost().get("output_tokens"));
    }

    @Test
    void costNotAccumulatedForToolSteps() {
        TrajectoryBuilder builder = TrajectoryBuilder.builder()
                .sessionId("s1")
                .source("online")
                .build();

        ToolCallDetail detail = ToolCallDetail.builder()
                .toolName("test_tool")
                .toolDescription("A test tool")
                .build();

        TrajectoryStep step = TrajectoryStep.builder()
                .kind("tool")
                .detail(detail)
                .meta(new HashMap<>())
                .build();

        builder.recordStep(step);

        Trajectory trajectory = builder.build();

        assertNull(trajectory.getCost());
    }

    @Test
    void costNotAccumulatedWithoutUsage() {
        TrajectoryBuilder builder = TrajectoryBuilder.builder()
                .sessionId("s1")
                .source("online")
                .build();

        LLMCallDetail detail = LLMCallDetail.builder()
                .model("gpt-4")
                .messages(List.of(Map.of("role", "user", "content", "hi")))
                .usage(null)
                .build();

        TrajectoryStep step = TrajectoryStep.builder()
                .kind("llm")
                .detail(detail)
                .meta(new HashMap<>())
                .build();

        builder.recordStep(step);

        Trajectory trajectory = builder.build();

        assertNull(trajectory.getCost());
    }

    @Test
    void differentSources() {
        TrajectoryBuilder onlineBuilder = TrajectoryBuilder.builder()
                .sessionId("s1")
                .source("online")
                .build();

        TrajectoryBuilder offlineBuilder = TrajectoryBuilder.builder()
                .sessionId("s2")
                .source("offline")
                .build();

        Trajectory onlineTraj = onlineBuilder.build();
        Trajectory offlineTraj = offlineBuilder.build();

        assertEquals("online", onlineTraj.getSource());
        assertEquals("offline", offlineTraj.getSource());
    }
}
