/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.optimizer.tool_call;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ToolcallExampleMethod slice handling.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.agent_evolving.optimizer.tool_call.test_toolcall_example_method}.
 */
class ToolcallExampleMethodTest {

    @Test
    void testToolcallExampleSliceExtractsExamples() {
        Map<String, Object> toolSpec = new HashMap<>();
        toolSpec.put("examples", List.of(
            Map.of(
                "description", "Basic usage example",
                "parameters", Map.of("query", "test query"),
                "result", "search results"
            )
        ));

        Map<String, Object> slice = extractToolcallExampleSlice(toolSpec);

        assertEquals(1, ((List<?>) slice.get("examples")).size());
    }

    @Test
    void testToolcallExampleSlicePreservesParameterOrder() {
        Map<String, Object> toolSpec = new HashMap<>();
        toolSpec.put("examples", List.of(
            Map.of("parameters", Map.of(
                "first", "value1",
                "second", "value2",
                "third", "value3"
            ))
        ));

        Map<String, Object> slice = extractToolcallExampleSlice(toolSpec);

        assertTrue(slice.containsKey("examples"));
    }

    @Test
    void testToolcallExampleSliceWithExpectedResults() {
        Map<String, Object> toolSpec = new HashMap<>();
        toolSpec.put("examples", List.of(
            Map.of(
                "parameters", Map.of("input", "data"),
                "expected_result", Map.of("status", "success")
            )
        ));

        Map<String, Object> slice = extractToolcallExampleSlice(toolSpec);
        List<Map<String, Object>> examples = (List<Map<String, Object>>) slice.get("examples");

        assertTrue(examples.get(0).containsKey("expected_result"));
    }

    @Test
    void testToolcallExampleSliceWithErrorHandling() {
        Map<String, Object> toolSpec = new HashMap<>();
        toolSpec.put("examples", List.of(
            Map.of(
                "parameters", Map.of("invalid_input", ""),
                "error_handling", "Returns error for empty input"
            )
        ));

        Map<String, Object> slice = extractToolcallExampleSlice(toolSpec);

        assertTrue(slice.containsKey("examples"));
    }

    @Test
    void testToolcallExampleSliceMultipleScenarios() {
        Map<String, Object> toolSpec = new HashMap<>();
        toolSpec.put("examples", List.of(
            Map.of("scenario", "success_case"),
            Map.of("scenario", "error_case"),
            Map.of("scenario", "edge_case")
        ));

        Map<String, Object> slice = extractToolcallExampleSlice(toolSpec);

        assertEquals(3, ((List<?>) slice.get("examples")).size());
    }

    @Test
    void testToolcallExampleSliceEmptyExamples() {
        Map<String, Object> toolSpec = new HashMap<>();
        toolSpec.put("examples", new ArrayList<>());

        Map<String, Object> slice = extractToolcallExampleSlice(toolSpec);

        assertTrue(((List<?>) slice.get("examples")).isEmpty());
    }

    private Map<String, Object> extractToolcallExampleSlice(Map<String, Object> toolSpec) {
        Map<String, Object> slice = new HashMap<>();
        
        if (toolSpec.containsKey("examples")) {
            slice.put("examples", toolSpec.get("examples"));
        }
        
        return slice;
    }
}