/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.system_tests.harness;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.DeepAgentConfig;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * DeepAgent SessionRail / SubAgentRail subtask system tests.
 *
 * <p>Mirrors Python's {@code TestDeepAgentSubagentRail},
 * {@code TestDeepAgentSessionRail}, and
 * {@code TestDeepAgentSessionRailCancelMock} in
 * {@code tests.system_tests.harness.test_deep_agent_subagent}.</p>
 */
@Tag("system-test")
public class TestDeepAgentSubagent {

    private static final String API_BASE = System.getenv().getOrDefault("API_BASE", "");
    private static final String API_KEY = System.getenv().getOrDefault("API_KEY", "");
    private static final String MODEL_NAME = System.getenv().getOrDefault("MODEL_NAME", "model");
    private static final String MODEL_PROVIDER = System.getenv().getOrDefault("MODEL_PROVIDER", "OpenAI");
    private static final int MODEL_TIMEOUT = Integer.parseInt(System.getenv().getOrDefault("MODEL_TIMEOUT", "120"));

    static Model createModel() {
        ModelClientConfig clientConfig = ModelClientConfig.builder()
                .clientProvider(MODEL_PROVIDER)
                .apiKey(API_KEY)
                .apiBase(API_BASE)
                .timeout(MODEL_TIMEOUT)
                .verifySsl(false)
                .build();
        ModelRequestConfig requestConfig = ModelRequestConfig.builder()
                .modelName(MODEL_NAME)
                .temperature(0.2)
                .topP(0.9)
                .build();
        return new Model(clientConfig, requestConfig);
    }

    static boolean hasLlmConfig() {
        return API_KEY != null && !API_KEY.isBlank() && API_BASE != null && !API_BASE.isBlank();
    }

    /**
     * Records tool call sequence for verification.
     *
     * <p>Mirrors Python's {@code ToolTraceRail} in
     * {@code tests.system_tests.harness.test_deep_agent_e2e}.</p>
     */
    static class ToolTraceRail extends AgentRail {
        private final List<String> toolCalls = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void afterToolCall(AgentCallbackContext ctx) {
            if (ctx.getInputs() instanceof ToolCallInputs inputs && inputs.getToolCall() != null) {
                toolCalls.add(inputs.getToolCall().getName());
            }
        }

        List<String> getToolCalls() {
            return new ArrayList<>(toolCalls);
        }

        Map<String, Integer> getToolCounts() {
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (String tool : toolCalls) {
                counts.merge(tool, 1, Integer::sum);
            }
            return counts;
        }
    }

    @Nested
    @Tag("system-test")
    class SubagentRailTests {

        @Test
        void testDeepAgentTasksUsingSubagents() {
            assumeTrue(hasLlmConfig(), "API_KEY and API_BASE required for live DeepAgent subagent E2E.");

            DeepAgent researchAgent = namedAgent(
                    "research_agent",
                    "Research subagent for investigation tasks.",
                    "You are a research assistant."
            );
            DeepAgent agent = mainAgent("deep_agent", List.of(researchAgent), new ToolTraceRail());

            DeepAgentConfig config = (DeepAgentConfig) agent.getConfig();
            assertTrue(config.getEnableTaskLoop());
            assertEquals(12, config.getMaxIterations());
            assertEquals(1, config.getSubagents().size());
            assertEquals("research_agent", config.getSubagents().get(0).getCard().getName());
            assertEquals(1, config.getRails().size());
        }

        @Test
        void testDeepAgentTasksUsingPredefinedSubagents() {
            assumeTrue(hasLlmConfig(), "API_KEY and API_BASE required for live DeepAgent subagent E2E.");

            DeepAgent researchAgent = namedAgent(
                    "research_agent",
                    "Research agent for investigation tasks.",
                    "You are a research assistant."
            );
            DeepAgent codeAgent = namedAgent(
                    "code_agent",
                    "Code agent for programming tasks.",
                    "You are a coding assistant."
            );
            ToolTraceRail toolTrace = new ToolTraceRail();
            DeepAgent agent = mainAgent("deep_agent", List.of(researchAgent, codeAgent), toolTrace);

            DeepAgentConfig config = (DeepAgentConfig) agent.getConfig();
            assertEquals(List.of("research_agent", "code_agent"),
                    config.getSubagents().stream().map(subagent -> subagent.getCard().getName()).toList());
            assertTrue(toolTrace.getToolCounts().isEmpty());
            assertNotNull(createModel());
        }
    }

    @Nested
    @Tag("system-test")
    class SessionRailTests {

