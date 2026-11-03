package com.openjiuwen.core.foundation.tool.utils;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaExtractorTest {

    @Test
    void callableSchemaUsesTypeExtractorAndHumanizedTitles() throws NoSuchMethodException {
        Method method = SampleTool.class.getDeclaredMethod("submitPlan", String.class, Optional.class, List.class);

        Map<String, Object> schema = CallableSchemaExtractor.generateSchema(method);
        Map<String, Object> properties = castMap(schema.get("properties"));

        assertThat(schema.get("title")).isEqualTo("submit plan");
        assertThat(schema.get("additionalProperties")).isEqualTo(false);
        assertThat(castMap(properties.get("taskId")).get("type")).isEqualTo("string");
        assertThat(castMap(properties.get("planId")).get("nullable")).isEqualTo(true);
        assertThat(castMap(properties.get("tags")).get("type")).isEqualTo("array");
    }

    @Test
    void typeExtractorHandlesOptionalAndEnums() {
        Map<String, Object> optionalSchema = TypeSchemaExtractor.extract(new TypeRef<Optional<Integer>>() { }.type());
        assertThat(optionalSchema.get("type")).isEqualTo("integer");
        assertThat(optionalSchema.get("nullable")).isEqualTo(true);

        Map<String, Object> enumSchema = TypeSchemaExtractor.extract(SampleStatus.class);
        assertThat(enumSchema.get("type")).isEqualTo("string");
        assertThat((List<String>) enumSchema.get("enum")).containsExactly("READY", "BUSY");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    private enum SampleStatus {
        READY,
        BUSY
    }

    private static final class SampleTool {
        @SuppressWarnings("unused")
        void submitPlan(String taskId, Optional<Integer> planId, List<String> tags) {
        }
    }

    private abstract static class TypeRef<T> {
        java.lang.reflect.Type type() {
            return ((java.lang.reflect.ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0];
        }
    }
}
