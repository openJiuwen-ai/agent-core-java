/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.executor;

import com.openjiuwen.dev_tools.agent_builder.utils.AgentBuilderConstants;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test history manager functionality.
 * <p>
 * Mirrors Python's {@code test_history_manager.py} in
 * {@code tests/unit_tests/dev_tools/agent_builder/executor/test_history_manager.py}.
 */
class TestHistoryManager {

    @Nested
    class TestDialogueMessage {

        @Test
        void testDialogueMessageCreation() {
            OffsetDateTime timestamp = OffsetDateTime.now(ZoneOffset.UTC);

            HistoryManager.DialogueMessage message =
                    new HistoryManager.DialogueMessage("Hello", "user", timestamp);

            assertEquals("Hello", message.getContent());
            assertEquals("user", message.getRole());
            assertEquals(timestamp, message.getTimestamp());
        }

        @Test
        void testDialogueMessageToDict() {
            HistoryManager.DialogueMessage message =
                    new HistoryManager.DialogueMessage("Hello", "user", OffsetDateTime.now(ZoneOffset.UTC));

            Map<String, Object> result = message.toDict();

            assertEquals(Map.of("role", "user", "content", "Hello"), result);
            assertFalse(result.containsKey("timestamp"));
        }

        @Test
        void testDialogueMessageAssistant() {
            HistoryManager.DialogueMessage message =
                    new HistoryManager.DialogueMessage("Hi there!", "assistant", OffsetDateTime.now(ZoneOffset.UTC));

            assertEquals("assistant", message.getRole());
            assertEquals("assistant", message.toDict().get("role"));
        }
    }

    @Nested
    class TestHistoryCache {

        @Test
        void testHistoryCacheCreation() {
            HistoryManager.HistoryCache cache = new HistoryManager.HistoryCache();

            assertEquals(List.of(), cache.getHistory());
            assertEquals(AgentBuilderConstants.DEFAULT_MAX_HISTORY_SIZE, cache.getMaxHistorySize());
        }

        @Test
        void testHistoryCacheCustomSize() {
            HistoryManager.HistoryCache cache = new HistoryManager.HistoryCache(10);

            assertEquals(10, cache.getMaxHistorySize());
        }

        @Test
        void testGetHistoryEmpty() {
            assertEquals(List.of(), new HistoryManager.HistoryCache().getHistory());
        }

        @Test
        void testAddMessage() {
            HistoryManager.HistoryCache cache = new HistoryManager.HistoryCache();
            cache.addMessage(new HistoryManager.DialogueMessage("Test", "user", OffsetDateTime.now(ZoneOffset.UTC)));

            List<HistoryManager.DialogueMessage> history = cache.getHistory();
            assertEquals(1, history.size());
            assertEquals("Test", history.get(0).getContent());
        }

        @Test
        void testGetMessagesWithLimit() {
            HistoryManager.HistoryCache cache = new HistoryManager.HistoryCache(10);
            for (int i = 0; i < 5; i++) {
                cache.addMessage(new HistoryManager.DialogueMessage(
                        "Message " + i, "user", OffsetDateTime.now(ZoneOffset.UTC)));
            }

            List<Map<String, Object>> result = cache.getMessages(3);

            assertEquals(3, result.size());
            assertEquals("Message 2", result.get(0).get("content"));
            assertEquals("Message 3", result.get(1).get("content"));
            assertEquals("Message 4", result.get(2).get("content"));
        }

        @Test
        void testGetMessagesAll() {
            HistoryManager.HistoryCache cache = new HistoryManager.HistoryCache();
            for (int i = 0; i < 3; i++) {
                cache.addMessage(new HistoryManager.DialogueMessage(
                        "Message " + i, "user", OffsetDateTime.now(ZoneOffset.UTC)));
            }

            assertEquals(3, cache.getMessages(-1).size());
        }

        @Test
        void testMaxHistorySizeEnforcement() {
            HistoryManager.HistoryCache cache = new HistoryManager.HistoryCache(3);
            for (int i = 0; i < 5; i++) {
                cache.addMessage(new HistoryManager.DialogueMessage(
                        "Message " + i, "user", OffsetDateTime.now(ZoneOffset.UTC)));
            }

            List<HistoryManager.DialogueMessage> history = cache.getHistory();
            assertEquals(3, history.size());
            assertEquals("Message 2", history.get(0).getContent());
            assertEquals("Message 3", history.get(1).getContent());
            assertEquals("Message 4", history.get(2).getContent());
        }

        @Test
        void testClear() {
            HistoryManager.HistoryCache cache = new HistoryManager.HistoryCache();
            cache.addMessage(new HistoryManager.DialogueMessage("Test", "user", OffsetDateTime.now(ZoneOffset.UTC)));

            cache.clear();

            assertEquals(List.of(), cache.getHistory());
        }
    }

    @Nested
    class TestHistoryManagerMethods {

        @Test
        void testHistoryManagerCreation() {
            assertNotNull(new HistoryManager().getDialogueHistory());
        }

        @Test
        void testAddMessage() {
            HistoryManager manager = new HistoryManager();
            manager.addMessage("Hello", "user");

            List<Map<String, Object>> history = manager.getHistory();
            assertEquals(1, history.size());
            assertEquals("Hello", history.get(0).get("content"));
            assertEquals("user", history.get(0).get("role"));
        }

        @Test
        void testAddAssistantMessage() {
            HistoryManager manager = new HistoryManager();
            manager.addAssistantMessage("Hi there!");

            assertEquals("assistant", manager.getHistory().get(0).get("role"));
        }

        @Test
        void testAddUserMessage() {
            HistoryManager manager = new HistoryManager();
            manager.addUserMessage("Hello");

            assertEquals("user", manager.getHistory().get(0).get("role"));
        }

        @Test
        void testGetLatestKMessages() {
            HistoryManager manager = new HistoryManager();
            for (int i = 0; i < 5; i++) {
                manager.addUserMessage("Message " + i);
            }

            List<Map<String, Object>> result = manager.getLatestKMessages(3);

            assertEquals(3, result.size());
            assertEquals("Message 2", result.get(0).get("content"));
            assertEquals("Message 4", result.get(2).get("content"));
        }

        @Test
        void testGetHistory() {
            HistoryManager manager = new HistoryManager();
            manager.addUserMessage("Hello");
            manager.addAssistantMessage("Hi!");

            assertEquals(2, manager.getHistory().size());
        }

        @Test
        void testClear() {
            HistoryManager manager = new HistoryManager();
            manager.addUserMessage("Hello");

            manager.clear();

            assertEquals(List.of(), manager.getHistory());
        }

        @Test
        void testCustomTimestamp() {
            HistoryManager manager = new HistoryManager();
            OffsetDateTime customTime = OffsetDateTime.of(2025, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC);

            manager.addMessage("Test", "user", customTime);

            assertEquals(customTime, manager.getDialogueHistory().getHistory().get(0).getTimestamp());
        }

        @Test
        void testMultipleSessionsIndependent() {
            HistoryManager manager1 = new HistoryManager();
            HistoryManager manager2 = new HistoryManager();

            manager1.addUserMessage("Session 1");
            manager2.addUserMessage("Session 2");

            assertEquals(1, manager1.getHistory().size());
            assertEquals(1, manager2.getHistory().size());
            assertEquals("Session 1", manager1.getHistory().get(0).get("content"));
            assertEquals("Session 2", manager2.getHistory().get(0).get("content"));
        }
    }
}
