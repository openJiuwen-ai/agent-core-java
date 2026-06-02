/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.checkpointer;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.session.config.Config;
import com.openjiuwen.core.session.internal.AgentSession;
import com.openjiuwen.core.workflow.WorkflowCard;
import com.openjiuwen.core.workflow.WorkflowUtils;
import com.openjiuwen.extensions.checkpointer.redis.RedisCheckpointer;
import com.openjiuwen.extensions.store.kv.RedisStore;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test integration runner functionality.
 *
 * <p>Mirrors the Redis-backed recovery path from Python's
 * {@code tests_unit_tests.extensions.checkpointer.test_integration_runner}.
 */
class TestIntegrationRunner {

    @Nested
    class TestRunnerOperations {

        @Test
        void testRunIntegration() {
            RedisCheckpointer checkpointer = checkpointer();
            AgentSession session = session("session-1", "agent-1", checkpointer);

            assertDoesNotThrow(() -> checkpointer.preAgentExecute(session, null));
            assertFalse(checkpointer.sessionExists("session-1"));
        }

        @Test
        void testRunWithCheckpointer() {
            RedisCheckpointer checkpointer = checkpointer();
            AgentSession interrupted = session("session-1", "agent-1", checkpointer);
            interrupted.state().update(Map.of("step", "waiting"));
            checkpointer.interruptAgentExecute(interrupted);

            AgentSession resumed = session("session-1", "agent-1", checkpointer);
            checkpointer.preAgentExecute(resumed, "continue");

            assertEquals("waiting", resumed.state().get("step"));
            assertEquals(List.of("continue"), resumed.state().get(Constant.INTERACTIVE_INPUT));
        }

        @Test
        void testWorkflowAgentInvokeWithInterruptRecovery() {
            RedisCheckpointer checkpointer = checkpointer();
            AgentSession interrupted = session("conversation-1", "workflow-agent", checkpointer);
            interrupted.state().update(Map.of("interaction", "weather_city"));

            checkpointer.interruptAgentExecute(interrupted);
            AgentSession resumed = session("conversation-1", "workflow-agent", checkpointer);
            checkpointer.preAgentExecute(resumed, Map.of("query", "上海", "conversation_id", "conversation-1"));

            assertEquals("weather_city", resumed.state().get("interaction"));
            assertEquals(
                    List.of(Map.of("query", "上海", "conversation_id", "conversation-1")),
                    resumed.state().get(Constant.INTERACTIVE_INPUT));
        }

        @Test
        void testRedisCheckpointerInitialization() {
            RedisCheckpointer checkpointer = checkpointer();

            assertNotNull(checkpointer);
            assertEquals("RedisCheckpointer", checkpointer.getClass().getSimpleName());
        }

        @Test
        void testWorkflowRegistration() {
            WorkflowCard card = WorkflowCard.builder()
                    .id("test_interrupt_workflow")
                    .name("interrupt_test")
                    .version("1.0")
                    .build();
            Map<String, WorkflowCard> registered = Map.of(
                    WorkflowUtils.generateWorkflowKey(card.getId(), card.getVersion()), card);

            WorkflowCard workflow = registered.get(WorkflowUtils.generateWorkflowKey("test_interrupt_workflow", "1.0"));

            assertNotNull(workflow);
            assertEquals("interrupt_test", workflow.getName());
        }
    }

    private RedisCheckpointer checkpointer() {
        return new RedisCheckpointer(new RedisStore(new TestAgentStorage.FakeRedisClient()), null);
    }

    private AgentSession session(String sessionId, String agentId, RedisCheckpointer checkpointer) {
        Config config = new Config();
        config.setAgentConfig(new Config.MetadataLike(agentId, "agent", "invoke"));
        return new AgentSession(sessionId, config, checkpointer);
    }
}
