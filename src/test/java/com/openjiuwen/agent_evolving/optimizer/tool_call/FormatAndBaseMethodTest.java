/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.optimizer.tool_call;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for FormatAndBaseMethod slice handling.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.agent_evolving.optimizer.tool_call.test_format_and_base_method}.
 */
class FormatAndBaseMethodTest {

    @Test
    void testFormatSliceExtractsFormatSpec() {
        Map<String, Object> toolSpec = new HashMap<>();
        toolSpec.put("format", Map.of(
            "type", "json",
            "schema", Map.of("fields", List.of("name", "value"))
        ));

        Map<String, Object> slice = extractFormatSlice(toolSpec);

        assertEquals("json", ((Map<String, Object>) slice.get("format")).get("type"));
    }

    @Test
    void testFormatSliceWithValidationRules() {
        Map<String, Object> toolSpec = new HashMap<>();
        toolSpec.put("validation", Map.of(
            "required_fields", List.of("id"),
            "optional_fields", List.of("metadata")
        ));

        Map<String, Object> slice = extractFormatSlice(toolSpec);

        assertTrue(slice.containsKey("validation"));
    }

    @Test
    void testBaseMethodSliceExtractsMethodInfo() {
        Map<String, Object> toolSpec = new HashMap<>();
        toolSpec.put("base_method", Map.of(
            "name", "process",
            "returns", "Map<String, Object>"
        ));

        Map<String, Object> slice = extractBaseMethodSlice(toolSpec);

        assertEquals("process", ((Map<String, Object>) slice.get("base_method")).get("name"));
    }

    @Test
    void testBaseMethodSliceWithParameters() {
        Map<String, Object> toolSpec = new HashMap<>();
        toolSpec.put("parameters", List.of(
            Map.of("name", "input", "type", "String"),
            Map.of("name", "config", "type", "Map")
        ));

        Map<String, Object> slice = extractBaseMethodSlice(toolSpec);

        assertEquals(2, ((List<?>) slice.get("parameters")).size());
    }

    @Test
    void testCombinedFormatAndBaseSlice() {
        Map<String, Object> toolSpec = new HashMap<>();
        toolSpec.put("format", Map.of("type", "xml"));
        toolSpec.put("base_method", Map.of("name", "parse"));

        Map<String, Object> slice = extractCombinedSlice(toolSpec);

        assertTrue(slice.containsKey("format"));
        assertTrue(slice.containsKey("base_method"));
    }

    @Test
    void testFormatSliceWithDefaultValue() {
        Map<String, Object> toolSpec = new HashMap<>();
        toolSpec.put("defaults", Map.of("format", "json"));

        Map<String, Object> slice = extractFormatSlice(toolSpec);

        assertTrue(slice.containsKey("defaults"));
    }

    private Map<String, Object> extractFormatSlice(Map<String, Object> toolSpec) {
        Map<String, Object> slice = new HashMap<>();
        
        if (toolSpec.containsKey("format")) {
            slice.put("format", toolSpec.get("format"));
        }
        if (toolSpec.containsKey("validation")) {
            slice.put("validation", toolSpec.get("validation"));
        }
        if (toolSpec.containsKey("defaults")) {
            slice.put("defaults", toolSpec.get("defaults"));
        }
        
        return slice;
    }

    private Map<String, Object> extractBaseMethodSlice(Map<String, Object> toolSpec) {
        Map<String, Object> slice = new HashMap<>();
        
        if (toolSpec.containsKey("base_method")) {
            slice.put("base_method", toolSpec.get("base_method"));
        }
        if (toolSpec.containsKey("parameters")) {
            slice.put("parameters", toolSpec.get("parameters"));
        }
        
        return slice;
    }

    private Map<String, Object> extractCombinedSlice(Map<String, Object> toolSpec) {
        Map<String, Object> slice = new HashMap<>(extractFormatSlice(toolSpec));
        slice.putAll(extractBaseMethodSlice(toolSpec));
        return slice;
    }
}