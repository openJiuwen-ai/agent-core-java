/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.extensions.context_evolver;

import com.openjiuwen.extensions.context_evolver.core.config.Config;
import com.openjiuwen.extensions.context_evolver.core.context.ServiceContext;
import com.openjiuwen.extensions.context_evolver.service.AddMemoryRequest;
import com.openjiuwen.extensions.context_evolver.service.TaskMemoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code tests/unit_tests/extensions/context_evolver/test_retrieve_flow.py}.
 */
class TestRetrieveFlow {

    private Map<String, Object> configSnapshot;

    @BeforeEach
    void captureState() {
        configSnapshot = Config.snapshot();
        ServiceContext.getInstance().clear();
    }

    @AfterEach
    void restoreState() {
        Config.restore(configSnapshot);
        ServiceContext.getInstance().clear();
    }

    @ParameterizedTest
    @MethodSource("algorithms")
    @DisplayName("test_add_and_retrieve_memory")
    @Tag("level0")
    void testAddAndRetrieveMemory(String algoKey, String algoName) {
        TaskMemoryService service = createService(algoKey);
        assertEquals(algoName, service.getSummaryAlgorithm());
        assertEquals(algoName, service.getRetrievalAlgorithm());

        String userId = "retrieve-user-" + UUID.randomUUID();
        String content = "Use functools.lru_cache decorator for simple memoization. "
            + "For more complex cases, consider using Redis or memcached.";

        service.addMemory(userId, createAddRequest(algoName, content, "1")).join();

        Map<String, Object> result = service.retrieve(
            userId,
            "How do I implement caching in Python?"
        ).join();

        assertEquals("success", result.get("status"));
        assertFalse(((List<?>) result.get("retrieved_memory")).isEmpty());
        assertFalse(String.valueOf(result.get("memory_string")).isBlank());

        @SuppressWarnings("unchecked")
        Map<String, Object> first = (Map<String, Object>) ((List<?>) result.get("retrieved_memory")).getFirst();
        assertAlgorithmSpecificRetrievedMemory(algoName, first, content);
    }

    @ParameterizedTest
    @MethodSource("algorithms")
    @DisplayName("test_retrieve_without_memories")
    @Tag("level0")
    void testRetrieveWithoutMemories(String algoKey, String algoName) {
        TaskMemoryService service = createService(algoKey);

        Map<String, Object> result = service.retrieve(
            "empty-user-" + UUID.randomUUID(),
            "What is Python?"
        ).join();

        assertEquals(algoName, service.getSummaryAlgorithm());
        assertEquals("success", result.get("status"));
        assertEquals("", result.get("memory_string"));
        assertTrue(((List<?>) result.get("retrieved_memory")).isEmpty());
    }

    @ParameterizedTest
    @MethodSource("algorithms")
    @DisplayName("test_playbook_operations")
    @Tag("level0")
    void testPlaybookOperations(String algoKey, String algoName) {
        TaskMemoryService service = createService(algoKey);
        String userId = "playbook-user-" + UUID.randomUUID();

        service.addMemory(userId, createAddRequest(algoName, "Content 1", "1")).join();
        service.addMemory(userId, createAddRequest(algoName, "Content 2", "2")).join();

        Map<String, Object> playbook = service.getPlaybook(userId).join();
        assertEquals(userId, playbook.get("user_id"));
        assertEquals(2, ((Number) playbook.get("memory_count")).intValue());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> memories = (List<Map<String, Object>>) playbook.get("memories");
        assertEquals(2, memories.size());
        assertPlaybookContents(algoName, memories);

        Map<String, Object> cleared = service.clearPlaybook(userId).join();
        assertEquals("success", cleared.get("status"));

        Map<String, Object> emptyPlaybook = service.getPlaybook(userId).join();
        assertEquals(0, ((Number) emptyPlaybook.get("memory_count")).intValue());
        assertEquals(List.of(), emptyPlaybook.get("memories"));
    }

