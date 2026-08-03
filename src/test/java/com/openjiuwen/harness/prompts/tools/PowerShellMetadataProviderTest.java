/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.tools;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class PowerShellMetadataProviderTest {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<String> castList(Object value) {
        return (List<String>) value;
    }

    @Test
    void powerShellMetadataProviderMatchesPythonContract() {
        ToolMetadataProvider provider = new PowerShellMetadataProvider();
        Map<String, Object> schema = provider.getInputParams("cn");

        assertThat(provider.getName()).isEqualTo("powershell");
        assertThat(provider.getDescription("cn")).startsWith("执行给定的 PowerShell 命令并返回输出。工作目录会在命令之间保持不变；");
        assertThat(provider.getDescription("en")).startsWith("Execute a given PowerShell command and return its output.");
        assertThat(schema.get("type")).isEqualTo("object");
        assertThat(castMap(schema.get("properties")).keySet()).containsExactlyInAnyOrderElementsOf(
                java.util.List.of("command", "timeout", "workdir", "background", "max_output_chars", "description"));
        assertThat(castList(schema.get("required"))).containsExactly("command");
        assertThatCode(provider::validate).doesNotThrowAnyException();
    }
}
