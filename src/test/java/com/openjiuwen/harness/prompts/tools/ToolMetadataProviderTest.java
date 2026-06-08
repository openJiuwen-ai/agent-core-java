/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.tools;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolMetadataProviderTest {

    @Test
    void validateAcceptsBilingualNestedSchemas() {
        ToolMetadataProvider provider = new DemoProvider(
                "demo",
                "中文描述",
                "english description",
                schema("名称", "name", nestedSchema("子项")),
                schema("name", "name", nestedSchema("child"))
        );

        assertThatCode(provider::validate).doesNotThrowAnyException();
    }

    @Test
    void validateRejectsMissingDescriptions() {
        ToolMetadataProvider provider = new DemoProvider(
                "demo",
                " ",
                "english description",
                schema("名称", "name", Map.of()),
                schema("name", "name", Map.of())
        );

        assertThatThrownBy(provider::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("description is empty");
    }

    @Test
    void validateRejectsEmptyEnglishDescription() {
        ToolMetadataProvider provider = new DemoProvider(
                "demo",
                "中文描述",
                " ",
                schema("名称", "name", Map.of()),
                schema("name", "name", Map.of())
        );

        assertThatThrownBy(provider::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("en description is empty");
    }

    @Test
    void validateRejectsNonObjectSchema() {
        ToolMetadataProvider provider = new DemoProvider(
                "demo",
                "中文描述",
                "english description",
                Map.of(
                        "type", "array",
                        "properties", Map.of(),
                        "required", List.of()
                ),
                Map.of(
                        "type", "object",
                        "properties", Map.of(),
                        "required", List.of()
                )
        );

        assertThatThrownBy(provider::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cn schema type");
    }

    @Test
    void validateRejectsDivergentPropertySets() {
        ToolMetadataProvider provider = new DemoProvider(
                "demo",
                "中文描述",
                "english description",
                Map.of(
                        "type", "object",
                        "properties", Map.of("a", property("名称")),
                        "required", List.of("a")
                ),
                Map.of(
                        "type", "object",
                        "properties", Map.of("b", property("name")),
                        "required", List.of("b")
                )
        );

        assertThatThrownBy(provider::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("property keys differ");
    }

    @Test
    void validateRejectsMissingPropertyDescription() {
        ToolMetadataProvider provider = new DemoProvider(
                "demo",
                "中文描述",
                "english description",
                Map.of(
                        "type", "object",
                        "properties", Map.of("a", Map.of("type", "string")),
                        "required", List.of()
                ),
                Map.of(
                        "type", "object",
                        "properties", Map.of("a", property("name")),
                        "required", List.of()
                )
        );

        assertThatThrownBy(provider::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing description");
    }

    @Test
    void validateRejectsNestedObjectPropertyDifferences() {
        ToolMetadataProvider provider = new DemoProvider(
                "demo",
                "中文描述",
                "english description",
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "sub", Map.of(
                                        "type", "object",
                                        "description", "子对象",
                                        "properties", Map.of("x", property("字段")),
                                        "required", List.of()
                                )
                        ),
                        "required", List.of()
                ),
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "sub", Map.of(
                                        "type", "object",
                                        "description", "sub obj",
                                        "properties", Map.of("y", property("field")),
                                        "required", List.of()
                                )
                        ),
                        "required", List.of()
                )
        );

        assertThatThrownBy(provider::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("property keys differ");
    }

    private static Map<String, Object> schema(String rootDescription, String nestedDescription, Map<String, Object> arrayItems) {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "title", property(rootDescription),
                        "items", Map.of(
                                "type", "array",
                                "description", nestedDescription,
                                "items", arrayItems
                        )
                ),
                "required", List.of("title", "items")
        );
    }

    private static Map<String, Object> nestedSchema(String description) {
        return Map.of(
                "type", "object",
                "properties", Map.of("child", property(description)),
                "required", List.of("child")
        );
    }

    private static Map<String, Object> property(String description) {
        return Map.of(
                "type", "string",
                "description", description
        );
    }

    private record DemoProvider(
            String name,
            String cnDescription,
            String enDescription,
            Map<String, Object> cnSchema,
            Map<String, Object> enSchema) implements ToolMetadataProvider {

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getDescription(String language) {
            return "en".equals(language) ? enDescription : cnDescription;
        }

        @Override
        public Map<String, Object> getInputParams(String language) {
            return "en".equals(language) ? enSchema : cnSchema;
        }
    }
}
