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
import com.openjiuwen.core.memory.manage.memmodel.BaseMemoryUnit;
import com.openjiuwen.core.memory.manage.memmodel.UserProfileUnit;
import com.openjiuwen.core.memory.manage.memmodel.VariableUnit;
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
 * Unit tests for Generator.
 * Corresponds to Python: test_generation.py
 */
class GeneratorTest {

    @Mock
    private Model mockModel;

    private Generator generator;
    private AgentMemoryConfig configWithVariables;
    private AgentMemoryConfig configNoLongTerm;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        generator = new Generator();

        configWithVariables = AgentMemoryConfig.builder()
                .memVariables(List.of(
                        Param.string("user_name", "用户姓名", false)
                ))
                .enableLongTermMem(true)
                .build();

        configNoLongTerm = AgentMemoryConfig.builder()
                .memVariables(List.of())
                .enableLongTermMem(false)
                .build();
    }

    // Tests for genAllMemory

    @Test
    void testGenAllMemoryMissingParamsReturnsEmpty() {
        List<BaseMemoryUnit> result = generator.genAllMemory(
                null,  // messages
                null,  // config
                null,  // user_id
                null,  // scope_id
                null,  // model
                null,  // history_messages
                null   // message_mem_id
        ).join();

        assertEquals(List.of(), result);
    }

    @Test
    void testGenAllMemoryNoLongTermMem() {
        when(mockModel.invoke(any()))
                .thenReturn(CompletableFuture.completedFuture(
                        new AssistantMessage("{\"categories\": []}")
                ));

        List<BaseMessage> messages = List.of(new UserMessage("Test"));

        List<BaseMemoryUnit> result = generator.genAllMemory(
                messages,
                configNoLongTerm,
                "user1",
                "scope1",
                new Pair<>("test", mockModel),
                List.of(),
                null
        ).join();

        // Should only call gen_extracted_data, not categorizer
        // verify behavior without categorizer invocation for user profile
        assertNotNull(result);
    }

    @Test
    void testGenAllMemoryWithVariables() {
        when(mockModel.invoke(any()))
                .thenReturn(CompletableFuture.completedFuture(
                        new AssistantMessage("{\"user_name\": {\"value\": \"张三\"}}")
                ))
                .thenReturn(CompletableFuture.completedFuture(
                        new AssistantMessage("{\"categories\": []}")
                ));

        List<BaseMessage> messages = List.of(new UserMessage("我叫张三"));

        List<BaseMemoryUnit> result = generator.genAllMemory(
                messages,
                configWithVariables,
                "user1",
                "scope1",
                new Pair<>("test", mockModel),
                List.of(),
                null
        ).join();

        // Should have variable units
        List<VariableUnit> variableUnits = result.stream()
                .filter(u -> u instanceof VariableUnit)
                .map(u -> (VariableUnit) u)
                .toList();
        assertEquals(1, variableUnits.size());
        assertEquals("user_name", variableUnits.get(0).getVariableName());
    }

    @Test
    void testGenAllMemoryReturnsPartialOnEmptyCategories() {
        when(mockModel.invoke(any()))
                .thenReturn(CompletableFuture.completedFuture(
                        new AssistantMessage("{\"user_name\": {\"value\": \"测试\"}}")
                ))
                .thenReturn(CompletableFuture.completedFuture(
                        new AssistantMessage("{\"categories\": []}")
                ));

        List<BaseMessage> messages = List.of(new UserMessage("Test"));

        List<BaseMemoryUnit> result = generator.genAllMemory(
                messages,
                configWithVariables,
                "user1",
                "scope1",
                new Pair<>("test", mockModel),
                List.of(),
                null
        ).join();

        // Should return variable units even when no categories
        assertNotNull(result);
        assertTrue(result.size() >= 1);
    }

    // Tests for genExtractedData

    @Test
    void testGenExtractedDataReturnsVariableUnits() {
        when(mockModel.invoke(any()))
                .thenReturn(CompletableFuture.completedFuture(
                        new AssistantMessage("{\"user_name\": {\"value\": \"李四\"}}")
                ));

        ExtractMemoryParams params = new ExtractMemoryParams(
                "user1",
                "scope1",
                List.of(new UserMessage("我叫李四")),
                List.of(),
                new Pair<>("test", mockModel)
        );

        List<VariableUnit> result = generator.genExtractedData(params, configWithVariables).join();

        assertEquals(1, result.size());
        assertTrue(result.get(0) instanceof VariableUnit);
        assertEquals("user_name", result.get(0).getVariableName());
        assertEquals("李四", result.get(0).getVariableMem());
        assertEquals("user1", result.get(0).getUserId());
        assertEquals("scope1", result.get(0).getScopeId());
    }

    // Tests for genUserProfile

    @Test
    void testGenUserProfileReturnsUserProfileUnits() {
        String profileJson = """
                {
                    "personal_information": ["用户姓名是王五", "用户年龄是30岁"],
                    "interests_hobbies": ["用户喜欢跑步"]
                }
                """;
        when(mockModel.invoke(any()))
                .thenReturn(CompletableFuture.completedFuture(new AssistantMessage(profileJson)));

        ExtractMemoryParams params = new ExtractMemoryParams(
                "user1",
                "scope1",
                List.of(new UserMessage("我叫王五，30岁，喜欢跑步")),
                List.of(),
                new Pair<>("test", mockModel)
        );

        List<UserProfileUnit> result = generator.genUserProfile(params, "msg123", null).join();

        assertEquals(3, result.size());
        for (UserProfileUnit unit : result) {
            assertTrue(unit instanceof UserProfileUnit);
            assertEquals("user1", unit.getUserId());
            assertEquals("scope1", unit.getScopeId());
        }
    }

    @Test
    void testGenUserProfileNonListWarning() {
        String profileJson = """
                {
                    "personal_information": "not a list",
                    "interests_hobbies": ["valid item"]
                }
                """;
        when(mockModel.invoke(any()))
                .thenReturn(CompletableFuture.completedFuture(new AssistantMessage(profileJson)));

        ExtractMemoryParams params = new ExtractMemoryParams(
                "user1",
                "scope1",
                List.of(new UserMessage("Test")),
                List.of(),
                new Pair<>("test", mockModel)
        );

        List<UserProfileUnit> result = generator.genUserProfile(params, "msg123", null).join();

        // Only the list item should be included
        assertEquals(1, result.size());
    }
}

/**
 * Unit tests for ExtractMemoryParams.
 * Corresponds to Python: test_generation.py TestExtractMemoryParams
 */
class ExtractMemoryParamsTest {

    @Test
    void testDataclassFields() {
        Model mockModel = mock(Model.class);
        ExtractMemoryParams params = new ExtractMemoryParams(
                "user1",
                "scope1",
                List.of(),
                List.of(),
                new Pair<>("model", mockModel)
        );

        assertEquals("user1", params.userId());
        assertEquals("scope1", params.scopeId());
        assertEquals(List.of(), params.messages());
        assertEquals(List.of(), params.historyMessages());
        assertEquals("model", params.baseChatModel().getKey());
    }
}


