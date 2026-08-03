/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.tools;

import com.openjiuwen.core.foundation.tool.ToolCard;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's {@code TestValidateProvider}, {@code TestValidateAllProviders},
 * {@code TestBuildToolCard}, {@code TestRegisterToolProvider}, and {@code TestFailFast} in
 * {@code tests/unit_tests/harness/prompts/test_tool_metadata.py}.
 */
class ToolMetadataRegistryTest {

    @Test
    void validProviderPassesValidation() {
        assertThatCode(() -> ToolMetadataProvider.validateProvider(new ValidProvider()))
                .doesNotThrowAnyException();
    }

    @Test
    void emptyCnDescriptionRaises() {
        ToolMetadataProvider provider = new ValidProvider() {
            @Override
            public String getDescription(String language) {
                return "en".equals(language) ? "ok" : "";
            }
        };

        assertThatThrownBy(() -> ToolMetadataProvider.validateProvider(provider))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cn description is empty");
    }

    @Test
    void emptyEnDescriptionRaises() {
        ToolMetadataProvider provider = new ValidProvider() {
            @Override
            public String getDescription(String language) {
                return "en".equals(language) ? "  " : "好";
            }
        };

        assertThatThrownBy(() -> ToolMetadataProvider.validateProvider(provider))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("en description is empty");
    }

    @Test
    void schemaTypeNotObjectRaises() {
        ToolMetadataProvider provider = new ValidProvider() {
            @Override
            public Map<String, Object> getInputParams(String language) {
                if ("cn".equals(language)) {
                    return schema("array", Map.of(), List.of());
                }
                return schema("object", Map.of(), List.of());
            }
        };

        assertThatThrownBy(() -> ToolMetadataProvider.validateProvider(provider))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cn schema type");
    }

    @Test
    void propertyKeysDifferRaises() {
        ToolMetadataProvider provider = new ValidProvider() {
            @Override
            public Map<String, Object> getInputParams(String language) {
                if ("cn".equals(language)) {
                    return schema("object", Map.of("a", property("string", "x")), List.of());
                }
                return schema("object", Map.of("b", property("string", "y")), List.of());
            }
        };

        assertThatThrownBy(() -> ToolMetadataProvider.validateProvider(provider))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("property keys differ");
    }

    @Test
    void missingDescriptionInPropertyRaises() {
        ToolMetadataProvider provider = new ValidProvider() {
            @Override
            public Map<String, Object> getInputParams(String language) {
                if ("cn".equals(language)) {
                    return schema("object", Map.of("a", Map.of("type", "string")), List.of());
                }
                return schema("object", Map.of("a", property("string", "y")), List.of());
            }
        };

        assertThatThrownBy(() -> ToolMetadataProvider.validateProvider(provider))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing description");
    }

    @Test
    void nestedObjectPropertyKeysAreValidated() {
        ToolMetadataProvider provider = new ValidProvider() {
            @Override
            public Map<String, Object> getInputParams(String language) {
                Map<String, Object> nestedCn = schema("object", Map.of("x", property("string", "x")), List.of());
                Map<String, Object> nestedEn = schema("object", Map.of("y", property("string", "y")), List.of());
                if ("cn".equals(language)) {
                    return schema("object", Map.of("sub", withDescription(nestedCn, "子对象")), List.of());
                }
                return schema("object", Map.of("sub", withDescription(nestedEn, "sub obj")), List.of());
            }
        };

        assertThatThrownBy(() -> ToolMetadataProvider.validateProvider(provider))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("property keys differ");
    }

    @Test
    void allBuiltinProvidersPassValidation() {
        assertThatCode(HarnessPromptToolsPackage::validateAllToolProviders)
                .doesNotThrowAnyException();
    }

