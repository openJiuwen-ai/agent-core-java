/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.extraction;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MultilingualBaseModelTest {

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

    private static final class SampleModel extends MultilingualBaseModel {
        @Override
        public Map<String, Object> responseFormat() {
            return objectSchema("SampleModel", Map.of(
                    "name", property("string", "{{[name]}}"),
                    "count", property("integer", "{{[count]}}")));
        }
    }

    private static final class GenericTypes {
        private List<String> strings;
    }

    @Test
    void schemaReplacesDescriptionsAndAppliesStrictMode() {
        MultilingualBaseModel.MULTILINGUAL_DESCRIPTION.put(
                "cn",
                Map.of("{{[name]}}", "Name", "{{[count]}}", "Count"));

        Map<String, Object> schema = new SampleModel().multilingualModelJsonSchema("cn", true);
        Map<?, ?> properties = (Map<?, ?>) schema.get("properties");

        assertThat(((Map<?, ?>) properties.get("name")).get("description")).isEqualTo("Name");
        assertThat(((Map<?, ?>) properties.get("count")).get("description")).isEqualTo("Count");
        assertThat(schema.get("additionalProperties")).isEqualTo(Boolean.FALSE);
        assertThat(schema.get("required")).asList().containsExactlyInAnyOrder("name", "count");
    }

    @Test
    void readableSchemaAndResponseFormatExposeExpectedStructure() {
        MultilingualBaseModel.MULTILINGUAL_DESCRIPTION.put(
                "cn",
                Map.of("{{[name]}}", "Name", "{{[count]}}", "Count"));

        MultilingualBaseModel.ReadableSchema readableSchema = new SampleModel().readableSchema("cn");
        Map<String, Object> responseFormat = new SampleModel().responseFormat("cn");

        assertThat(readableSchema.outputSchema()).contains("name: string").contains("count: integer");
        assertThat(readableSchema.refs()).isEmpty();
        assertThat(responseFormat).containsEntry("type", "json_schema");
        assertThat(((Map<?, ?>) responseFormat.get("json_schema")).get("name")).isEqualTo("SampleModel");
    }

    @Test
    void toJsonTypesPreservesGenericTypeNames() throws NoSuchFieldException {
        Type type = GenericTypes.class.getDeclaredField("strings").getGenericType();

        assertThat(MultilingualBaseModel.toJsonTypes(type)).contains("List").contains("String");
    }
}
