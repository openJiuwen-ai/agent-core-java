/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.trajectory;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mirrors Python's tests.unit_tests.agent_evolving.trajectory.test_types.
 * Tests for trajectory types.
 */
class TrajectoryTypesTest {

    private TrajectoryStep makeStep(String kind, Object detail, Object error, Map<String, Object> meta) {
        return TrajectoryStep.builder()
                .kind(kind)
                .error(error)
                .detail(detail)
                .meta(meta != null ? meta : new HashMap<>())
                .build();
    }

    private TrajectoryStep makeLLMStep(String model, List<Map<String, Object>> messages,
                                       Map<String, Object> response, List<Map<String, Object>> tools,
                                       Map<String, Object> usage) {
        LLMCallDetail detail = LLMCallDetail.builder()
                .model(model)
                .messages(messages != null ? messages : List.of(Map.of("role", "user", "content", "hello")))
                .response(response)
                .tools(tools)
                .usage(usage)
                .build();
        return makeStep("llm", detail, null, null);
    }

    private TrajectoryStep makeToolStep(String toolName, Object callArgs, Object callResult,
                                        String toolDescription, Map<String, Object> toolSchema) {
        ToolCallDetail detail = ToolCallDetail.builder()
                .toolName(toolName)
                .callArgs(callArgs)
                .callResult(callResult)
                .toolDescription(toolDescription)
                .toolSchema(toolSchema)
                .build();
        return makeStep("tool", detail, null, null);
    }

    private Trajectory makeTrajectory(String caseId, List<TrajectoryStep> steps, String sessionId) {
        return Trajectory.builder()
                .executionId("exec1")
                .source("offline")
                .caseId(caseId)
                .sessionId(sessionId != null ? sessionId : caseId)
                .steps(steps != null ? steps : List.of())
                .build();
    }

    // === LLMCallDetail Tests ===

    @Test
    void llmCallDetailMinimalCreation() {
        LLMCallDetail detail = LLMCallDetail.builder()
                .model("gpt-4")
                .messages(List.of(Map.of("role", "user", "content", "hello")))
                .build();

        assertEquals("gpt-4", detail.getModel());
        assertEquals(1, detail.getMessages().size());
        assertNull(detail.getResponse());
        assertNull(detail.getTools());
        assertNull(detail.getUsage());
    }

    @Test
    void llmCallDetailFullCreation() {
        Map<String, Object> usage = new HashMap<>();
        usage.put("prompt_tokens", 10);
        usage.put("completion_tokens", 5);

        LLMCallDetail detail = LLMCallDetail.builder()
                .model("gpt-4")
                .messages(List.of(Map.of("role", "user", "content", "hello")))
                .response(Map.of("content", "response"))
                .tools(List.of(Map.of("name", "tool1")))
                .usage(usage)
                .build();

        assertEquals("gpt-4", detail.getModel());
        assertEquals(Map.of("content", "response"), detail.getResponse());
        assertEquals(List.of(Map.of("name", "tool1")), detail.getTools());
        assertEquals(10, ((Number) detail.getUsage().get("prompt_tokens")).intValue());
        assertEquals(5, ((Number) detail.getUsage().get("completion_tokens")).intValue());
    }

    // === ToolCallDetail Tests ===

    @Test
    void toolCallDetailMinimalCreation() {
        ToolCallDetail detail = ToolCallDetail.builder()
                .toolName("test_tool")
                .build();

        assertEquals("test_tool", detail.getToolName());
        assertNull(detail.getCallArgs());
        assertNull(detail.getCallResult());
    }

    @Test
    void toolCallDetailFullCreation() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");

        ToolCallDetail detail = ToolCallDetail.builder()
                .toolName("test_tool")
                .callArgs(Map.of("arg", "value"))
                .callResult(Map.of("result", "success"))
                .toolDescription("A test tool")
                .toolSchema(schema)
                .build();

