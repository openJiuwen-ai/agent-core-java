/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.extraction;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for MultilingualBaseModel.
 * <p>
 * Mirrors Python's test_base.py from
 * <code>tests/unit_tests/core/memory/graph/extraction/test_base.py</code>.
 */
@DisplayName("Extraction Base Tests")
class TestExtractionBase {

    private static Map<String, Object> property(String type, String description) {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("type", type);
        property.put("description", description);
        return property;
    }

    private static Map<String, Object> freeFormObjectProperty(String description) {
        Map<String, Object> property = property("object", description);
        property.put("additionalProperties", true);
        return property;
    }

    private static Map<String, Object> objectSchema(String title, Map<String, Object> properties) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("title", title);
        schema.put("type", "object");
        schema.put("properties", properties);
        return schema;
    }

    private static List<String> objectNodesWithWrongAdditionalProperties(Object schema) {
        List<String> bad = new ArrayList<>();
        collectObjectNodesWithWrongAdditionalProperties(schema, "$", bad);
        return bad;
    }

    @SuppressWarnings("unchecked")
    private static void collectObjectNodesWithWrongAdditionalProperties(Object node, String path, List<String> bad) {
        if (node instanceof Map<?, ?> map) {
            if ("object".equals(map.get("type")) && map.get("properties") instanceof Map<?, ?>) {
                if (!Boolean.FALSE.equals(map.get("additionalProperties"))) {
                    bad.add(path + ": additionalProperties=" + map.get("additionalProperties"));
                }
            }
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                collectObjectNodesWithWrongAdditionalProperties(entry.getValue(), path + "." + entry.getKey(), bad);
            }
        } else if (node instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                collectObjectNodesWithWrongAdditionalProperties(list.get(i), path + "[" + i + "]", bad);
            }
        }
    }

    /**
     * Minimal model for testing.
     */
    static class SampleModel extends MultilingualBaseModel {
        @Override
        public Map<String, Object> responseFormat() {
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("name", property("string", "{{[test_name]}}"));
            properties.put("count", property("integer", "{{[test_count]}}"));
            return objectSchema("SampleModel", properties);
        }
    }

    static class StrictInner extends MultilingualBaseModel {
        @Override
        public Map<String, Object> responseFormat() {
            return objectSchema("StrictInner", Map.of("value", property("string", "{{[strict_inner_val]}}")));
        }
    }

    static class StrictNestedRoot extends MultilingualBaseModel {
        @Override
        public Map<String, Object> responseFormat() {
            Map<String, Object> itemRef = new LinkedHashMap<>();
            itemRef.put("$ref", "#/$defs/StrictInner");
            Map<String, Object> items = new LinkedHashMap<>();
            items.put("type", "array");
            items.put("description", "{{[strict_inner_list]}}");
            items.put("items", itemRef);
            Map<String, Object> schema = objectSchema("StrictNestedRoot", Map.of("items", items));
            schema.put("$defs", Map.of("StrictInner", new StrictInner().responseFormat()));
            return schema;
        }
    }

    static class StrictWithDict extends MultilingualBaseModel {
        @Override
        public Map<String, Object> responseFormat() {
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("summary", property("string", "{{[strict_summary]}}"));
            properties.put("attributes", freeFormObjectProperty("{{[strict_attrs]}}"));
            return objectSchema("StrictWithDict", properties);
        }
    }

    static class StrictOuterWithNestedDict extends MultilingualBaseModel {
        @Override
        public Map<String, Object> responseFormat() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("$ref", "#/$defs/StrictWithDict");
            payload.put("description", "{{[strict_outer_payload]}}");
            Map<String, Object> schema = objectSchema("StrictOuterWithNestedDict", Map.of("payload", payload));
            schema.put("$defs", Map.of("StrictWithDict", new StrictWithDict().responseFormat()));
            return schema;
        }
    }

    static class GenericTypes {
        List<String> strings;
    }

    @Nested
    @DisplayName("MultilingualBaseModel Schema Tests")
    class TestMultilingualBaseModelSchema {

        @Test
        @DisplayName("schema returns dict with properties")
        void testSchemaReturnsDictWithProperties() {
            MultilingualBaseModel.MULTILINGUAL_DESCRIPTION.put("cn", Map.of());

            Map<String, Object> schema = new SampleModel().multilingualModelJsonSchema("cn", false);

            assertTrue(schema.containsKey("properties"));
            assertTrue(((Map<?, ?>) schema.get("properties")).containsKey("name"));
            assertTrue(((Map<?, ?>) schema.get("properties")).containsKey("count"));
            assertEquals("string", ((Map<?, ?>) ((Map<?, ?>) schema.get("properties")).get("name")).get("type"));
            assertEquals("integer", ((Map<?, ?>) ((Map<?, ?>) schema.get("properties")).get("count")).get("type"));
        }

        @Test
        @DisplayName("schema replaces descriptions from lookup")
        void testSchemaReplacesDescriptionsFromLookup() {
            MultilingualBaseModel.MULTILINGUAL_DESCRIPTION.put("cn", Map.of(
                    "{{[test_name]}}", "Name",
                    "{{[test_count]}}", "Count"));

            Map<String, Object> schema = new SampleModel().multilingualModelJsonSchema("cn", false);
            Map<?, ?> properties = (Map<?, ?>) schema.get("properties");

            assertEquals("Name", ((Map<?, ?>) properties.get("name")).get("description"));
            assertEquals("Count", ((Map<?, ?>) properties.get("count")).get("description"));
        }

        @Test
        @DisplayName("schema strict sets additional properties false")
        void testSchemaStrictSetsAdditionalPropertiesFalse() {
            MultilingualBaseModel.MULTILINGUAL_DESCRIPTION.put("cn", Map.of());

            Map<String, Object> schema = new SampleModel().multilingualModelJsonSchema("cn", true);

            assertEquals(Boolean.FALSE, schema.get("additionalProperties"));
            assertInstanceOf(Map.class, schema.get("properties"));
        }
    }

    @Nested
    @DisplayName("Strict Schema Nested AdditionalProperties Tests")
    class TestStrictSchemaNestedAdditionalProperties {

        @Test
        @DisplayName("strict nested list model all objects additional properties false")
        void testStrictNestedListModelAllObjectsAdditionalPropertiesFalse() {
            MultilingualBaseModel.MULTILINGUAL_DESCRIPTION.put("cn", Map.of(
                    "{{[strict_inner_val]}}", "inner",
                    "{{[strict_inner_list]}}", "list"));

            Map<String, Object> schema = new StrictNestedRoot().multilingualModelJsonSchema("cn", true);
            List<String> bad = objectNodesWithWrongAdditionalProperties(schema);

            assertTrue(schema.containsKey("$defs"));
            assertEquals(List.of(), bad);
        }

        @Test
        @DisplayName("strict inline dict field free form unchanged")
        void testStrictInlineDictFieldFreeFormUnchanged() {
            MultilingualBaseModel.MULTILINGUAL_DESCRIPTION.put("cn", Map.of(
                    "{{[strict_summary]}}", "summary",
                    "{{[strict_attrs]}}", "attrs"));

            Map<String, Object> schema = new StrictWithDict().multilingualModelJsonSchema("cn", true);
            Map<?, ?> attributes = (Map<?, ?>) ((Map<?, ?>) schema.get("properties")).get("attributes");
            List<String> bad = objectNodesWithWrongAdditionalProperties(schema);

            assertEquals("object", attributes.get("type"));
            assertTrue(!(attributes.get("properties") instanceof Map<?, ?>));
            assertEquals(Boolean.TRUE, attributes.get("additionalProperties"));
            assertEquals(List.of(), bad);
        }

        @Test
        @DisplayName("strict outer model with nested dict model")
        void testStrictOuterModelWithNestedDictModelStructuredStrictDictFieldUnchanged() {
            MultilingualBaseModel.MULTILINGUAL_DESCRIPTION.put("cn", Map.of(
                    "{{[strict_summary]}}", "summary",
                    "{{[strict_attrs]}}", "attrs",
                    "{{[strict_outer_payload]}}", "payload"));

            Map<String, Object> schema = new StrictOuterWithNestedDict().multilingualModelJsonSchema("cn", true);
            Map<?, ?> defs = (Map<?, ?>) schema.get("$defs");
            Map<?, ?> inner = (Map<?, ?>) defs.get("StrictWithDict");
            Map<?, ?> attributes = (Map<?, ?>) ((Map<?, ?>) inner.get("properties")).get("attributes");
            Map<?, ?> payload = (Map<?, ?>) ((Map<?, ?>) schema.get("properties")).get("payload");
            List<String> bad = objectNodesWithWrongAdditionalProperties(schema);

            assertEquals("object", attributes.get("type"));
            assertTrue(!(attributes.get("properties") instanceof Map<?, ?>));
            assertEquals(Boolean.TRUE, attributes.get("additionalProperties"));
            assertEquals(Boolean.FALSE, inner.get("additionalProperties"));
            assertEquals("#/$defs/StrictWithDict", payload.get("$ref"));
            assertEquals(List.of(), bad);
        }

        @Test
        @DisplayName("strict entity extraction all objects additional properties false")
        void testStrictEntityExtractionAllObjectsAdditionalPropertiesFalse() {
            MultilingualBaseModel.MULTILINGUAL_DESCRIPTION.put("cn", Map.of(
                    "{{[ent_ext_list]}}", "entities",
                    "{{[ent_def_name]}}", "name",
                    "{{[ent_def_type]}}", "type id"));

            Map<String, Object> schema = new ExtractionModels.EntityExtraction()
                    .multilingualModelJsonSchema("cn", true);
            List<String> bad = objectNodesWithWrongAdditionalProperties(schema);

            assertEquals(List.of(), bad);
        }

        @Test
        @DisplayName("response format nested matches strict schema")
        void testResponseFormatNestedMatchesStrictSchema() {
            MultilingualBaseModel.MULTILINGUAL_DESCRIPTION.put("cn", Map.of());

            Map<String, Object> format = new StrictNestedRoot().responseFormat("cn");
            Map<?, ?> jsonSchema = (Map<?, ?>) format.get("json_schema");
            List<String> bad = objectNodesWithWrongAdditionalProperties(jsonSchema.get("schema"));

            assertEquals(List.of(), bad);
        }
    }

    @Nested
    @DisplayName("ReadableSchema Tests")
    class TestReadableSchema {

        @Test
        @DisplayName("readable schema returns tuple of str and dict")
        void testReadableSchemaReturnsTupleOfStrAndDict() {
            MultilingualBaseModel.MULTILINGUAL_DESCRIPTION.put("cn", Map.of(
                    "{{[test_name]}}", "Name",
                    "{{[test_count]}}", "Count"));

            MultilingualBaseModel.ReadableSchema readableSchema = new SampleModel().readableSchema("cn");

            assertTrue(readableSchema.outputSchema().contains("name"));
            assertTrue(readableSchema.outputSchema().contains("count"));
            assertTrue(readableSchema.refs().isEmpty());
        }

        @Test
        @DisplayName("readable schema with defs includes refs")
        void testReadableSchemaWithDefsIncludesRefs() {
            MultilingualBaseModel.MULTILINGUAL_DESCRIPTION.put("cn", Map.of(
                    "{{[ent_ext_list]}}", "entities",
                    "{{[ent_def_name]}}", "name",
                    "{{[ent_def_type]}}", "type"));

            MultilingualBaseModel.ReadableSchema readableSchema =
                    new ExtractionModels.EntityExtraction().readableSchema("cn");

            assertTrue(readableSchema.outputSchema().contains("extracted_entities"));
            assertTrue(readableSchema.refs().containsKey("EntityDeclaration"));
        }
    }

    @Nested
    @DisplayName("ResponseFormat Tests")
    class TestResponseFormat {

        @Test
        @DisplayName("response format has json schema type and name")
        void testResponseFormatHasJsonSchemaTypeAndName() {
            MultilingualBaseModel.MULTILINGUAL_DESCRIPTION.put("cn", Map.of());

            Map<String, Object> format = new SampleModel().responseFormat("cn");
            Map<?, ?> jsonSchema = (Map<?, ?>) format.get("json_schema");
            Map<?, ?> schema = (Map<?, ?>) jsonSchema.get("schema");

            assertEquals("json_schema", format.get("type"));
            assertEquals("SampleModel", jsonSchema.get("name"));
            assertEquals(Boolean.FALSE, jsonSchema.get("strict"));
            assertEquals(Boolean.FALSE, schema.get("additionalProperties"));
        }
    }

    @Nested
    @DisplayName("RecursiveReplace Tests")
    class TestRecursiveReplace {

        @Test
        @DisplayName("recursive replace replaces key in dict")
        void testRecursiveReplaceReplacesKeyInDict() {
            Map<String, Object> nested = new LinkedHashMap<>();
            nested.put("description", "b");
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("description", "a");
            data.put("nested", nested);

            MultilingualBaseModel.recursiveReplace(data, Map.of("a", "A", "b", "B"), "description", "description");

            assertEquals("A", data.get("description"));
            assertEquals("B", nested.get("description"));
        }

        @Test
        @DisplayName("recursive replace missing key unchanged")
        void testRecursiveReplaceMissingKeyUnchanged() {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("description", "unknown");

            MultilingualBaseModel.recursiveReplace(data, Map.of(), "description", "description");

            assertEquals("unknown", data.get("description"));
        }

        @Test
        @DisplayName("recursive replace lists traversed")
        void testRecursiveReplaceListsTraversed() {
            Map<String, Object> child = new LinkedHashMap<>();
            child.put("description", "x");
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("items", List.of(child));

            MultilingualBaseModel.recursiveReplace(data, Map.of("x", "X"), "description", "description");

            assertEquals("X", child.get("description"));
        }
    }

    @Nested
    @DisplayName("ToJsonTypes Tests")
    class TestToJsonTypes {

        @Test
        @DisplayName("simple type returns name")
        void testSimpleTypeReturnsName() {
            assertEquals("String", MultilingualBaseModel.toJsonTypes(String.class));
            assertEquals("Integer", MultilingualBaseModel.toJsonTypes(Integer.class));
        }

        @Test
        @DisplayName("list type returns origin and args")
        void testListTypeReturnsOriginAndArgs() throws NoSuchFieldException {
            Type type = GenericTypes.class.getDeclaredField("strings").getGenericType();

            String result = MultilingualBaseModel.toJsonTypes(type);

            assertTrue(result.contains("List"));
            assertTrue(result.contains("String"));
        }

        @Test
        @DisplayName("origin without args returns origin name")
        void testOriginWithoutArgsReturnsOriginName() {
            assertEquals("List", MultilingualBaseModel.toJsonTypes(List.class));
        }
    }
}