    @Test
    @DisplayName("test_algorithm_normalization")
    @Tag("level0")
    void testAlgorithmNormalization() {
        assertEquals("ACE", createService("ACE").getSummaryAlgorithm());
        assertEquals("ReasoningBank", createService("RB").getSummaryAlgorithm());
        assertEquals("ReasoningBank", createService("REASONINGBANK").getSummaryAlgorithm());
        assertEquals("ReMe", createService("REME").getSummaryAlgorithm());
        assertEquals("RefCon", createService("REFCON").getSummaryAlgorithm());
        assertEquals("DivCon", createService("DIVCON").getSummaryAlgorithm());
    }

    private static Stream<Arguments> algorithms() {
        return Stream.of(
            Arguments.of("ACE", "ACE"),
            Arguments.of("RB", "ReasoningBank"),
            Arguments.of("REME", "ReMe"),
            Arguments.of("REFCON", "RefCon"),
            Arguments.of("DIVCON", "DivCon")
        );
    }

    private TaskMemoryService createService(String algorithm) {
        return new TaskMemoryService(
            "gpt-5.2",
            "text-embedding-3-small",
            null,
            algorithm,
            algorithm
        );
    }

    private AddMemoryRequest createAddRequest(String algorithm, String content, String identifier) {
        AddMemoryRequest request = new AddMemoryRequest();
        request.setContent(content);
        switch (algorithm) {
            case "ReasoningBank" -> {
                request.setTitle("Memory " + identifier);
                request.setDescription("Description for memory " + identifier);
            }
            case "ReMe", "RefCon", "DivCon" ->
                request.setWhenToUse("When to use memory " + identifier);
            default ->
                request.setSection("python_best_practices");
        }
        return request;
    }

    private void assertAlgorithmSpecificRetrievedMemory(
        String algorithm,
        Map<String, Object> memory,
        String content
    ) {
        switch (algorithm) {
            case "ReasoningBank" -> {
                assertEquals("Memory 1", memory.get("title"));
                assertEquals("Description for memory 1", memory.get("description"));
                assertEquals(content, memory.get("content"));
            }
            case "ReMe", "RefCon", "DivCon" -> {
                assertEquals("When to use memory 1", memory.get("when_to_use"));
                assertEquals(content, memory.get("content"));
            }
            default -> {
                assertEquals("python_best_practices", memory.get("section"));
                assertTrue(String.valueOf(memory.get("content")).contains("lru_cache"));
            }
        }
    }

    private void assertPlaybookContents(String algorithm, List<Map<String, Object>> memories) {
        switch (algorithm) {
            case "ReasoningBank" -> {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> firstMemory = (List<Map<String, Object>>) memories.get(0).get("memory");
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> secondMemory = (List<Map<String, Object>>) memories.get(1).get("memory");
                List<String> contents = List.of(
                    String.valueOf(firstMemory.get(0).get("content")),
                    String.valueOf(secondMemory.get(0).get("content"))
                );
                List<String> titles = List.of(
                    String.valueOf(firstMemory.get(0).get("title")),
                    String.valueOf(secondMemory.get(0).get("title"))
                );
                assertTrue(contents.contains("Content 1"));
                assertTrue(contents.contains("Content 2"));
                assertTrue(titles.contains("Memory 1"));
                assertTrue(titles.contains("Memory 2"));
            }
            case "ReMe", "RefCon", "DivCon" -> {
                List<String> contents = memories.stream()
                    .map(memory -> String.valueOf(memory.get("content")))
                    .toList();
                assertTrue(contents.contains("Content 1"));
                assertTrue(contents.contains("Content 2"));
            }
            default -> {
                List<String> contents = memories.stream()
                    .map(memory -> String.valueOf(memory.get("content")))
                    .toList();
                assertTrue(contents.stream().anyMatch(content -> content.contains("Content 1")));
                assertTrue(contents.stream().anyMatch(content -> content.contains("Content 2")));
            }
        }
    }
}
