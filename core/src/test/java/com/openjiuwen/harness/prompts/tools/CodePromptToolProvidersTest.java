/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.tools;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class CodePromptToolProvidersTest {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<String> castList(Object value) {
        return (List<String>) value;
    }

    @Test
    void codeMetadataProviderMatchesPythonContract() {
        ToolMetadataProvider provider = new CodePromptToolProviders.CodeMetadataProvider();
        Map<String, Object> schema = provider.getInputParams("cn");

        assertThat(provider.getName()).isEqualTo("code");
        assertThat(provider.getDescription("cn")).isEqualTo("执行代码（Python 或 JavaScript）。");
        assertThat(provider.getDescription("en")).isEqualTo("Execute code (Python or JavaScript).");
        assertThat(schema.get("type")).isEqualTo("object");
        assertThat(castMap(schema.get("properties")).keySet()).containsExactlyInAnyOrderElementsOf(java.util.List.of("code", "language", "timeout"));
        assertThat(castList(schema.get("required"))).containsExactlyElementsOf(java.util.List.of("code"));
        assertThatCode(provider::validate).doesNotThrowAnyException();
    }

}
