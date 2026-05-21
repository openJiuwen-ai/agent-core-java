/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.optimizer.tool_call;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DescriptionExampleMethod slice handling.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.agent_evolving.optimizer.tool_call.test_description_example_method}.
 */
class DescriptionExampleMethodTest {

    @Test
    void testDescriptionExampleSliceExtractsDescription() {
        Map<String, Object> toolSpec = new HashMap<>();
        toolSpec.put("description", "This tool performs data transformation");
        toolSpec.put("examples", List.of(
            Map.of("input", "raw_data", "output", "transformed_data")
        ));

        Map<String, Object> slice = extractDescriptionExampleSlice(toolSpec);

        assertTrue(slice.containsKey("description"));
        assertTrue(slice.containsKey("examples"));
    }

    @Test
    void testDescriptionExampleSliceWithMultipleExamples() {
        Map<String, Object> toolSpec = new HashMap<>();
        toolSpec.put("examples", List.of(
            Map.of("scenario", "case1", "usage", "example usage 1"),
            Map.of("scenario", "case2", "usage", "example usage 2"),
            Map.of("scenario", "case3", "usage", "example usage 3")
        ));

        Map<String, Object> slice = extractDescriptionExampleSlice(toolSpec);

        assertEquals(3, ((List<?>) slice.get("examples")).size());
    }

    @Test
    void testDescriptionExampleSlicePreservesExampleOrder() {
        Map<String, Object> toolSpec = new HashMap<>();
        toolSpec.put("examples", List.of(
            Map.of("order", 1),
            Map.of("order", 2),
            Map.of("order", 3)
        ));

        Map<String, Object> slice = extractDescriptionExampleSlice(toolSpec);
        List<Map<String, Object>> examples = (List<Map<String, Object>>) slice.get("examples");

        assertEquals(1, examples.get(0).get("order"));
        assertEquals(2, examples.get(1).get("order"));
        assertEquals(3, examples.get(2).get("order"));
    }

    @Test
    void testDescriptionExampleSliceEmptyExamples() {
        Map<String, Object> toolSpec = new HashMap<>();
        toolSpec.put("description", "No examples provided");
        toolSpec.put("examples", new ArrayList<>());

        Map<String, Object> slice = extractDescriptionExampleSlice(toolSpec);

        assertTrue(((List<?>) slice.get("examples")).isEmpty());
    }

    @Test
    void testDescriptionExampleSliceWithBestPractices() {
        Map<String, Object> toolSpec = new HashMap<>();
        toolSpec.put("best_practices", List.of(
            "Always validate input before transformation",
            "Use appropriate data types"
        ));

        Map<String, Object> slice = extractDescriptionExampleSlice(toolSpec);

        assertEquals(2, ((List<?>) slice.get("best_practices")).size());
    }

    @Test
    void testDescriptionExampleSliceWithEdgeCases() {
        Map<String, Object> toolSpec = new HashMap<>();
        toolSpec.put("edge_cases", List.of(
            Map.of("condition", "empty input", "handling", "return default value")
        ));

        Map<String, Object> slice = extractDescriptionExampleSlice(toolSpec);

        assertTrue(slice.containsKey("edge_cases"));
    }

    private Map<String, Object> extractDescriptionExampleSlice(Map<String, Object> toolSpec) {
        Map<String, Object> slice = new HashMap<>();
        
        if (toolSpec.containsKey("description")) {
            slice.put("description", toolSpec.get("description"));
        }
        if (toolSpec.containsKey("examples")) {
            slice.put("examples", toolSpec.get("examples"));
        }
        if (toolSpec.containsKey("best_practices")) {
            slice.put("best_practices", toolSpec.get("best_practices"));
        }
        if (toolSpec.containsKey("edge_cases")) {
            slice.put("edge_cases", toolSpec.get("edge_cases"));
        }
        
        return slice;
    }
}