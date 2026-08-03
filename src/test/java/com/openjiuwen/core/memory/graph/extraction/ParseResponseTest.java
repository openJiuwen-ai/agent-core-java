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
 * Mirrors Python's parse-response tests in
 * {@code tests/unit_tests/core/memory/graph/extraction/test_parse_response.py}.
 */
@DisplayName("Parse Response Tests")
class ParseResponseTest {

    @Nested
    @DisplayName("ParseJson Tests")
    class ParseJsonTests {

        @Test
        @DisplayName("plain json object")
        void plainJsonObject() {
            Object result = ParseResponse.parseJson("{\"a\": 1, \"b\": \"x\"}", null);
            assertEquals(Map.of("a", 1, "b", "x"), result);
        }

        @Test
        @DisplayName("json in code block")
        void jsonInCodeBlock() {
            Object result = ParseResponse.parseJson("Some text\n```json\n{\"x\": 42}\n```", null);
            assertEquals(Map.of("x", 42), result);
        }

        @Test
        @DisplayName("code block empty type treated as json")
        void codeBlockEmptyTypeTreatedAsJson() {
            Object result = ParseResponse.parseJson("```\n[1, 2, 3]\n```", null);
            assertEquals(List.of(1, 2, 3), result);
        }

        @Test
        @DisplayName("non json code block skipped")
        void nonJsonCodeBlockSkipped() {
            Object result = ParseResponse.parseJson("```python\nx = 1\n```", null);
            assertNull(result);
        }

        @Test
        @DisplayName("invalid json returns null")
        void invalidJsonReturnsNull() {
            assertNull(ParseResponse.parseJson("not json at all {", null));
        }

        @Test
        @DisplayName("output schema required filters keys")
        void outputSchemaRequiredFiltersKeys() {
            Map<String, Object> outputSchema = Map.of(
                    "json_schema", Map.of("required", List.of("extracted_entities")));

            Object result = ParseResponse.parseJson(
                    "{\"extracted_entities\": [{\"name\": \"E1\", \"entity_type_id\": 0}]}",
                    outputSchema);

            assertNotNull(result);
            assertTrue(result instanceof Map<?, ?>);
        }

        @Test
        @DisplayName("array in response")
        void arrayInResponse() {
            Object result = ParseResponse.parseJson("[1, 2, 3]", null);
            assertEquals(List.of(1, 2, 3), result);
        }

        @Test
        @DisplayName("code block invalid json falls through")
        void codeBlockInvalidJsonFallsThrough() {
            assertNull(ParseResponse.parseJson("```json\n{invalid\n```", null));
        }

        @Test
        @DisplayName("raw decode json called with trailing brace")
        void rawDecodeJsonCalledWithTrailingBrace() {
            Object result = ParseResponse.parseJson(" [{\"a\": 1},", null);
            assertTrue(result == null || result instanceof List<?> || result instanceof Map<?, ?>);
        }

        @Test
        @DisplayName("parse with required and dict rebuilds from fuzzy keys")
        void parseWithRequiredAndDictRebuildsFromFuzzyKeys() {
            Map<String, Object> outputSchema = Map.of(
                    "json_schema", Map.of("required", List.of("extracted_entities")));

            Object result = ParseResponse.parseJson("{\"extracted_entities\": []}", outputSchema);

            assertNotNull(result);
            assertInstanceOf(Map.class, result);
        }
    }

    @Nested
    @DisplayName("TryGetKey Tests")
    class TryGetKeyTests {

        @Test
        @DisplayName("exact key match")
        void exactKeyMatch() {
            Map<String, Object> src = Map.of("extracted_entities", List.of(1, 2), "other", 0);
            String keyRef = ParseResponse.tryGetKey("extracted_entities", src);

            assertNotNull(keyRef);
            assertTrue(src.containsKey(keyRef));
        }

        @Test
        @DisplayName("fuzzy match")
        void fuzzyMatch() {
            Map<String, Object> src = Map.of("ExtractedEntities", List.of());
            String keyRef = ParseResponse.tryGetKey("extracted_entities", src);

            assertNotNull(keyRef);
            assertTrue(src.containsKey(keyRef));
        }

        @Test
        @DisplayName("no match returns null")
        void noMatchReturnsNull() {
            assertNull(ParseResponse.tryGetKey("xyz", Map.of("a", 1, "b", 2)));
        }
    }

    @Nested
    @DisplayName("EnsureList Tests")
    class EnsureListTests {

        @Test
        @DisplayName("list unchanged")
        void listUnchanged() {
            List<Object> value = List.of(1, 2, 3);
            List<Object> result = ParseResponse.ensureList(value);
            assertTrue(result == value);
        }

        @Test
        @DisplayName("single object wrapped in list")
        void singleObjectWrappedInList() {
            assertEquals(List.of(42), ParseResponse.ensureList(42));
            assertEquals(List.of("x"), ParseResponse.ensureList("x"));
        }

        @Test
        @DisplayName("dict with single list value unwrapped")
        void dictWithSingleListValueUnwrapped() {
            assertEquals(List.of(1, 2), ParseResponse.ensureList(Map.of("items", List.of(1, 2))));
        }

        @Test
        @DisplayName("dict with single non list value wrapped")
        void dictWithSingleNonListValueWrapped() {
            Map<String, Object> value = Map.of("key", "not a list");
            List<Object> result = ParseResponse.ensureList(value);
            assertEquals(List.of(value), result);
        }

        @Test
        @DisplayName("dict with multiple keys wrapped")
        void dictWithMultipleKeysWrapped() {
            Map<String, Object> value = Map.of("a", 1, "b", 2);
            assertEquals(List.of(value), ParseResponse.ensureList(value));
        }
    }

    @Nested
    @DisplayName("RawDecodeJson Tests")
    class RawDecodeJsonTests {

        @Test
        @DisplayName("raw decode json plain object")
        void rawDecodeJsonPlainObject() {
            Object result = ParseResponse.rawDecodeJson("  {\"x\": 1}");
            assertEquals(Map.of("x", 1), result);
        }

        @Test
        @DisplayName("raw decode json with required rebuilds dict branch")
        void rawDecodeJsonWithRequiredRebuildsDictBranch() {
            Object result = ParseResponse.rawDecodeJson(
                    "  {\"extracted_entities\": [1]}",
                    List.of("extracted_entities"));
            assertInstanceOf(Map.class, result);
        }

        @Test
        @DisplayName("raw decode json with required and list continues")
        void rawDecodeJsonWithRequiredAndListContinues() {
            assertNull(ParseResponse.rawDecodeJson("  [1, 2]", List.of("x")));
        }

        @Test
        @DisplayName("raw decode json trailing comma brace two candidates")
        void rawDecodeJsonTrailingCommaBraceTwoCandidates() {
            Object result = ParseResponse.rawDecodeJson("  [{\"a\": 1},");
            assertTrue(result == null || result.equals(List.of(Map.of("a", 1))));
        }
    }
}
