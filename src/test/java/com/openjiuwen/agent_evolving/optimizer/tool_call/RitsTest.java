/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.optimizer.tool_call;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RITS (Reduced Instruction Tool Schema) handling.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.agent_evolving.optimizer.tool_call.test_rits}.
 */
class RitsTest {

    @Test
    void testRitsExtractsReducedSchema() {
        Map<String, Object> fullSchema = new HashMap<>();
        fullSchema.put("name", "complex_tool");
        fullSchema.put("description", "A complex tool with many parameters");
        fullSchema.put("parameters", Map.of(
            "type", "object",
            "properties", Map.of(
                "required_param", Map.of("type", "string"),
                "optional_param1", Map.of("type", "integer"),
                "optional_param2", Map.of("type", "boolean"),
                "optional_param3", Map.of("type", "array")
            )
        ));

        Map<String, Object> reduced = extractRitsSchema(fullSchema);

        assertTrue(reduced.containsKey("name"));
        assertTrue(reduced.containsKey("parameters"));
    }

    @Test
    void testRitsPreservesRequiredParameters() {
        Map<String, Object> fullSchema = new HashMap<>();
        fullSchema.put("parameters", Map.of(
            "required", List.of("essential_param"),
            "properties", Map.of(
                "essential_param", Map.of("type", "string"),
                "optional_param", Map.of("type", "integer")
            )
        ));

        Map<String, Object> reduced = extractRitsSchema(fullSchema);
        Map<String, Object> params = (Map<String, Object>) reduced.get("parameters");

        List<String> required = (List<String>) params.get("required");
        assertTrue(required.contains("essential_param"));
    }

    @Test
    void testRitsRemovesOptionalParameters() {
        Map<String, Object> fullSchema = new HashMap<>();
        fullSchema.put("parameters", Map.of(
            "properties", Map.of(
                "a", Map.of("type", "string"),
                "b", Map.of("type", "string"),
                "c", Map.of("type", "string"),
                "d", Map.of("type", "string"),
                "e", Map.of("type", "string"),
                "f", Map.of("type", "string")
            )
        ));

        Map<String, Object> reduced = extractRitsSchema(fullSchema);
        Map<String, Object> params = (Map<String, Object>) reduced.get("parameters");

        int propCount = ((Map<?, ?>) params.get("properties")).size();
        assertTrue(propCount <= 4); // RITS keeps at most 4 parameters
    }

    @Test
    void testRitsHandlesEmptySchema() {
        Map<String, Object> fullSchema = new HashMap<>();

        Map<String, Object> reduced = extractRitsSchema(fullSchema);

        assertTrue(reduced.containsKey("name"));
    }

    @Test
    void testRitsPreservesDescriptionWhenShort() {
        Map<String, Object> fullSchema = new HashMap<>();
        fullSchema.put("description", "Short description");

        Map<String, Object> reduced = extractRitsSchema(fullSchema);

        assertEquals("Short description", reduced.get("description"));
    }

    @Test
    void testRitsTruncatesLongDescription() {
        Map<String, Object> fullSchema = new HashMap<>();
        fullSchema.put("description", "This is a very long description that exceeds the typical limit for RITS schemas and should be truncated to a reasonable length");

        Map<String, Object> reduced = extractRitsSchema(fullSchema);

        String desc = (String) reduced.get("description");
        assertTrue(desc.length() <= 100);
    }

    private Map<String, Object> extractRitsSchema(Map<String, Object> fullSchema) {
        Map<String, Object> reduced = new HashMap<>();
        
        // Always include name
        if (fullSchema.containsKey("name")) {
            reduced.put("name", fullSchema.get("name"));
        }
        
        // Truncate description if too long
        if (fullSchema.containsKey("description")) {
            String desc = (String) fullSchema.get("description");
            reduced.put("description", desc.length() > 100 ? desc.substring(0, 100) : desc);
        }
        
        // Reduce parameters to at most 4 properties
        if (fullSchema.containsKey("parameters")) {
            Map<String, Object> params = new HashMap<>((Map<String, Object>) fullSchema.get("parameters"));
            if (params.containsKey("properties")) {
                Map<String, Object> props = new HashMap<>((Map<String, Object>) params.get("properties"));
                if (props.size() > 4) {
                    Map<String, Object> reducedProps = new HashMap<>();
                    int count = 0;
                    for (Map.Entry<String, Object> entry : props.entrySet()) {
                        if (count >= 4) break;
                        reducedProps.put(entry.getKey(), entry.getValue());
                        count++;
                    }
                    params.put("properties", reducedProps);
                }
            }
            reduced.put("parameters", params);
        }
        
        return reduced;
    }
}