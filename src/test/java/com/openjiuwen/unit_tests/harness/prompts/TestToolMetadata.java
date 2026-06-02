/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.harness.prompts;

import com.openjiuwen.harness.prompts.tools.ToolDescriptionRegistry;
import com.openjiuwen.harness.prompts.tools.ToolMetadataProvider;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Mirrors Python's {@code tests/unit_tests/harness/prompts/test_tool_metadata.py}.
 */
class TestToolMetadata {

    @Test
    void testValidProviderPasses() {
        ToolMetadataProvider.validateProvider(new ValidProvider());
    }

    @Test
    void testEmptyCnDescriptionRaises() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                ToolMetadataProvider.validateProvider(new ToolMetadataProvider() {
                    @Override
                    public String getName() {
                        return "bad_cn";
                    }

                    @Override
                    public String getDescription(String language) {
                        return "cn".equals(language) ? "" : "ok";
                    }

                    @Override
                    public Map<String, Object> getInputParams(String language) {
                        return Map.of("type", "object", "properties", Map.of(), "required", List.of());
                    }
                })
        );
        assertThat(error).hasMessageContaining("cn description is empty");
    }

    @Test
    void testEmptyEnDescriptionRaises() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                ToolMetadataProvider.validateProvider(new ToolMetadataProvider() {
                    @Override
                    public String getName() {
                        return "bad_en";
                    }

                    @Override
                    public String getDescription(String language) {
                        return "en".equals(language) ? " " : "好";
                    }

                    @Override
                    public Map<String, Object> getInputParams(String language) {
                        return Map.of("type", "object", "properties", Map.of(), "required", List.of());
                    }
                })
        );
        assertThat(error).hasMessageContaining("en description is empty");
    }

    @Test
    void testSchemaTypeNotObjectRaises() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                ToolMetadataProvider.validateProvider(new ToolMetadataProvider() {
                    @Override
                    public String getName() {
                        return "bad_schema";
                    }

                    @Override
                    public String getDescription(String language) {
                        return "ok";
                    }

                    @Override
                    public Map<String, Object> getInputParams(String language) {
                        return "cn".equals(language)
                                ? Map.of("type", "array", "properties", Map.of(), "required", List.of())
                                : Map.of("type", "object", "properties", Map.of(), "required", List.of());
                    }
                })
        );
        assertThat(error).hasMessageContaining("cn schema type");
    }

    @Test
    void testPropertyKeysDifferRaises() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                ToolMetadataProvider.validateProvider(new ToolMetadataProvider() {
                    @Override
                    public String getName() {
                        return "bad_keys";
                    }

                    @Override
                    public String getDescription(String language) {
                        return "ok";
                    }

                    @Override
                    public Map<String, Object> getInputParams(String language) {
                        return "cn".equals(language)
                                ? Map.of("type", "object", "properties", Map.of("a", Map.of("type", "string", "description", "x")), "required", List.of())
                                : Map.of("type", "object", "properties", Map.of("b", Map.of("type", "string", "description", "y")), "required", List.of());
                    }
                })
        );
        assertThat(error).hasMessageContaining("property keys differ");
    }

    @Test
    void testMissingDescriptionInPropertyRaises() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                ToolMetadataProvider.validateProvider(new ToolMetadataProvider() {
                    @Override
                    public String getName() {
                        return "bad_desc";
                    }

                    @Override
                    public String getDescription(String language) {
                        return "ok";
                    }

                    @Override
                    public Map<String, Object> getInputParams(String language) {
                        return "cn".equals(language)
                                ? Map.of("type", "object", "properties", Map.of("a", Map.of("type", "string")), "required", List.of())
                                : Map.of("type", "object", "properties", Map.of("a", Map.of("type", "string", "description", "y")), "required", List.of());
                    }
                })
        );
        assertThat(error).hasMessageContaining("missing description");
    }

    @Test
    void testNestedObjectValidated() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                ToolMetadataProvider.validateProvider(new ToolMetadataProvider() {
                    @Override
                    public String getName() {
                        return "bad_nested";
                    }

                    @Override
                    public String getDescription(String language) {
                        return "ok";
                    }

                    @Override
                    public Map<String, Object> getInputParams(String language) {
                        Map<String, Object> nestedCn = Map.of(
                                "type", "object",
                                "properties", Map.of("x", Map.of("type", "string", "description", "x")),
                                "required", List.of()
                        );
                        Map<String, Object> nestedEn = Map.of(
                                "type", "object",
                                "properties", Map.of("y", Map.of("type", "string", "description", "y")),
                                "required", List.of()
                        );
                        return "cn".equals(language)
                                ? Map.of("type", "object", "properties", Map.of("sub", merge(nestedCn, "description", "子对象")), "required", List.of())
                                : Map.of("type", "object", "properties", Map.of("sub", merge(nestedEn, "description", "sub obj")), "required", List.of());
                    }
                })
        );
        assertThat(error).hasMessageContaining("property keys differ");
    }

    @Test
    void testAllBuiltinProvidersPass() {
        ToolDescriptionRegistry.validateAllToolProviders();
    }

    @Test
    void testReturnsCorrectCard() {
        Map<String, Object> card = ToolDescriptionRegistry.buildToolCard("bash", "BashTool", "cn", null);
        assertThat(String.valueOf(card.get("id"))).startsWith("BashTool_");
        assertThat(card.get("name")).isEqualTo("bash");
        assertThat(card.get("description")).isEqualTo(ToolDescriptionRegistry.getToolDescription("bash", "cn"));
        assertThat(card.get("input_params")).isEqualTo(ToolDescriptionRegistry.getToolInputParams("bash", "cn"));
    }

    @Test
    void testReturnsCorrectPowershellCard() {
        Map<String, Object> card = ToolDescriptionRegistry.buildToolCard("powershell", "PowerShellTool", "en", null);
        assertThat(String.valueOf(card.get("id"))).startsWith("PowerShellTool_");
        assertThat(card.get("name")).isEqualTo("powershell");
        assertThat(card.get("description")).isEqualTo(ToolDescriptionRegistry.getToolDescription("powershell", "en"));
        assertThat(card.get("input_params")).isEqualTo(ToolDescriptionRegistry.getToolInputParams("powershell", "en"));
    }

    @Test
    void testWithAgentId() {
        Map<String, Object> card = ToolDescriptionRegistry.buildToolCard("bash", "BashTool", "cn", "test_agent_123");
        assertThat(card.get("id")).isEqualTo("BashTool_test_agent_123");
        assertThat(card.get("name")).isEqualTo("bash");
    }

    @Test
    void testEnLanguage() {
        Map<String, Object> card = ToolDescriptionRegistry.buildToolCard("code", "CodeTool", "en", null);
        assertThat(card.get("description")).isEqualTo(ToolDescriptionRegistry.getToolDescription("code", "en"));
        assertThat(card.get("input_params")).isEqualTo(ToolDescriptionRegistry.getToolInputParams("code", "en"));
    }

    @Test
    void testUnknownToolRaises() {
        assertThrows(
                ToolDescriptionRegistry.KeyError.class,
                () -> ToolDescriptionRegistry.buildToolCard("nonexistent", "X", "cn", null)
        );
    }

    @Test
    void testRegisterValidProvider() {
        ToolDescriptionRegistry.registerToolProvider(new ValidProvider());
        assertThat(ToolDescriptionRegistry.getRegisteredNames()).contains("test_valid");
    }

    @Test
    void testRegisterInvalidProviderRaises() {
        assertThrows(IllegalArgumentException.class, () ->
                ToolDescriptionRegistry.registerToolProvider(new ToolMetadataProvider() {
                    @Override
                    public String getName() {
                        return "bad_reg";
                    }

                    @Override
                    public String getDescription(String language) {
                        return "cn".equals(language) ? "" : "ok";
                    }

                    @Override
                    public Map<String, Object> getInputParams(String language) {
                        return Map.of("type", "object", "properties", Map.of(), "required", List.of());
                    }
                })
        );
        assertThat(ToolDescriptionRegistry.getRegisteredNames()).doesNotContain("bad_reg");
    }

    @Test
    void testGetToolDescriptionUnknownRaises() {
        ToolDescriptionRegistry.KeyError error = assertThrows(
                ToolDescriptionRegistry.KeyError.class,
                () -> ToolDescriptionRegistry.getToolDescription("no_such_tool", "cn")
        );
        assertThat(error).hasMessageContaining("not registered");
    }

    @Test
    void testGetToolInputParamsUnknownRaises() {
        ToolDescriptionRegistry.KeyError error = assertThrows(
                ToolDescriptionRegistry.KeyError.class,
                () -> ToolDescriptionRegistry.getToolInputParams("no_such_tool", "cn")
        );
        assertThat(error).hasMessageContaining("not registered");
    }

    private static Map<String, Object> merge(Map<String, Object> base, String key, Object value) {
        java.util.LinkedHashMap<String, Object> merged = new java.util.LinkedHashMap<>(base);
        merged.put(key, value);
        return merged;
    }

    private static final class ValidProvider implements ToolMetadataProvider {
        @Override
        public String getName() {
            return "test_valid";
        }

        @Override
        public String getDescription(String language) {
            return "en".equals(language) ? "Test tool" : "测试工具";
        }

        @Override
        public Map<String, Object> getInputParams(String language) {
            return Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "a", Map.of(
                                    "type", "string",
                                    "description", "en".equals(language) ? "Param A" : "参数A"
                            )
                    ),
                    "required", List.of("a")
            );
        }
    }
}