        assertEquals("test_tool", detail.getToolName());
        assertEquals(Map.of("arg", "value"), detail.getCallArgs());
        assertEquals(Map.of("result", "success"), detail.getCallResult());
        assertEquals("A test tool", detail.getToolDescription());
        assertEquals(Map.of("type", "object"), detail.getToolSchema());
    }

    // === TrajectoryStep Tests ===

    @Test
    void trajectoryStepCreation() {
        TrajectoryStep step = makeStep("llm", null, null, null);

        assertEquals("llm", step.getKind());
        assertNull(step.getError());
        assertNull(step.getDetail());
        assertEquals(Map.of(), step.getMeta());
    }

    @Test
    void trajectoryStepLLMStep() {
        TrajectoryStep step = makeLLMStep("gpt-4", null, null, null, null);

        assertEquals("llm", step.getKind());
        assertInstanceOf(LLMCallDetail.class, step.getDetail());
        assertEquals("gpt-4", ((LLMCallDetail) step.getDetail()).getModel());
    }

    @Test
    void trajectoryStepToolStep() {
        TrajectoryStep step = makeToolStep("test_tool", Map.of("arg", "value"), Map.of("result", "success"),
                null, null);

        assertEquals("tool", step.getKind());
        ToolCallDetail detail = assertInstanceOf(ToolCallDetail.class, step.getDetail());
        assertEquals("test_tool", detail.getToolName());
        assertEquals(Map.of("arg", "value"), detail.getCallArgs());
    }

    @Test
    void trajectoryStepWithError() {
        Map<String, Object> errorInfo = new HashMap<>();
        errorInfo.put("message", "Error occurred");

        TrajectoryStep step = TrajectoryStep.builder()
                .kind("llm")
                .error(errorInfo)
                .meta(new HashMap<>())
                .build();

        assertNotNull(step.getError());
    }

    @Test
    void trajectoryStepWithMeta() {
        TrajectoryStep step = makeStep("llm", null, null, Map.of(
                "operator_id", "op1",
                "agent_id", "agent1",
                "span_name", "test_span"
        ));

        assertEquals("op1", step.getMeta().get("operator_id"));
        assertEquals("agent1", step.getMeta().get("agent_id"));
        assertEquals("test_span", step.getMeta().get("span_name"));
    }

    @Test
    void trajectoryStepWithRlFields() {
        TrajectoryStep step = TrajectoryStep.builder()
                .kind("llm")
                .reward(1.0)
                .logprobs(List.of(-0.5, -0.3))
                .promptTokenIds(List.of(1, 2, 3))
                .completionTokenIds(List.of(101, 102, 103))
                .build();

        assertEquals(1.0, step.getReward());
        assertEquals(List.of(-0.5, -0.3), step.getLogprobs());
        assertEquals(List.of(1, 2, 3), step.getPromptTokenIds());
        assertEquals(List.of(101, 102, 103), step.getCompletionTokenIds());
    }

    // === Trajectory Tests ===

    @Test
    void trajectoryMinimalCreation() {
        Trajectory traj = Trajectory.builder()
                .executionId("exec1")
                .steps(List.of())
                .build();

        assertEquals("exec1", traj.getExecutionId());
        assertTrue(traj.getSteps().isEmpty());
        assertEquals("offline", traj.getSource());
    }

    @Test
    void trajectoryWithSteps() {
        TrajectoryStep step1 = makeLLMStep("gpt-4", null, null, null, null);
        TrajectoryStep step2 = makeToolStep("tool1", null, null, null, null);

        Trajectory traj = Trajectory.builder()
                .executionId("exec1")
                .steps(List.of(step1, step2))
                .caseId("case1")
                .build();

        assertEquals(2, traj.getSteps().size());
        assertEquals("case1", traj.getCaseId());
    }

    @Test
    void trajectoryWithCost() {
        Map<String, Integer> cost = new HashMap<>();
        cost.put("input_tokens", 100);
        cost.put("output_tokens", 50);

        Trajectory traj = Trajectory.builder()
                .executionId("exec1")
                .steps(List.of())
                .cost(cost)
                .build();

        assertNotNull(traj.getCost());
        assertEquals(100, traj.getCost().get("input_tokens"));
    }

    @Test
    void trajectoryOnlineTrajectory() {
        Trajectory traj = Trajectory.builder()
                .executionId("exec-online")
                .source("online")
                .sessionId("session-123")
                .caseId(null)
                .steps(List.of())
                .build();

        assertEquals("online", traj.getSource());
        assertEquals("session-123", traj.getSessionId());
        assertNull(traj.getCaseId());
    }

    @Test
    void updateKeyTupleCreation() {
        UpdateKey key = UpdateKey.of("op1", "system_prompt");

        assertEquals(UpdateKey.of("op1", "system_prompt"), key);
        assertEquals("op1", key.getOperatorId());
        assertEquals("system_prompt", key.getTarget());
    }

    @Test
    void updatesDictCreation() {
        Updates updates = new Updates();
        updates.put("op1", "system_prompt", "new prompt");
        updates.put("op1", "user_prompt", "new user");

        assertTrue(updates.containsKey(UpdateKey.of("op1", "system_prompt")));
        assertEquals("new prompt", updates.get("op1", "system_prompt"));
    }
}
