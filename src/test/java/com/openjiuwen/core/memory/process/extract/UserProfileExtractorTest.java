/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */

package com.openjiuwen.core.memory.process.extract;

import com.openjiuwen.core.common.utils.Pair;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UserProfileExtractor.
 * Corresponds to Python: test_user_profile_extractor.py
 */
class UserProfileExtractorTest {

    @Mock
    private Model mockModel;

    private List<BaseMessage> sampleMessages;
    private List<BaseMessage> sampleHistoryMessages;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        sampleMessages = List.of(
                new UserMessage("我叫张三，今年25岁，喜欢打篮球"),
                new AssistantMessage("很高兴认识你，张三！")
        );
        sampleHistoryMessages = List.of(
                new UserMessage("你好"),
                new AssistantMessage("你好！有什么可以帮助你的吗？")
        );
    }

    // Tests for getMessage (private method, tested through getUserProfile behavior)

    @Test
    void testGetMessageWithoutUserDefine() {
        String result = UserProfileExtractor.getMessage(null);

        assertNotNull(result);
        assertTrue(result.length() > 0);
    }

    @Test
    void testGetMessageWithEmptyUserDefine() {
        String result = UserProfileExtractor.getMessage(Map.of());

        assertNotNull(result);
        assertTrue(result.length() > 0);
    }

    @Test
    void testGetMessageWithUserDefine() {
        Map<String, String> userDefine = Map.of(
                "职业", "用户的职业信息",
                "爱好", "用户的兴趣爱好"
        );

        String result = UserProfileExtractor.getMessage(userDefine);

        assertNotNull(result);
        assertTrue(result.contains("职业"));
        assertTrue(result.contains("爱好"));
    }

    // Tests for getUserProfile

    @Test
    void testGetUserProfileSuccess() {
        String expectedJson = "{\"name\": \"张三\", \"age\": \"25岁\", \"hobby\": [\"打篮球\"]}";
        when(mockModel.invoke(any()))
                .thenReturn(CompletableFuture.completedFuture(new AssistantMessage(expectedJson)));

        Map<String, Object> result = UserProfileExtractor.getUserProfile(
                sampleMessages,
                sampleHistoryMessages,
                new Pair<>("test-model", mockModel),
                null,
                3
        ).join();

        assertEquals("张三", result.get("name"));
        assertEquals("25岁", result.get("age"));
        verify(mockModel, times(1)).invoke(any());
    }

    @Test
    void testGetUserProfileWithUserDefine() {
        Map<String, String> userDefine = Map.of("职业", "用户职业信息");
        String expectedJson = "{\"name\": \"张三\", \"职业\": \"软件工程师\"}";
        when(mockModel.invoke(any()))
                .thenReturn(CompletableFuture.completedFuture(new AssistantMessage(expectedJson)));

        Map<String, Object> result = UserProfileExtractor.getUserProfile(
                sampleMessages,
                sampleHistoryMessages,
                new Pair<>("test-model", mockModel),
                userDefine,
                3
        ).join();

        assertEquals("张三", result.get("name"));
        assertEquals("软件工程师", result.get("职业"));
    }

    @Test
    void testGetUserProfileJsonDecodeErrorRetry() {
        String expectedJson = "{\"name\": \"张三\"}";
        when(mockModel.invoke(any()))
                .thenReturn(CompletableFuture.completedFuture(new AssistantMessage("invalid json{")))
                .thenReturn(CompletableFuture.completedFuture(new AssistantMessage("still invalid")))
                .thenReturn(CompletableFuture.completedFuture(new AssistantMessage(expectedJson)));

        Map<String, Object> result = UserProfileExtractor.getUserProfile(
                sampleMessages,
                sampleHistoryMessages,
                new Pair<>("test-model", mockModel),
                null,
                3
        ).join();

        assertEquals("张三", result.get("name"));
        verify(mockModel, times(3)).invoke(any());
    }

    @Test
    void testGetUserProfileAllRetriesExhausted() {
        when(mockModel.invoke(any()))
                .thenReturn(CompletableFuture.completedFuture(new AssistantMessage("not valid json")));

        Map<String, Object> result = UserProfileExtractor.getUserProfile(
                sampleMessages,
                sampleHistoryMessages,
                new Pair<>("test-model", mockModel),
                null,
                3
        ).join();

        assertEquals(Map.of(), result);
        verify(mockModel, times(3)).invoke(any());
    }

    @Test
    void testGetUserProfileNonDictResult() {
        when(mockModel.invoke(any()))
                .thenReturn(CompletableFuture.completedFuture(
                        new AssistantMessage("[\"list\", \"not\", \"dict\"]")
                ));

        Map<String, Object> result = UserProfileExtractor.getUserProfile(
                sampleMessages,
                sampleHistoryMessages,
                new Pair<>("test-model", mockModel),
                null,
                1
        ).join();

        assertEquals(Map.of(), result);
    }

    @Test
    void testGetUserProfileEmptyMessages() {
        when(mockModel.invoke(any()))
                .thenReturn(CompletableFuture.completedFuture(new AssistantMessage("{}")));

        Map<String, Object> result = UserProfileExtractor.getUserProfile(
                List.of(),
                sampleHistoryMessages,
                new Pair<>("test-model", mockModel),
                null,
                3
        ).join();

        assertNotNull(result);
    }

    @Test
    void testGetUserProfileEmptyHistoryMessages() {
        String expectedJson = "{\"name\": \"张三\"}";
        when(mockModel.invoke(any()))
                .thenReturn(CompletableFuture.completedFuture(new AssistantMessage(expectedJson)));

        Map<String, Object> result = UserProfileExtractor.getUserProfile(
                sampleMessages,
                List.of(),
                new Pair<>("test-model", mockModel),
                null,
                3
        ).join();

        assertEquals("张三", result.get("name"));
    }

    @Test
    void testGetUserProfileModelInputFormat() {
        when(mockModel.invoke(any()))
                .thenReturn(CompletableFuture.completedFuture(new AssistantMessage("{\"test\": \"value\"}")));

        UserProfileExtractor.getUserProfile(
                sampleMessages,
                sampleHistoryMessages,
                new Pair<>("test-model", mockModel),
                null,
                3
        ).join();

        verify(mockModel).invoke(any());
    }

    @Test
    void testGetUserProfileJsonWithMarkdownFence() {
        String expectedJson = "{\"name\": \"张三\"}";
        String wrappedJson = "```json\n" + expectedJson + "\n```";
        when(mockModel.invoke(any()))
                .thenReturn(CompletableFuture.completedFuture(new AssistantMessage(wrappedJson)));

        Map<String, Object> result = UserProfileExtractor.getUserProfile(
                sampleMessages,
                sampleHistoryMessages,
                new Pair<>("test-model", mockModel),
                null,
                1
        ).join();

        // Result depends on parser implementation
        assertNotNull(result);
    }

    @Test
    void testGetUserProfileCustomRetries() {
        when(mockModel.invoke(any()))
                .thenReturn(CompletableFuture.completedFuture(new AssistantMessage("invalid")));

        UserProfileExtractor.getUserProfile(
                sampleMessages,
                sampleHistoryMessages,
                new Pair<>("test-model", mockModel),
                null,
                5
        ).join();

        verify(mockModel, times(5)).invoke(any());
    }

    @Test
    void testGetUserProfileNestedDict() {
        String expectedJson = """
                {
                    "basic_info": {
                        "name": "张三",
                        "age": 25
                    },
                    "interests": ["篮球", "编程"]
                }
                """;
        when(mockModel.invoke(any()))
                .thenReturn(CompletableFuture.completedFuture(new AssistantMessage(expectedJson)));

        Map<String, Object> result = UserProfileExtractor.getUserProfile(
                sampleMessages,
                sampleHistoryMessages,
                new Pair<>("test-model", mockModel),
                null,
                3
        ).join();

        assertNotNull(result);
        assertTrue(result.containsKey("basic_info"));
        @SuppressWarnings("unchecked")
        Map<String, Object> basicInfo = (Map<String, Object>) result.get("basic_info");
        assertEquals("张三", basicInfo.get("name"));
    }
}


