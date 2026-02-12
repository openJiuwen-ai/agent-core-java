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
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for Categorizer.
 * Corresponds to Python: test_categorizer.py TestCategorizerGetCategories
 */
class CategorizerTest {

    @Mock
    private Model mockModel;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testGetCategoriesSuccess() {
        when(mockModel.invoke(any()))
                .thenReturn(CompletableFuture.completedFuture(
                        new AssistantMessage("{\"categories\": [\"user_profile\", \"interests\"]}")
                ));

        List<BaseMessage> messages = List.of(new UserMessage("我喜欢吃川菜"));
        List<BaseMessage> history = List.of();

        List<String> result = Categorizer.getCategories(
                messages,
                history,
                new Pair<>("test_model", mockModel),
                3
        ).join();

        assertEquals(List.of("user_profile", "interests"), result);
    }

    @Test
    void testGetCategoriesEmptyResult() {
        when(mockModel.invoke(any()))
                .thenReturn(CompletableFuture.completedFuture(
                        new AssistantMessage("{\"categories\": []}")
                ));

        List<BaseMessage> messages = List.of(new UserMessage("Hello"));

        List<String> result = Categorizer.getCategories(
                messages,
                List.of(),
                new Pair<>("test_model", mockModel),
                3
        ).join();

        assertEquals(List.of(), result);
    }

    @Test
    void testGetCategoriesJsonErrorRetries() {
        when(mockModel.invoke(any()))
                .thenReturn(CompletableFuture.completedFuture(new AssistantMessage("invalid json")))
                .thenReturn(CompletableFuture.completedFuture(new AssistantMessage("still invalid")))
                .thenReturn(CompletableFuture.completedFuture(new AssistantMessage("{\"categories\": [\"user_profile\"]}")));

        List<BaseMessage> messages = List.of(new UserMessage("Test"));

        List<String> result = Categorizer.getCategories(
                messages,
                List.of(),
                new Pair<>("test_model", mockModel),
                3
        ).join();

        assertEquals(List.of("user_profile"), result);
        verify(mockModel, times(3)).invoke(any());
    }

    @Test
    void testGetCategoriesRetriesExhausted() {
        when(mockModel.invoke(any()))
                .thenReturn(CompletableFuture.completedFuture(new AssistantMessage("invalid")));

        List<BaseMessage> messages = List.of(new UserMessage("Test"));

        List<String> result = Categorizer.getCategories(
                messages,
                List.of(),
                new Pair<>("test_model", mockModel),
                3
        ).join();

        assertEquals(List.of(), result);
        verify(mockModel, times(3)).invoke(any());
    }

    @Test
    void testGetCategoriesNonDictResponse() {
        when(mockModel.invoke(any()))
                .thenReturn(CompletableFuture.completedFuture(
                        new AssistantMessage("[\"not\", \"a\", \"dict\"]")
                ));

        List<BaseMessage> messages = List.of(new UserMessage("Test"));

        List<String> result = Categorizer.getCategories(
                messages,
                List.of(),
                new Pair<>("test_model", mockModel),
                1
        ).join();

        assertEquals(List.of(), result);
    }

    @Test
    void testGetCategoriesMissingCategoriesKey() {
        when(mockModel.invoke(any()))
                .thenReturn(CompletableFuture.completedFuture(
                        new AssistantMessage("{\"other_key\": [\"value\"]}")
                ));

        List<BaseMessage> messages = List.of(new UserMessage("Test"));

        List<String> result = Categorizer.getCategories(
                messages,
                List.of(),
                new Pair<>("test_model", mockModel),
                1
        ).join();

        assertEquals(List.of(), result);
    }

    @Test
    void testGetCategoriesWithHistory() {
        when(mockModel.invoke(any()))
                .thenReturn(CompletableFuture.completedFuture(
                        new AssistantMessage("{\"categories\": [\"user_profile\"]}")
                ));

        List<BaseMessage> messages = List.of(new UserMessage("我叫张三"));
        List<BaseMessage> history = List.of(
                new UserMessage("你好"),
                new AssistantMessage("你好，有什么可以帮你的？")
        );

        List<String> result = Categorizer.getCategories(
                messages,
                history,
                new Pair<>("test_model", mockModel),
                3
        ).join();

        assertEquals(List.of("user_profile"), result);
        verify(mockModel).invoke(any());
    }

    @Test
    void testGetCategoriesDefaultRetries() {
        when(mockModel.invoke(any()))
                .thenReturn(CompletableFuture.completedFuture(new AssistantMessage("invalid")));

        List<BaseMessage> messages = List.of(new UserMessage("Test"));

        // Use the overload without retries parameter (default is 3)
        Categorizer.getCategories(
                messages,
                List.of(),
                new Pair<>("test_model", mockModel)
        ).join();

        verify(mockModel, times(3)).invoke(any());
    }
}


