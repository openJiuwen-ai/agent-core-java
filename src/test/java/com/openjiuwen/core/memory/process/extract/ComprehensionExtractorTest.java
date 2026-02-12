/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */

package com.openjiuwen.core.memory.process.extract;

import com.openjiuwen.core.common.schema.Param;
import com.openjiuwen.core.common.utils.Pair;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.memory.config.AgentMemoryConfig;
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
 * Unit tests for ComprehensionExtractor.
 * Corresponds to Python: test_variable_extractor.py
 */
class ComprehensionExtractorTest {

    @Mock
    private Model mockModel;

    private AgentMemoryConfig configWithVariables;
    private AgentMemoryConfig configEmptyVariables;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        configWithVariables = AgentMemoryConfig.builder()
                .memVariables(List.of(
                        Param.string("user_name", "用户姓名", false),
                        Param.string("user_age", "用户年龄", false)
                ))
                .build();

        configEmptyVariables = AgentMemoryConfig.builder()
                .memVariables(List.of())
                .build();
    }

    @Test
    void testExtractSuccess() {
        String responseJson = "{\"user_name\": {\"value\": \"张三\"}, \"user_age\": {\"value\": \"25\"}}";
        when(mockModel.invoke(any()))
                .thenReturn(CompletableFuture.completedFuture(new AssistantMessage(responseJson)));

        List<BaseMessage> messages = List.of(new UserMessage("我叫张三，今年25岁"));
        BaseMessage history = new UserMessage("");

        List<ExtractedData> result = ComprehensionExtractor.extract(
                messages,
                history,
                new Pair<>("test_model", mockModel),
                configWithVariables
        ).join();

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(r -> r instanceof ExtractedData));

        Map<String, String> values = result.stream()
                .collect(java.util.stream.Collectors.toMap(ExtractedData::key, ExtractedData::value));
        assertEquals("张三", values.get("user_name"));
        assertEquals("25", values.get("user_age"));
    }

    @Test
    void testExtractEmptyVariablesReturnsEmpty() {
        List<BaseMessage> messages = List.of(new UserMessage("Test"));
        BaseMessage history = new UserMessage("");

        List<ExtractedData> result = ComprehensionExtractor.extract(
                messages,
                history,
                new Pair<>("test_model", mockModel),
                configEmptyVariables
        ).join();

        assertEquals(List.of(), result);
        verify(mockModel, never()).invoke(any());
    }

    @Test
    void testExtractNullValueSkipped() {
        String responseJson = "{\"user_name\": {\"value\": \"张三\"}, \"user_age\": {\"value\": \"null\"}}";
        when(mockModel.invoke(any()))
                .thenReturn(CompletableFuture.completedFuture(new AssistantMessage(responseJson)));

        List<BaseMessage> messages = List.of(new UserMessage("我叫张三"));
        BaseMessage history = new UserMessage("");

        List<ExtractedData> result = ComprehensionExtractor.extract(
                messages,
                history,
                new Pair<>("test_model", mockModel),
                configWithVariables
        ).join();

        assertEquals(1, result.size());
        assertEquals("user_name", result.get(0).key());
    }

    @Test
    void testExtractNoneValueSkipped() {
        String responseJson = "{\"user_name\": {\"value\": \"张三\"}, \"user_age\": {\"value\": null}}";
        when(mockModel.invoke(any()))
                .thenReturn(CompletableFuture.completedFuture(new AssistantMessage(responseJson)));

        List<BaseMessage> messages = List.of(new UserMessage("我叫张三"));
        BaseMessage history = new UserMessage("");

        List<ExtractedData> result = ComprehensionExtractor.extract(
                messages,
                history,
                new Pair<>("test_model", mockModel),
                configWithVariables
        ).join();

        assertEquals(1, result.size());
    }

    @Test
    void testExtractEmptyValueSkipped() {
        String responseJson = "{\"user_name\": {\"value\": \"\"}, \"user_age\": {\"value\": \"25\"}}";
        when(mockModel.invoke(any()))
                .thenReturn(CompletableFuture.completedFuture(new AssistantMessage(responseJson)));

        List<BaseMessage> messages = List.of(new UserMessage("我25岁"));
        BaseMessage history = new UserMessage("");

        List<ExtractedData> result = ComprehensionExtractor.extract(
                messages,
                history,
                new Pair<>("test_model", mockModel),
                configWithVariables
        ).join();

        assertEquals(1, result.size());
        assertEquals("user_age", result.get(0).key());
    }

    @Test
    void testExtractInvalidJsonReturnsEmpty() {
        when(mockModel.invoke(any()))
                .thenReturn(CompletableFuture.completedFuture(new AssistantMessage("invalid json")));

        List<BaseMessage> messages = List.of(new UserMessage("Test"));
        BaseMessage history = new UserMessage("");

        List<ExtractedData> result = ComprehensionExtractor.extract(
                messages,
                history,
                new Pair<>("test_model", mockModel),
                configWithVariables
        ).join();

        assertEquals(List.of(), result);
    }

    @Test
    void testExtractExceptionDuringParseReturnsEmpty() {
        when(mockModel.invoke(any()))
                .thenReturn(CompletableFuture.completedFuture(new AssistantMessage("not json at all")));

        List<BaseMessage> messages = List.of(new UserMessage("Test"));
        BaseMessage history = new UserMessage("");

        List<ExtractedData> result = ComprehensionExtractor.extract(
                messages,
                history,
                new Pair<>("test_model", mockModel),
                configWithVariables
        ).join();

        assertEquals(List.of(), result);
    }

    @Test
    void testExtractDataTypeIsUser() {
        String responseJson = "{\"user_name\": {\"value\": \"张三\"}}";
        when(mockModel.invoke(any()))
                .thenReturn(CompletableFuture.completedFuture(new AssistantMessage(responseJson)));

        List<BaseMessage> messages = List.of(new UserMessage("我叫张三"));
        BaseMessage history = new UserMessage("");

        List<ExtractedData> result = ComprehensionExtractor.extract(
                messages,
                history,
                new Pair<>("test_model", mockModel),
                configWithVariables
        ).join();

        assertEquals(ExtractedDataType.USER, result.get(0).type());
    }

    // Tests for _check_value

    @Test
    void testCheckValueValid() {
        assertTrue(ComprehensionExtractor.checkValue(Map.of("value", "valid")));
    }

    @Test
    void testCheckValueNone() {
        assertFalse(ComprehensionExtractor.checkValue(null));
    }

    @Test
    void testCheckValueNotDict() {
        assertFalse(ComprehensionExtractor.checkValue("string"));
        assertFalse(ComprehensionExtractor.checkValue(123));
    }

    @Test
    void testCheckValueNoneInDict() {
        java.util.HashMap<String, Object> map = new java.util.HashMap<>();
        map.put("value", null);
        assertFalse(ComprehensionExtractor.checkValue(map));
    }

    @Test
    void testCheckValueNoneString() {
        assertFalse(ComprehensionExtractor.checkValue(Map.of("value", "none")));
        assertFalse(ComprehensionExtractor.checkValue(Map.of("value", "None")));
        assertFalse(ComprehensionExtractor.checkValue(Map.of("value", "NONE")));
    }

    @Test
    void testCheckValueMissingValueKey() {
        boolean result = ComprehensionExtractor.checkValue(Map.of("other", "data"));
        // Empty string from .get default should fail the none check
        assertTrue(result);  // Empty string is not "none"
    }
}


