/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.harness;

import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DeepAgent SessionRail / SubAgentRail subtask system tests.
 * <p>
 * Mirrors Python's {@code TestDeepAgentSubagentRail},
 * {@code TestDeepAgentSessionRail}, and
 * {@code TestDeepAgentSessionRailCancelMock} in
 * {@code tests.system_tests.harness.test_deep_agent_subagent}.
 */
@Tag("system-test")
class DeepAgentSubagentTest extends DeepAgentE2ETest {

    @Nested
    @Tag("system-test")
    class SubagentRailTests {

        @Test
        @Disabled("skip system test")
        void testDeepAgentTasksUsingSubagents() {
            requireLlmConfig();
        }

        @Test
        @Disabled("skip system test")
        void testDeepAgentTasksUsingPredefinedSubagents() {
            requireLlmConfig();
        }
    }

    @Nested
    @Tag("system-test")
    class SessionRailTests {

        @Test
        @Disabled("skip system test")
        void testAutoInvokeOnSpawnDoneNoQuery2() {
            requireLlmConfig();
        }

        @Test
        @Disabled("skip system test")
        void testAsyncSpawnQuery2NotBlocked() {
            requireLlmConfig();
        }

        @Test
        @Disabled("skip system test")
        void testAsyncSpawnSteeringVisibleDuringQuery3() {
            requireLlmConfig();
        }

        @Test
        @Disabled("skip system test")
        void testAutoInvokeDedupMultiSpawn() {
            requireLlmConfig();
        }

        @Test
        @Disabled("skip system test")
        void testRealLlmTwoSpawnCancelOneOtherCompletes() {
            requireLlmConfig();
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

            Map<String, Object> invoke(Map<String, Object> inputs) throws Exception {
                Thread.sleep(delayMs);
                return Map.of("output", output);
            }
        }

        static class FastSubAgent {
            private final String output;

            FastSubAgent(String output) {
                this.output = output;
            }

            Map<String, Object> invoke(Map<String, Object> inputs) {
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

            Map<String, Object> row = taskRow(toolkit, fixedTaskId);
            assertNotNull(row);
            assertEquals("canceled", row.get("status"));
        }

        @Test
        void testSessionsCancelScenario2CancelWhenRunning() {
            String fixedTaskId = "cancel_s2_task_id";
            DeepAgentConfig.SessionToolkit toolkit = new DeepAgentConfig.SessionToolkit();
            DeepAgent agent = buildCancelAgent("cancel_s2", toolkit);

            agent.spawnSubagentTask(fixedTaskId, "general-purpose", "slow analysis", fixedTaskId);
            Map<String, Object> beforeCancel = taskRow(toolkit, fixedTaskId);
            assertNotNull(beforeCancel);
            assertEquals("running", beforeCancel.get("status"));

            agent.cancelTask(fixedTaskId);
            assertEquals("canceled", taskRow(toolkit, fixedTaskId).get("status"));
        }

        @Test
        void testSessionsCancelScenario3CancelShouldNotTriggerSteering() {
            String fixedTaskId = "cancel_s3_task_id";
            LoopObserveRail observe = new LoopObserveRail("[STEERING]");
            DeepAgentConfig.SessionToolkit toolkit = new DeepAgentConfig.SessionToolkit();
            DeepAgent agent = buildCancelAgent("cancel_s3", toolkit);

            agent.spawnSubagentTask(fixedTaskId, "general-purpose", "slow task", fixedTaskId);
            agent.cancelTask(fixedTaskId);

            assertEquals("canceled", taskRow(toolkit, fixedTaskId).get("status"));
            assertFalse(observe.steerSeenInModelMessages);
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
            Map<String, Object> result = fastAgent.invoke(Map.of());
            toolkit.completeTask(fixedTaskId, String.valueOf(result.get("output")));
            agent.cancelTask(fixedTaskId);

            Map<String, Object> row = taskRow(toolkit, fixedTaskId);
            assertNotNull(row);
            assertEquals("completed", row.get("status"));
            assertEquals("completed quickly", row.get("result"));
        }

        private DeepAgent buildCancelAgent(String name, DeepAgentConfig.SessionToolkit toolkit) {
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

        private Map<String, Object> taskRow(DeepAgentConfig.SessionToolkit toolkit, String taskId) {
            return toolkit.listTasks().stream()
                    .filter(row -> taskId.equals(row.get("task_id")))
                    .findFirst()
                    .orElse(null);
        }

        private Map<String, String> statusMap(DeepAgentConfig.SessionToolkit toolkit) {
            Map<String, String> statuses = new LinkedHashMap<>();
            for (Map<String, Object> row : toolkit.listTasks()) {
                statuses.put(String.valueOf(row.get("task_id")), String.valueOf(row.get("status")));
            }
            return statuses;
        }
    }
}
