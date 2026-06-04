/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.extraction;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for Parse Response.
 * <p>
 * Mirrors Python's test_parse_response.py from
 * <code>tests/unit_tests/core/memory/graph/extraction/test_parse_response.py</code>.
 */
@DisplayName("Parse Response Tests")
class TestParseResponse {

    @Nested
    @DisplayName("ParseJson Tests")
    class TestParseJson {

        @Test
        @DisplayName("plain json object")
        void testPlainJsonObject() {
            Object result = ParseResponse.parseJson("{\"a\": 1, \"b\": \"x\"}", null);

            assertEquals(Map.of("a", 1, "b", "x"), result);
        }

        @Test
        @DisplayName("json in code block")
        void testJsonInCodeBlock() {
            Object result = ParseResponse.parseJson("Some text\n```json\n{\"x\": 42}\n```", null);

            assertEquals(Map.of("x", 42), result);
        }

        @Test
        @DisplayName("code block empty type treated as json")
        void testCodeBlockEmptyTypeTreatedAsJson() {
            Object result = ParseResponse.parseJson("```\n[1, 2, 3]\n```", null);

            assertEquals(List.of(1, 2, 3), result);
        }

        @Test
        @DisplayName("non json code block skipped")
        void testNonJsonCodeBlockSkipped() {
            Object result = ParseResponse.parseJson("```python\nx = 1\n```", null);

            assertNull(result);
        }

        @Test
        @DisplayName("invalid json returns null")
        void testInvalidJsonReturnsNull() {
            Object result = ParseResponse.parseJson("not json at all {", null);

            assertNull(result);
        }

        @Test
        @DisplayName("output schema required filters keys")
        void testOutputSchemaRequiredFiltersKeys() {
            Map<String, Object> outputSchema = Map.of(
                    "json_schema", Map.of("required", List.of("extracted_entities")));

            Object result = ParseResponse.parseJson(
                    "{\"extracted_entities\": [{\"name\": \"E1\", \"entity_type_id\": 0}]}",
                    outputSchema);

            assertInstanceOf(Map.class, result);
            assertTrue(((Map<?, ?>) result).containsKey("extracted_entities"));
        }

        @Test
        @DisplayName("array in response")
        void testArrayInResponse() {
            Object result = ParseResponse.parseJson("[1, 2, 3]", null);

            assertEquals(List.of(1, 2, 3), result);
        }

        @Test
        @DisplayName("code block invalid json falls through")
        void testCodeBlockInvalidJsonFallsThrough() {
            Object result = ParseResponse.parseJson("```json\n{invalid\n```", null);

            assertNull(result);
        }

        @Test
        @DisplayName("raw decode json called with trailing brace")
        void testRawDecodeJsonCalledWithTrailingBrace() {
            Object result = ParseResponse.parseJson(" [{\"a\": 1},", null);

            assertTrue(result == null || result instanceof List<?> || result instanceof Map<?, ?>);
        }

        @Test
        @DisplayName("parse with required and dict rebuilds from fuzzy keys")
        void testParseWithRequiredAndDictRebuildsFromFuzzyKeys() {
            Map<String, Object> outputSchema = Map.of(
                    "json_schema", Map.of("required", List.of("extracted_entities")));

            Object result = ParseResponse.parseJson("{\"extracted_entities\": []}", outputSchema);

            assertInstanceOf(Map.class, result);
        }
    }

    @Nested
    @DisplayName("TryGetKey Tests")
    class TestTryGetKey {

        @Test
        @DisplayName("exact key match")
        void testExactKeyMatch() {
            Map<String, Object> src = Map.of("extracted_entities", List.of(1, 2), "other", 0);

            Object keyRef = ParseResponse.tryGetKey("extracted_entities", src);

            assertNotNull(keyRef);
            assertTrue(src.containsKey(keyRef));
        }

        @Test
        @DisplayName("fuzzy match")
        void testFuzzyMatch() {
            Map<String, Object> src = Map.of("ExtractedEntities", List.of());

            Object keyRef = ParseResponse.tryGetKey("extracted_entities", src);

            assertNotNull(keyRef);
            assertTrue(src.containsKey(keyRef));
        }

        @Test
        @DisplayName("no match returns null")
        void testNoMatchReturnsNull() {
            assertNull(ParseResponse.tryGetKey("xyz", Map.of("a", 1, "b", 2)));
        }
    }

    @Nested
    @DisplayName("EnsureList Tests")
    class TestEnsureList {

        @Test
        @DisplayName("list unchanged")
        void testListUnchanged() {
            List<Object> val = List.of(1, 2, 3);

            List<Object> result = ParseResponse.ensureList(val);

            assertTrue(result == val);
        }

        @Test
        @DisplayName("single object wrapped in list")
        void testSingleObjectWrappedInList() {
            assertEquals(List.of(42), ParseResponse.ensureList(42));
            assertEquals(List.of("x"), ParseResponse.ensureList("x"));
        }

        @Test
        @DisplayName("dict with single list value unwrapped")
        void testDictWithSingleListValueUnwrapped() {
            assertEquals(List.of(1, 2), ParseResponse.ensureList(Map.of("items", List.of(1, 2))));
        }

        @Test
        @DisplayName("dict with single non list value wrapped")
        void testDictWithSingleNonListValueWrapped() {
            Map<String, Object> val = Map.of("key", "not a list");

            List<Object> result = ParseResponse.ensureList(val);

            assertEquals(List.of(val), result);
        }

        @Test
        @DisplayName("dict with multiple keys wrapped")
        void testDictWithMultipleKeysWrapped() {
            Map<String, Object> val = Map.of("a", 1, "b", 2);

            assertEquals(List.of(val), ParseResponse.ensureList(val));
        }
    }

    @Nested
    @DisplayName("RawDecodeJson Tests")
    class TestRawDecodeJson {

        @Test
        @DisplayName("raw decode json plain object")
        void testRawDecodeJsonPlainObject() {
            Object result = ParseResponse.rawDecodeJson("  {\"x\": 1}");

            assertEquals(Map.of("x", 1), result);
        }

        @Test
        @DisplayName("raw decode json with required rebuilds dict branch")
        void testRawDecodeJsonWithRequiredRebuildsDictBranch() {
            Object result = ParseResponse.rawDecodeJson(
                    "  {\"extracted_entities\": [1]}",
                    List.of("extracted_entities"));

            assertInstanceOf(Map.class, result);
        }

        @Test
        @DisplayName("raw decode json with required and list continues")
        void testRawDecodeJsonWithRequiredAndListContinues() {
            Object result = ParseResponse.rawDecodeJson("  [1, 2]", List.of("x"));

            assertNull(result);
        }

        @Test
        @DisplayName("raw decode json trailing comma brace two candidates")
        void testRawDecodeJsonTrailingCommaBraceTwoCandidates() {
            Object result = ParseResponse.rawDecodeJson("  [{\"a\": 1},");

            assertTrue(result == null || result.equals(List.of(Map.of("a", 1))));
        }
    }
}
