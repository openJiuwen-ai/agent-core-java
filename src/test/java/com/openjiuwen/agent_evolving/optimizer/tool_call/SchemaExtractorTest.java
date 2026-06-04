/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.optimizer.tool_call;

import com.openjiuwen.agent_evolving.optimizer.tool_call.utils.SchemaExtractor;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for SchemaExtractor functionality.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.agent_evolving.optimizer.tool_call.test_schema_extractor}.
 */
class SchemaExtractorTest {

    @Test
    void testExtractSchemaWithDictAndNestedValues() {
        Map<String, Object> src = new LinkedHashMap<>();
        src.put("name", "tool");
        src.put("parameters", Map.of(
                "type", "object",
                "properties", Map.of(
                        "q", Map.of("type", "string"),
                        "k", List.of(1, 2)
                ),
                "required", List.of("q")
        ));
        src.put("enabled", true);

        Map<String, Object> out = SchemaExtractor.extractSchema(src);

        assertEquals("", out.get("name"));
        assertEquals("", out.get("enabled"));
        Map<?, ?> parameters = (Map<?, ?>) out.get("parameters");
        assertEquals(List.of("q"), parameters.get("required"));
        Map<?, ?> properties = (Map<?, ?>) parameters.get("properties");
        assertEquals("", ((Map<?, ?>) properties.get("q")).get("type"));
        assertEquals(List.of(1, 2), properties.get("k"));
    }

    @Test
    void testExtractSchemaWithJsonString() {
        Map<String, Object> out = SchemaExtractor.extractSchema("{\"a\": 1, \"b\": {\"c\": 2}}");

        assertEquals(Map.of("a", "", "b", Map.of("c", "")), out);
    }

    @Test
    void testExtractSchemaWithInvalidJsonString() {
        assertEquals(Map.of(), SchemaExtractor.extractSchema("not-json"));
    }

    @Test
    void testExtractSchemaWithNonDictInput() {
        assertEquals(Map.of(), SchemaExtractor.extractSchema(List.of("not", "a", "dict")));
    }

    @Test
    void testExtractSchemaWithEmptyDict() {
        assertTrue(SchemaExtractor.extractSchema(Map.of()).isEmpty());
    }

    @Test
    void testExtractSchemaPreservesLists() {
        Map<String, Object> out = SchemaExtractor.extractSchema(Map.of("required", List.of("q", "k")));

        assertEquals(List.of("q", "k"), out.get("required"));
    }

    @Test
    void testExtractSchemaRecursesDeeply() {
        Map<String, Object> out = SchemaExtractor.extractSchema(Map.of(
                "outer", Map.of(
                        "middle", Map.of(
                                "inner", "value"
                        )
                )
        ));

        assertEquals(Map.of("outer", Map.of("middle", Map.of("inner", ""))), out);
    }
}