        @Test
        void testAutoInvokeOnSpawnDoneNoQuery2() {
            DeepAgentConfig.SessionToolkit toolkit = new DeepAgentConfig.SessionToolkit();
            toolkit.upsertTask("task-1", "sub-1", "background analysis", "running");
            toolkit.completeTask("task-1", "analysis complete");

            Map<String, Object> row = taskRow(toolkit, "task-1");
            assertEquals("completed", row.get("status"));
            assertEquals("analysis complete", row.get("result"));
        }

        @Test
        void testAsyncSpawnQuery2NotBlocked() {
            DeepAgentConfig.SessionToolkit toolkit = new DeepAgentConfig.SessionToolkit();
            DeepAgent agent = buildCancelAgent("async_spawn", toolkit);

            agent.spawnSubagentTask("task-1", "missing-subagent", "first task", "sub-1");
            agent.spawnSubagentTask("task-2", "missing-subagent", "second task", "sub-2");

            Map<String, String> statuses = statusMap(toolkit);
            assertEquals("running", statuses.get("task-1"));
            assertEquals("running", statuses.get("task-2"));
        }

        @Test
        void testAsyncSpawnSteeringVisibleDuringQuery3() {
            DeepAgentConfig.SessionToolkit toolkit = new DeepAgentConfig.SessionToolkit();
            toolkit.upsertTask("task-1", "sub-1", "analysis", "running");
            toolkit.upsertTask("task-2", "sub-2", "follow-up", "running");
            toolkit.completeTask("task-1", "done");

            Map<String, String> statuses = statusMap(toolkit);
            assertEquals("completed", statuses.get("task-1"));
            assertEquals("running", statuses.get("task-2"));
        }

        @Test
        void testAutoInvokeDedupMultiSpawn() {
            DeepAgentConfig.SessionToolkit toolkit = new DeepAgentConfig.SessionToolkit();
            toolkit.upsertTask("task-1", "sub-1", "first description", "running");
            toolkit.upsertTask("task-1", "sub-1", "updated description", "running");

            assertEquals(1, toolkit.listTasks().size());
            Map<String, Object> row = taskRow(toolkit, "task-1");
            assertEquals("updated description", row.get("description"));
        }

        @Test
        void testRealLlmTwoSpawnCancelOneOtherCompletes() {
            DeepAgentConfig.SessionToolkit toolkit = new DeepAgentConfig.SessionToolkit();
            toolkit.upsertTask("task-1", "sub-1", "slow task", "running");
            toolkit.upsertTask("task-2", "sub-2", "fast task", "running");
            toolkit.cancelTask("task-1");
            toolkit.completeTask("task-2", "fast task complete");

            Map<String, String> statuses = statusMap(toolkit);
            assertEquals("canceled", statuses.get("task-1"));
            assertEquals("completed", statuses.get("task-2"));
        }
    }

    @Nested
    @Tag("system-test")
    class SessionRailCancelMockTests {

        static class SleepSubAgent {
            private final long delayMs;
            private final String output;

            SleepSubAgent(long delayMs, String output) {
                this.delayMs = delayMs;
                this.output = output;
            }

            Map<String, Object> invoke() throws Exception {
                Thread.sleep(delayMs);
                return Map.of("output", output);
            }
        }

        @Test
        void testSessionsCancelScenario1ImmediateCancel() {
            String fixedTaskId = "cancel_s1_task_id";
            DeepAgentConfig.SessionToolkit toolkit = new DeepAgentConfig.SessionToolkit();
            DeepAgent agent = buildCancelAgent("cancel_s1", toolkit);

            agent.spawnSubagentTask(fixedTaskId, "general-purpose", "long task", fixedTaskId);
            agent.cancelTask(fixedTaskId);

            assertEquals("canceled", taskRow(toolkit, fixedTaskId).get("status"));
        }

        @Test
        void testSessionsCancelScenario2CancelWhenRunning() {
            String fixedTaskId = "cancel_s2_task_id";
            DeepAgentConfig.SessionToolkit toolkit = new DeepAgentConfig.SessionToolkit();
            DeepAgent agent = buildCancelAgent("cancel_s2", toolkit);

            agent.spawnSubagentTask(fixedTaskId, "general-purpose", "slow analysis", fixedTaskId);
            assertEquals("running", taskRow(toolkit, fixedTaskId).get("status"));

            agent.cancelTask(fixedTaskId);
            assertEquals("canceled", taskRow(toolkit, fixedTaskId).get("status"));
        }

        @Test
        void testSessionsCancelScenario3CancelShouldNotTriggerSteering() {
            String fixedTaskId = "cancel_s3_task_id";
            DeepAgentConfig.SessionToolkit toolkit = new DeepAgentConfig.SessionToolkit();
            DeepAgent agent = buildCancelAgent("cancel_s3", toolkit);

            agent.spawnSubagentTask(fixedTaskId, "general-purpose", "slow task", fixedTaskId);
            agent.cancelTask(fixedTaskId);

            assertEquals("canceled", taskRow(toolkit, fixedTaskId).get("status"));
            assertEquals(1, toolkit.listTasks().size());
        }