    @Test
    void buildToolCardReturnsCorrectCard() {
        ToolCard card = HarnessPromptToolsPackage.buildToolCard("bash", "BashTool", "cn");

        assertThat(card.getId()).startsWith("BashTool_");
        assertThat(card.getName()).isEqualTo("bash");
        assertThat(card.getDescription()).isEqualTo(HarnessPromptToolsPackage.getToolDescription("bash", "cn"));
        assertThat(card.getInputParams()).isEqualTo(HarnessPromptToolsPackage.getToolInputParams("bash", "cn"));
    }

    @Test
    void buildToolCardReturnsCorrectPowerShellCard() {
        ToolCard card = HarnessPromptToolsPackage.buildToolCard("powershell", "PowerShellTool", "en");

        assertThat(card.getId()).startsWith("PowerShellTool_");
        assertThat(card.getName()).isEqualTo("powershell");
        assertThat(card.getDescription()).isEqualTo(HarnessPromptToolsPackage.getToolDescription("powershell", "en"));
        assertThat(card.getInputParams()).isEqualTo(HarnessPromptToolsPackage.getToolInputParams("powershell", "en"));
    }

    @Test
    void buildToolCardUsesAgentIdWhenProvided() {
        ToolCard card = HarnessPromptToolsPackage.buildToolCard("bash", "BashTool", "cn", "test_agent_123");

        assertThat(card.getId()).isEqualTo("BashTool_test_agent_123");
        assertThat(card.getName()).isEqualTo("bash");
    }

    @Test
    void buildToolCardSupportsEnglishLanguage() {
        ToolCard card = HarnessPromptToolsPackage.buildToolCard("code", "CodeTool", "en");

        assertThat(card.getDescription()).isEqualTo(HarnessPromptToolsPackage.getToolDescription("code", "en"));
        assertThat(card.getInputParams()).isEqualTo(HarnessPromptToolsPackage.getToolInputParams("code", "en"));
    }

    @Test
    void buildToolCardUnknownToolRaises() {
        assertThatThrownBy(() -> HarnessPromptToolsPackage.buildToolCard("nonexistent", "X", "cn"))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void registerValidProviderAddsItToRegistry() {
        ToolMetadataProvider provider = new ValidProvider();

        HarnessPromptToolsPackage.registerToolProvider(provider);

        assertThat(HarnessPromptToolsPackage.getToolDescription("test_valid", "cn")).isEqualTo("测试工具");
    }

    @Test
    void registerInvalidProviderRaisesAndDoesNotRegisterIt() {
        ToolMetadataProvider provider = new ValidProvider() {
            @Override
            public String getName() {
                return "bad_reg";
            }

            @Override
            public String getDescription(String language) {
                return "en".equals(language) ? "ok" : "";
            }
        };

        assertThatThrownBy(() -> HarnessPromptToolsPackage.registerToolProvider(provider))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HarnessPromptToolsPackage.getToolDescription("bad_reg", "cn"))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void getToolDescriptionUnknownRaises() {
        assertThatThrownBy(() -> HarnessPromptToolsPackage.getToolDescription("no_such_tool", "cn"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("not registered");
    }

    @Test
    void getToolInputParamsUnknownRaises() {
        assertThatThrownBy(() -> HarnessPromptToolsPackage.getToolInputParams("no_such_tool", "cn"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("not registered");
    }

    private static Map<String, Object> schema(String type, Map<String, Object> properties, List<String> required) {
        return Map.of(
                "type", type,
                "properties", properties,
                "required", required);
    }

    private static Map<String, Object> property(String type, String description) {
        return Map.of(
                "type", type,
                "description", description);
    }

    private static Map<String, Object> withDescription(Map<String, Object> source, String description) {
        java.util.LinkedHashMap<String, Object> result = new java.util.LinkedHashMap<>(source);
        result.put("description", description);
        return result;
    }

    private static class ValidProvider implements ToolMetadataProvider {
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
            String description = "en".equals(language) ? "Param A" : "参数A";
            return schema("object", Map.of("a", property("string", description)), List.of("a"));
        }
    }
}
