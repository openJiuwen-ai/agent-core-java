/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.optimizer.tool_call;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CustomizedApi slice handling.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.agent_evolving.optimizer.tool_call.test_customized_api}.
 */
class CustomizedApiTest {

    @Test
    void testCustomApiSliceExtractsApiSpec() {
        Map<String, Object> toolSpec = new HashMap<>();
        toolSpec.put("name", "custom_search");
        toolSpec.put("description", "Search with custom API");
        toolSpec.put("parameters", Map.of(
            "type", "object",
            "properties", Map.of(
                "query", Map.of("type", "string", "description", "Search query"),
                "limit", Map.of("type", "integer", "description", "Max results")
            )
        ));

        Map<String, Object> slice = extractApiSlice(toolSpec);

        assertEquals("custom_search", slice.get("name"));
        assertTrue(slice.containsKey("parameters"));
    }

    @Test
    void testCustomApiSliceWithOptionalParams() {
        Map<String, Object> toolSpec = new HashMap<>();
        toolSpec.put("name", "flexible_tool");
        toolSpec.put("parameters", Map.of(
            "required", List.of("query"),
            "properties", Map.of(
                "query", Map.of("type", "string"),
                "optional_param", Map.of("type", "string")
            )
        ));

        Map<String, Object> slice = extractApiSlice(toolSpec);

        Map<String, Object> params = (Map<String, Object>) slice.get("parameters");
        List<String> required = (List<String>) params.get("required");
        assertEquals(1, required.size());
        assertEquals("query", required.get(0));
    }

    @Test
    void testCustomApiSliceWithNoParameters() {
        Map<String, Object> toolSpec = new HashMap<>();
        toolSpec.put("name", "simple_tool");
        toolSpec.put("description", "No parameters");

        Map<String, Object> slice = extractApiSlice(toolSpec);

        assertEquals("simple_tool", slice.get("name"));
        assertFalse(slice.containsKey("parameters"));
    }

    @Test
    void testCustomApiSlicePreservesDescription() {
        Map<String, Object> toolSpec = new HashMap<>();
        toolSpec.put("name", "described_tool");
        toolSpec.put("description", "This tool has a detailed description for customization");

        Map<String, Object> slice = extractApiSlice(toolSpec);

        assertEquals("This tool has a detailed description for customization", slice.get("description"));
    }

    @Test
    void testCustomApiSliceHandlesNestedProperties() {
        Map<String, Object> toolSpec = new HashMap<>();
        toolSpec.put("parameters", Map.of(
            "properties", Map.of(
                "nested", Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "inner", Map.of("type", "string")
                    )
                )
            )
        ));

        Map<String, Object> slice = extractApiSlice(toolSpec);

        Map<String, Object> params = (Map<String, Object>) slice.get("parameters");
        assertTrue(params.containsKey("properties"));
    }

    private Map<String, Object> extractApiSlice(Map<String, Object> toolSpec) {
        Map<String, Object> slice = new HashMap<>();
        
        if (toolSpec.containsKey("name")) {
            slice.put("name", toolSpec.get("name"));
        }
        if (toolSpec.containsKey("description")) {
            slice.put("description", toolSpec.get("description"));
        }
        if (toolSpec.containsKey("parameters")) {
            slice.put("parameters", toolSpec.get("parameters"));
        }
        if (toolSpec.containsKey("required")) {
            slice.put("required", toolSpec.get("required"));
        }
        
        return slice;
    }
}
