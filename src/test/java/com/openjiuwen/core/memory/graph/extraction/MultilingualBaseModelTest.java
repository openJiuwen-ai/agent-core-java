/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.extraction;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code test_base} module in
 * {@code tests/unit_tests/core/memory/graph/extraction/test_base.py}.
 */
class MultilingualBaseModelTest {

    private Map<String, Map<String, String>> previousDescriptions;

    @BeforeEach
    void snapshotDescriptions() {
        previousDescriptions = new LinkedHashMap<>();
        MultilingualBaseModel.MULTILINGUAL_DESCRIPTION.forEach(
                (language, descriptions) -> previousDescriptions.put(language, new LinkedHashMap<>(descriptions)));
    }

    @AfterEach
    void restoreDescriptions() {
        MultilingualBaseModel.MULTILINGUAL_DESCRIPTION.clear();
        previousDescriptions.forEach(
                (language, descriptions) -> MultilingualBaseModel.MULTILINGUAL_DESCRIPTION.put(
                        language, new LinkedHashMap<>(descriptions)));
    }

    private static Map<String, Object> property(String type, String description) {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("type", type);
        property.put("description", description);
        return property;
    }

    private static Map<String, Object> objectSchema(String title, Map<String, Object> properties) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("title", title);
        schema.put("type", "object");
        schema.put("properties", properties);
        return schema;
    }

    private static final class _SampleModel extends MultilingualBaseModel {
        @Override
        public Map<String, Object> responseFormat() {
            return objectSchema("_SampleModel", Map.of(
                    "name", property("string", "{{[test_name]}}"),
                    "count", property("integer", "{{[test_count]}}")));
        }
    }

    private static final class StrictInner extends MultilingualBaseModel {
        @Override
        public Map<String, Object> responseFormat() {
            return objectSchema("StrictInner", Map.of(
                    "value", property("string", "{{[strict_inner_val]}}")));
        }
    }

    private static final class StrictNestedRoot extends MultilingualBaseModel {
        @Override
        public Map<String, Object> responseFormat() {
            Map<String, Object> itemRef = new LinkedHashMap<>();
            itemRef.put("$ref", "#/$defs/StrictInner");
            Map<String, Object> items = property("array", "{{[strict_inner_list]}}");
            items.put("items", itemRef);

            Map<String, Object> schema = objectSchema("StrictNestedRoot", Map.of("items", items));
            schema.put("$defs", Map.of("StrictInner", new StrictInner().responseFormat()));
            return schema;
        }
    }

    private static final class StrictWithDict extends MultilingualBaseModel {
        @Override
        public Map<String, Object> responseFormat() {
            Map<String, Object> attributes = property("object", "{{[strict_attrs]}}");
            attributes.put("additionalProperties", true);
            return objectSchema("StrictWithDict", Map.of(
                    "summary", property("string", "{{[strict_summary]}}"),
                    "attributes", attributes));
        }
    }

    private static final class StrictOuterWithNestedDict extends MultilingualBaseModel {
        @Override
        public Map<String, Object> responseFormat() {
            Map<String, Object> payload = property("object", "{{[strict_outer_payload]}}");
            payload.put("$ref", "#/$defs/StrictWithDict");

            Map<String, Object> schema = objectSchema("StrictOuterWithNestedDict", Map.of("payload", payload));
            schema.put("$defs", Map.of("StrictWithDict", new StrictWithDict().responseFormat()));
            return schema;
        }
    }

    private static final class GenericTypes {
        private List<String> strings;
    }

    @Test
    void schemaReturnsDictWithProperties() {
        putDescriptions("cn", Map.of());

        Map<String, Object> schema = new _SampleModel().multilingualModelJsonSchema("cn", false);
        Map<?, ?> properties = (Map<?, ?>) schema.get("properties");

        assertThat(properties.containsKey("name")).isTrue();
        assertThat(properties.containsKey("count")).isTrue();
        assertThat((Object) ((Map<?, ?>) properties.get("name")).get("type")).isEqualTo("string");
        assertThat((Object) ((Map<?, ?>) properties.get("count")).get("type")).isEqualTo("integer");
    }

    @Test
    void schemaReplacesDescriptionsFromLookup() {
        putDescriptions("cn", Map.of("{{[test_name]}}", "\u540d\u79f0", "{{[test_count]}}", "\u6570\u91cf"));

        Map<String, Object> schema = new _SampleModel().multilingualModelJsonSchema("cn", false);
        Map<?, ?> properties = (Map<?, ?>) schema.get("properties");

        assertThat((Object) ((Map<?, ?>) properties.get("name")).get("description")).isEqualTo("\u540d\u79f0");
        assertThat((Object) ((Map<?, ?>) properties.get("count")).get("description")).isEqualTo("\u6570\u91cf");
    }

    @Test
    void schemaStrictSetsAdditionalPropertiesFalse() {
        MultilingualBaseModel.MULTILINGUAL_DESCRIPTION.put(
                "cn",
                Map.of("{{[name]}}", "Name", "{{[count]}}", "Count"));

        Map<String, Object> schema = new _SampleModel().multilingualModelJsonSchema("cn", true);
        Map<?, ?> properties = (Map<?, ?>) schema.get("properties");

        assertThat(properties).isInstanceOf(Map.class);
        assertThat(schema.get("additionalProperties")).isEqualTo(Boolean.FALSE);
    }

    @Test
    void strictNestedListModelAllObjectsAdditionalPropertiesFalse() {
        putDescriptions("cn", Map.of(
                "{{[strict_inner_val]}}", "inner",
                "{{[strict_inner_list]}}", "list"));

        Map<String, Object> schema = new StrictNestedRoot().multilingualModelJsonSchema("cn", true);

        assertThat(schema).containsKey("$defs");
        assertThat(objectNodesWithWrongAdditionalProperties(schema)).isEmpty();
    }

    @Test
    void strictInlineDictFieldFreeFormUnchanged() {
        putDescriptions("cn", Map.of(
                "{{[strict_summary]}}", "summary",
                "{{[strict_attrs]}}", "attrs"));

        Map<String, Object> schema = new StrictWithDict().multilingualModelJsonSchema("cn", true);
        Map<?, ?> attrs = (Map<?, ?>) ((Map<?, ?>) schema.get("properties")).get("attributes");

        assertThat((Object) attrs.get("type")).isEqualTo("object");
        assertThat(attrs.containsKey("properties")).isFalse();
        assertThat((Object) attrs.get("additionalProperties")).isEqualTo(Boolean.TRUE);
        assertThat(objectNodesWithWrongAdditionalProperties(schema)).isEmpty();
    }

    @Test
    void strictOuterModelWithNestedDictKeepsFreeFormDictField() {
        putDescriptions("cn", Map.of(
                "{{[strict_summary]}}", "summary",
                "{{[strict_attrs]}}", "attrs",
                "{{[strict_outer_payload]}}", "payload"));

        Map<String, Object> schema = new StrictOuterWithNestedDict().multilingualModelJsonSchema("cn", true);
        Map<?, ?> defs = (Map<?, ?>) schema.get("$defs");
        Map<?, ?> inner = (Map<?, ?>) defs.get("StrictWithDict");
        Map<?, ?> attrs = (Map<?, ?>) ((Map<?, ?>) inner.get("properties")).get("attributes");

        assertThat((Object) attrs.get("type")).isEqualTo("object");
        assertThat(attrs.containsKey("properties")).isFalse();
        assertThat((Object) attrs.get("additionalProperties")).isEqualTo(Boolean.TRUE);
        assertThat((Object) inner.get("additionalProperties")).isEqualTo(Boolean.FALSE);
        assertThat((Object) ((Map<?, ?>) ((Map<?, ?>) schema.get("properties")).get("payload")).get("$ref"))
                .isEqualTo("#/$defs/StrictWithDict");
        assertThat(objectNodesWithWrongAdditionalProperties(schema)).isEmpty();
    }

    @Test
    void strictEntityExtractionAllObjectsAdditionalPropertiesFalse() {
        putDescriptions("cn", Map.of(
                "{{[ent_ext_list]}}", "entities",
                "{{[ent_def_name]}}", "name",
                "{{[ent_def_type]}}", "type id"));

        Map<String, Object> schema = new ExtractionModels.EntityExtraction().multilingualModelJsonSchema("cn", true);

        assertThat(objectNodesWithWrongAdditionalProperties(schema)).isEmpty();
    }

    @Test
    void responseFormatNestedMatchesStrictSchema() {
        putDescriptions("cn", Map.of());

        Map<String, Object> format = new StrictNestedRoot().responseFormat("cn");
        Map<?, ?> jsonSchema = (Map<?, ?>) format.get("json_schema");
        Map<?, ?> inner = (Map<?, ?>) jsonSchema.get("schema");

        assertThat(objectNodesWithWrongAdditionalProperties(inner)).isEmpty();
    }

    @Test
    void readableSchemaReturnsStringAndRefs() {
        putDescriptions("cn", Map.of("{{[test_name]}}", "\u540d\u79f0", "{{[test_count]}}", "\u6570\u91cf"));

        MultilingualBaseModel.ReadableSchema readableSchema = new _SampleModel().readableSchema("cn");

        assertThat(readableSchema.outputSchema()).contains("name").contains("count");
        assertThat(readableSchema.refs()).isEmpty();
    }

    @Test
    void readableSchemaWithDefinitionsIncludesRefs() {
        putDescriptions("cn", Map.of(
                "{{[ent_ext_list]}}", "entities",
                "{{[ent_def_name]}}", "name",
                "{{[ent_def_type]}}", "type"));

        MultilingualBaseModel.ReadableSchema readableSchema = new ExtractionModels.EntityExtraction().readableSchema("cn");

        assertThat(readableSchema.outputSchema()).contains("extracted_entities");
        assertThat(readableSchema.refs()).containsKey("EntityDeclaration");
    }

    @Test
    void responseFormatHasJsonSchemaTypeAndName() {
        putDescriptions("cn", Map.of());

        Map<String, Object> format = new _SampleModel().responseFormat("cn");
        Map<?, ?> jsonSchema = (Map<?, ?>) format.get("json_schema");
        Map<?, ?> schema = (Map<?, ?>) jsonSchema.get("schema");

        assertThat(format).containsEntry("type", "json_schema");
        assertThat((Object) jsonSchema.get("name")).isEqualTo("_SampleModel");
        assertThat((Object) jsonSchema.get("strict")).isEqualTo(Boolean.FALSE);
        assertThat((Object) schema.get("additionalProperties")).isEqualTo(Boolean.FALSE);
    }

    @Test
    void recursiveReplaceReplacesKeyInDict() {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("description", "b");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("description", "a");
        data.put("nested", nested);

        boolean replaced = MultilingualBaseModel.recursiveReplace(
                data, Map.of("a", "A", "b", "B"), "description", "description");

        assertThat(replaced).isTrue();
        assertThat(data).containsEntry("description", "A");
        assertThat(nested).containsEntry("description", "B");
    }

    @Test
    void recursiveReplaceMissingLookupValueLeavesOriginalValue() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("description", "unknown");

        boolean replaced = MultilingualBaseModel.recursiveReplace(data, Map.of(), "description", "description");

        assertThat(replaced).isTrue();
        assertThat(data).containsEntry("description", "unknown");
    }

    @Test
    void recursiveReplaceTraversesLists() {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("description", "x");
        List<Map<String, Object>> data = List.of(item);

        boolean replaced = MultilingualBaseModel.recursiveReplace(data, Map.of("x", "X"), "description", "description");

        assertThat(replaced).isTrue();
        assertThat(item).containsEntry("description", "X");
    }

    @Test
    void simpleTypeReturnsPythonStyleName() {
        assertThat(MultilingualBaseModel.toJsonTypes(String.class)).isEqualTo("str");
        assertThat(MultilingualBaseModel.toJsonTypes(Integer.TYPE)).isEqualTo("int");
    }

    @Test
    void listTypeReturnsOriginAndArgs() throws NoSuchFieldException {
        Type type = GenericTypes.class.getDeclaredField("strings").getGenericType();

        assertThat(MultilingualBaseModel.toJsonTypes(type)).isEqualTo("list[str]");
    }

    @Test
    void originWithoutArgsReturnsOriginName() {
        assertThat(MultilingualBaseModel.toJsonTypes(List.class)).isEqualTo("list");
    }

    private static void putDescriptions(String language, Map<String, String> descriptions) {
        MultilingualBaseModel.MULTILINGUAL_DESCRIPTION.put(language, new LinkedHashMap<>(descriptions));
    }

    private static List<String> objectNodesWithWrongAdditionalProperties(Object schema) {
        List<String> bad = new ArrayList<>();
        collectWrongAdditionalProperties(schema, "$", bad);
        return bad;
    }

    @SuppressWarnings("unchecked")
    private static void collectWrongAdditionalProperties(Object schema, String path, List<String> bad) {
        if (schema instanceof Map<?, ?> map) {
            Object type = map.get("type");
            Object properties = map.get("properties");
            if ("object".equals(type) && properties instanceof Map<?, ?>
                    && !Boolean.FALSE.equals(map.get("additionalProperties"))) {
                bad.add(path + ": additionalProperties=" + map.get("additionalProperties"));
            }
            ((Map<Object, Object>) map).forEach(
                    (key, value) -> collectWrongAdditionalProperties(value, path + "." + key, bad));
        } else if (schema instanceof List<?> list) {
            for (int index = 0; index < list.size(); index++) {
                collectWrongAdditionalProperties(list.get(index), path + "[" + index + "]", bad);
            }
        }
    }
}
