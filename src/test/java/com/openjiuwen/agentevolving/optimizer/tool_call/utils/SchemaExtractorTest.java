package com.openjiuwen.agentevolving.optimizer.tool_call.utils;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Mirrors Python's {@code tests/unit_tests/agent_evolving/optimizer/tool_call/test_schema_extractor.py}.
 */
class SchemaExtractorTest {

    @Test
    void extractSchemaRecursesMapsAndPreservesLists() {
        Map<String, Object> src = new LinkedHashMap<>();
        src.put("name", "tool");
        src.put("parameters", Map.of(
                "type", "object",
                "properties", Map.of("q", Map.of("type", "string"), "k", List.of(1, 2)),
                "required", List.of("q")
        ));
        src.put("enabled", true);

        Map<String, Object> out = SchemaExtractor.extractSchema(src);

        assertEquals("", out.get("name"));
        assertEquals("", out.get("enabled"));
        assertEquals(List.of("q"), ((Map<?, ?>) out.get("parameters")).get("required"));
        assertEquals("", ((Map<?, ?>) ((Map<?, ?>) ((Map<?, ?>) out.get("parameters")).get("properties")).get("q")).get("type"));
        assertEquals(List.of(1, 2), ((Map<?, ?>) ((Map<?, ?>) out.get("parameters")).get("properties")).get("k"));
    }

    @Test
    void extractSchemaParsesJsonStringsAndRejectsNonJsonText() {
        assertEquals(Map.of("a", "", "b", Map.of("c", "")), SchemaExtractor.extractSchema("{\"a\":1,\"b\":{\"c\":2}}"));
        assertEquals(Map.of(), SchemaExtractor.extractSchema("not-json"));
    }
}