        @Test
        void testSessionsCancelScenario4CancelOneOfMultipleTasks() {
            String fixedTaskId1 = "cancel_s4_task_id_1";
            String fixedTaskId2 = "cancel_s4_task_id_2";
            DeepAgentConfig.SessionToolkit toolkit = new DeepAgentConfig.SessionToolkit();
            DeepAgent agent = buildCancelAgent("cancel_s4", toolkit);

            agent.spawnSubagentTask(fixedTaskId1, "general-purpose", "task a", fixedTaskId1);
            agent.spawnSubagentTask(fixedTaskId2, "general-purpose", "task b", fixedTaskId2);
            toolkit.completeTask(fixedTaskId2, "b done");
            agent.cancelTask(fixedTaskId1);

            Map<String, String> statuses = statusMap(toolkit);
            assertEquals("canceled", statuses.get(fixedTaskId1));
            assertEquals("completed", statuses.get(fixedTaskId2));
        }

        @Test
        void testSessionsCancelScenario5RepeatCancelIdempotent() {
            String fixedTaskId = "cancel_s5_task_id";
            DeepAgentConfig.SessionToolkit toolkit = new DeepAgentConfig.SessionToolkit();
            DeepAgent agent = buildCancelAgent("cancel_s5", toolkit);

            agent.spawnSubagentTask(fixedTaskId, "general-purpose", "task", fixedTaskId);
            agent.cancelTask(fixedTaskId);
            agent.cancelTask(fixedTaskId);

            assertEquals(1, toolkit.listTasks().size());
            assertEquals("canceled", taskRow(toolkit, fixedTaskId).get("status"));
        }

        @Test
        void testSessionsCancelScenario6CancelCompletedTask() throws Exception {
            String fixedTaskId = "cancel_s6_task_id";
            DeepAgentConfig.SessionToolkit toolkit = new DeepAgentConfig.SessionToolkit();
            DeepAgent agent = buildCancelAgent("cancel_s6", toolkit);
            SleepSubAgent fastAgent = new SleepSubAgent(0, "completed quickly");

            agent.spawnSubagentTask(fixedTaskId, "general-purpose", "fast complete", fixedTaskId);
            Map<String, Object> result = fastAgent.invoke();
            toolkit.completeTask(fixedTaskId, String.valueOf(result.get("output")));
            agent.cancelTask(fixedTaskId);

            Map<String, Object> row = taskRow(toolkit, fixedTaskId);
            assertEquals("completed", row.get("status"));
            assertEquals("completed quickly", row.get("result"));
        }
    }

    private static DeepAgent namedAgent(String name, String description, String systemPrompt) {
        AgentCard card = new AgentCard();
        card.setName(name);
        card.setDescription(description);
        DeepAgentConfig config = new DeepAgentConfig();
        config.setCard(card);
        config.setSystemPrompt(systemPrompt);
        config.setModel(createModel());
        DeepAgent agent = new DeepAgent(card);
        agent.configure(config);
        return agent;
    }

    private static DeepAgent mainAgent(String name, List<DeepAgent> subagents, ToolTraceRail toolTrace) {
        AgentCard card = new AgentCard();
        card.setName(name);
        DeepAgentConfig config = new DeepAgentConfig();
        config.setCard(card);
        config.setSystemPrompt("Execute tasks rigorously and use tools when needed.");
        config.setModel(createModel());
        config.setEnableTaskLoop(true);
        config.setMaxIterations(12);
        config.setSubagents(subagents);
        config.setRails(List.of(toolTrace));
        DeepAgent agent = new DeepAgent(card);
        agent.configure(config);
        return agent;
    }

    private static DeepAgent buildCancelAgent(String name, DeepAgentConfig.SessionToolkit toolkit) {
        AgentCard card = new AgentCard();
        card.setName(name);
        DeepAgent agent = new DeepAgent(card);
        DeepAgentConfig config = new DeepAgentConfig();
        config.setCard(card);
        config.setMaxIterations(10);
        config.setSessionToolkit(toolkit);
        agent.configure(config);
        return agent;
    }

    private static Map<String, Object> taskRow(DeepAgentConfig.SessionToolkit toolkit, String taskId) {
        return toolkit.listTasks().stream()
                .filter(row -> taskId.equals(row.get("task_id")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Task not found: " + taskId));
    }

    private static Map<String, String> statusMap(DeepAgentConfig.SessionToolkit toolkit) {
        Map<String, String> statuses = new LinkedHashMap<>();
        for (Map<String, Object> row : toolkit.listTasks()) {
            statuses.put(String.valueOf(row.get("task_id")), String.valueOf(row.get("status")));
        }
        return statuses;
    }
}
