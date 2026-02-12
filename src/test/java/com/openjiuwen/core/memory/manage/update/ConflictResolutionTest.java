/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.memory.manage.update;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.memory.manage.memmodel.ConflictType;
import com.openjiuwen.core.common.utils.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ConflictResolution.
 * Converted from Python: test_conflict_resolution.py
 */
class ConflictResolutionTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Nested
    @DisplayName("Check Conflict Tests")
    class CheckConflictTests {

        @Test
        @DisplayName("Test empty old_messages returns ADD operation for new message")
        void testEmptyOldMessagesReturnsAdd() {
            Model mockModel = mock(Model.class);
            Pair<String, Model> baseChatModel = Pair.of("test_model", mockModel);

            List<Map<String, Object>> result = ConflictResolution.checkConflict(
                List.of(),
                "用户喜欢吃川菜",
                baseChatModel
            ).join();

            assertEquals(1, result.size());
            assertEquals("0", result.get(0).get("id"));
            assertEquals("用户喜欢吃川菜", result.get(0).get("text"));
            assertEquals(ConflictType.ADD.getValue(), result.get(0).get("event"));
        }

        @Test
        @DisplayName("Test null base_chat_model returns ADD operation")
        void testNoneBaseChatModelReturnsAdd() {
            List<Map<String, Object>> result = ConflictResolution.checkConflict(
                List.of("existing memory"),
                "new memory",
                null
            ).join();

            assertEquals(1, result.size());
            assertEquals(ConflictType.ADD.getValue(), result.get(0).get("event"));
        }

        @Test
        @DisplayName("Test new_message already in old_messages returns NONE operation")
        void testNewMessageInOldMessagesReturnsNone() {
            Model mockModel = mock(Model.class);
            Pair<String, Model> baseChatModel = Pair.of("test_model", mockModel);

            String existingMsg = "用户居住在北京";
            List<Map<String, Object>> result = ConflictResolution.checkConflict(
                List.of(existingMsg, "其他记忆"),
                existingMsg,
                baseChatModel
            ).join();

            assertEquals(1, result.size());
            assertEquals("0", result.get(0).get("id"));
            assertEquals(existingMsg, result.get(0).get("text"));
            assertEquals(ConflictType.NONE.getValue(), result.get(0).get("event"));
        }

        @Test
        @DisplayName("Test LLM returns valid conflict resolution result")
        void testLlmReturnsValidConflictResolution() throws Exception {
            Model mockModel = mock(Model.class);
            Pair<String, Model> baseChatModel = Pair.of("test_model", mockModel);

            String responseJson = objectMapper.writeValueAsString(Map.of(
                "new_message", Map.of(
                    "id", "0",
                    "text", "用户喜欢吃川菜和粤菜",
                    "event", "UPDATE"
                ),
                "old_messages", List.of(
                    Map.of(
                        "id", "1",
                        "text", "用户喜欢吃川菜",
                        "event", "DELETE"
                    )
                )
            ));

            AssistantMessage mockResponse = AssistantMessage.of(responseJson);
            when(mockModel.invoke(any()))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));

            List<Map<String, Object>> result = ConflictResolution.checkConflict(
                List.of("用户喜欢吃川菜"),
                "用户喜欢吃川菜和粤菜",
                baseChatModel
            ).join();

            assertEquals(2, result.size());
            assertEquals("0", result.get(0).get("id"));
            assertEquals("UPDATE", result.get(0).get("event"));
            assertEquals("1", result.get(1).get("id"));
            assertEquals("DELETE", result.get(1).get("event"));
        }

        @Test
        @DisplayName("Test JSON decode error triggers retry")
        void testLlmJsonDecodeErrorRetries() throws Exception {
            Model mockModel = mock(Model.class);
            Pair<String, Model> baseChatModel = Pair.of("test_model", mockModel);

            String validJson = objectMapper.writeValueAsString(Map.of(
                "new_message", Map.of(
                    "id", "0",
                    "text", "valid response",
                    "event", "ADD"
                )
            ));

            // First two calls return invalid JSON, third returns valid
            when(mockModel.invoke(any()))
                .thenReturn(CompletableFuture.completedFuture(AssistantMessage.of("invalid json {")))
                .thenReturn(CompletableFuture.completedFuture(AssistantMessage.of("still invalid")))
                .thenReturn(CompletableFuture.completedFuture(AssistantMessage.of(validJson)));

            List<Map<String, Object>> result = ConflictResolution.checkConflict(
                List.of("old message"),
                "new message",
                baseChatModel,
                3
            ).join();

            assertEquals(1, result.size());
            assertEquals("valid response", result.get(0).get("text"));
            verify(mockModel, times(3)).invoke(any());
        }

        @Test
        @DisplayName("Test exhausting retries returns empty list")
        void testRetriesExhaustedReturnsEmptyList() {
            Model mockModel = mock(Model.class);
            Pair<String, Model> baseChatModel = Pair.of("test_model", mockModel);

            when(mockModel.invoke(any()))
                .thenReturn(CompletableFuture.completedFuture(AssistantMessage.of("invalid json")));

            List<Map<String, Object>> result = ConflictResolution.checkConflict(
                List.of("old message"),
                "new message",
                baseChatModel,
                3
            ).join();

            assertTrue(result.isEmpty());
            verify(mockModel, times(3)).invoke(any());
        }

        @Test
        @DisplayName("Test LLM returning non-dict result triggers retry")
        void testLlmReturnsNonDictRetries() throws Exception {
            Model mockModel = mock(Model.class);
            Pair<String, Model> baseChatModel = Pair.of("test_model", mockModel);

            String validJson = objectMapper.writeValueAsString(Map.of(
                "new_message", Map.of(
                    "id", "0",
                    "text", "correct",
                    "event", "ADD"
                )
            ));

            // First call returns list instead of dict, second returns valid dict
            when(mockModel.invoke(any()))
                .thenReturn(CompletableFuture.completedFuture(AssistantMessage.of("[\"not\", \"a\", \"dict\"]")))
                .thenReturn(CompletableFuture.completedFuture(AssistantMessage.of(validJson)));

            List<Map<String, Object>> result = ConflictResolution.checkConflict(
                List.of("old"),
                "new",
                baseChatModel,
                3
            ).join();

            assertEquals(1, result.size());
            assertEquals("correct", result.get(0).get("text"));
        }

        @Test
        @DisplayName("Test default retries parameter is 3")
        void testDefaultRetriesIsThree() {
            Model mockModel = mock(Model.class);
            Pair<String, Model> baseChatModel = Pair.of("test_model", mockModel);

            when(mockModel.invoke(any()))
                .thenReturn(CompletableFuture.completedFuture(AssistantMessage.of("invalid")));

            ConflictResolution.checkConflict(
                List.of("old"),
                "new",
                baseChatModel
            ).join();

            verify(mockModel, times(3)).invoke(any());
        }

        @Test
        @DisplayName("Test result with only new_message (no old_messages modifications)")
        void testOnlyNewMessageInResult() throws Exception {
            Model mockModel = mock(Model.class);
            Pair<String, Model> baseChatModel = Pair.of("test_model", mockModel);

            String responseJson = objectMapper.writeValueAsString(Map.of(
                "new_message", Map.of(
                    "id", "0",
                    "text", "独立的新记忆",
                    "event", "ADD"
                )
            ));

            when(mockModel.invoke(any()))
                .thenReturn(CompletableFuture.completedFuture(AssistantMessage.of(responseJson)));

            List<Map<String, Object>> result = ConflictResolution.checkConflict(
                List.of("不相关的旧记忆"),
                "独立的新记忆",
                baseChatModel
            ).join();

            assertEquals(1, result.size());
            assertEquals("ADD", result.get(0).get("event"));
        }

        @Test
        @DisplayName("Test result with multiple old_messages modifications")
        void testMultipleOldMessagesInResult() throws Exception {
            Model mockModel = mock(Model.class);
            Pair<String, Model> baseChatModel = Pair.of("test_model", mockModel);

            String responseJson = objectMapper.writeValueAsString(Map.of(
                "new_message", Map.of(
                    "id", "0",
                    "text", "合并后的记忆",
                    "event", "ADD"
                ),
                "old_messages", List.of(
                    Map.of("id", "1", "text", "旧记忆1", "event", "DELETE"),
                    Map.of("id", "2", "text", "旧记忆2", "event", "DELETE")
                )
            ));

            when(mockModel.invoke(any()))
                .thenReturn(CompletableFuture.completedFuture(AssistantMessage.of(responseJson)));

            List<Map<String, Object>> result = ConflictResolution.checkConflict(
                List.of("旧记忆1", "旧记忆2"),
                "合并后的记忆",
                baseChatModel
            ).join();

            assertEquals(3, result.size());
        }

        @Test
        @DisplayName("Test old_messages field that is not a list is ignored")
        void testOldMessagesNotListIgnored() throws Exception {
            Model mockModel = mock(Model.class);
            Pair<String, Model> baseChatModel = Pair.of("test_model", mockModel);

            String responseJson = objectMapper.writeValueAsString(Map.of(
                "new_message", Map.of(
                    "id", "0",
                    "text", "new memory",
                    "event", "ADD"
                ),
                "old_messages", "not a list"
            ));

            when(mockModel.invoke(any()))
                .thenReturn(CompletableFuture.completedFuture(AssistantMessage.of(responseJson)));

            List<Map<String, Object>> result = ConflictResolution.checkConflict(
                List.of("old"),
                "new",
                baseChatModel
            ).join();

            assertEquals(1, result.size());
            assertEquals("new memory", result.get(0).get("text"));
        }
    }
}



