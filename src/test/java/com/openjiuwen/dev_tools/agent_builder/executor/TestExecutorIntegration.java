/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.executor;

import com.openjiuwen.dev_tools.agent_builder.builders.BaseAgentBuilder;
import com.openjiuwen.dev_tools.agent_builder.utils.AgentBuilderEnums;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * System tests for agent_builder executor module.
 * <p>
 * Mirrors Python's {@code test_executor_integration.py} in
 * {@code tests/system_tests/dev_tools/agent_builder/executor/test_executor_integration.py}.
 */
class TestExecutorIntegration {

    @Nested
    class TestHistoryManagerIntegration {

        @Test
        void testHistoryManagerMultiSessionWorkflow() {
            HistoryManager manager1 = new HistoryManager();
            HistoryManager manager2 = new HistoryManager();

            manager1.addMessage("Create an assistant", "user");
            manager1.addMessage("Please tell me the assistant name", "assistant");
            manager1.addMessage("Call it helper", "user");

            manager2.addMessage("Create a workflow", "user");
            manager2.addMessage("Please describe the workflow requirement", "assistant");

            assertEquals(3, manager1.getHistory().size());
            assertEquals(2, manager2.getHistory().size());
            assertEquals("Create an assistant", manager1.getHistory().get(0).get("content"));
            assertEquals("Create a workflow", manager2.getHistory().get(0).get("content"));
        }

        @Test
        void testHistoryManagerWithLimit() {
            HistoryManager manager = new HistoryManager();
            for (int i = 0; i < 10; i++) {
                manager.addMessage("Message " + i, "user");
            }

            List<Map<String, Object>> recentHistory = manager.getLatestKMessages(5);

            assertEquals(5, recentHistory.size());
            assertEquals("Message 5", recentHistory.get(0).get("content"));
        }

        @Test
        void testHistoryCacheMaxSizeEnforcement() {
            HistoryManager.HistoryCache cache = new HistoryManager.HistoryCache(5);
            for (int i = 0; i < 10; i++) {
                cache.addMessage(new HistoryManager.DialogueMessage(
                        "Message " + i, "user", OffsetDateTime.now(ZoneOffset.UTC)));
            }

            List<Map<String, Object>> history = cache.getMessages(-1);

            assertEquals(5, history.size());
            assertEquals("Message 5", history.get(0).get("content"));
        }

        @Test
        void testDialogueMessageCreation() {
            HistoryManager.DialogueMessage userMessage =
                    new HistoryManager.DialogueMessage("User message", "user", OffsetDateTime.now(ZoneOffset.UTC));
            HistoryManager.DialogueMessage assistantMessage =
                    new HistoryManager.DialogueMessage("Assistant reply", "assistant", OffsetDateTime.now(ZoneOffset.UTC));

            assertEquals("user", userMessage.getRole());
            assertEquals("User message", userMessage.getContent());
            assertEquals("assistant", assistantMessage.getRole());
            assertEquals("Assistant reply", assistantMessage.getContent());
        }

        @Test
        void testDialogueMessageToDict() {
            HistoryManager.DialogueMessage message =
                    new HistoryManager.DialogueMessage("Test message", "user", OffsetDateTime.now(ZoneOffset.UTC));

            Map<String, Object> messageDict = message.toDict();

            assertEquals("user", messageDict.get("role"));
            assertEquals("Test message", messageDict.get("content"));
            assertFalse(messageDict.containsKey("timestamp"));
        }
    }

    @Nested
    class TestHistoryManagerPersistence {

        @Test
        void testSessionClearAndRecreate() {
            HistoryManager manager = new HistoryManager();
            manager.addMessage("Message 1", "user");
            manager.addMessage("Reply 1", "assistant");

            assertEquals(2, manager.getHistory().size());

            manager.clear();

            assertEquals(0, manager.getHistory().size());

            manager.addMessage("New message", "user");

            assertEquals(1, manager.getHistory().size());
        }

        @Test
        void testMessageTimestampOrdering() {
            HistoryManager manager = new HistoryManager();
            manager.addMessage("Message 1", "user");
            manager.addMessage("Message 2", "user");

            List<Map<String, Object>> history = manager.getHistory();

            assertEquals("Message 1", history.get(0).get("content"));
            assertEquals("Message 2", history.get(1).get("content"));
        }
    }

    @Nested
    class TestExecutorIntegrationInner {

        @Test
        void testExecutorWithRealHistoryManager() {
            Map<String, HistoryManager> historyManagerMap = new HashMap<>();
            Map<String, BaseAgentBuilder> agentBuilderMap = new HashMap<>();
            agentBuilderMap.put("session_001", new RecordingBuilder());

            AgentBuildExecutor executor1 = new AgentBuildExecutor(
                    "Create assistant",
                    "session_001",
                    AgentBuilderEnums.AgentType.LLM_AGENT.getValue(),
                    historyManagerMap,
                    agentBuilderMap,
                    validModelInfo(),
                    false);
            AgentBuildExecutor executor2 = new AgentBuildExecutor(
                    "Continue dialogue",
                    "session_001",
                    AgentBuilderEnums.AgentType.LLM_AGENT.getValue(),
                    historyManagerMap,
                    agentBuilderMap,
                    validModelInfo(),
                    false);

            assertSame(executor1.getHistoryManager(), executor2.getHistoryManager());
            assertTrue(historyManagerMap.containsKey("session_001"));
        }

        @Test
        void testExecutorHistoryPersistenceAcrossExecutions() {
            Map<String, HistoryManager> historyManagerMap = new HashMap<>();
            Map<String, BaseAgentBuilder> agentBuilderMap = new HashMap<>();
            agentBuilderMap.put("session_001", new RecordingBuilder());

            AgentBuildExecutor executor = new AgentBuildExecutor(
                    "Create assistant",
                    "session_001",
                    AgentBuilderEnums.AgentType.LLM_AGENT.getValue(),
                    historyManagerMap,
                    agentBuilderMap,
                    validModelInfo(),
                    false);

            executor.getHistoryManager().addMessage("Test message", "user");

            assertEquals(1, executor.getHistoryManager().getHistory().size());
        }
    }

    private static Map<String, Object> validModelInfo() {
        return Map.of(
                "model_provider", "openai",
                "model_name", "gpt-4",
                "api_key", "test_key",
                "temperature", 0.7,
                "top_p", 0.9);
    }

    private static final class RecordingBuilder extends BaseAgentBuilder {
        private RecordingBuilder() {
            super(null);
        }

        @Override
        protected Map<String, Object> handleInitial(Map<String, Object> query, List<Map<String, Object>> history) {
            return Map.of("result", "ok");
        }

        @Override
        protected Map<String, Object> handleProcessing(Map<String, Object> query, List<Map<String, Object>> history) {
            return Map.of("result", "ok");
        }
    }
}
