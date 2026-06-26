/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.tools;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class BashMetadataProviderTest {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<String> castList(Object value) {
        return (List<String>) value;
    }

    @Test
    void bashMetadataProviderMatchesPythonContract() {
        ToolMetadataProvider provider = new BashMetadataProvider();
        Map<String, Object> schema = provider.getInputParams("en");
        Map<String, Object> properties = castMap(schema.get("properties"));

        assertThat(provider.getName()).isEqualTo("bash");
        assertThat(provider.getDescription("cn")).startsWith("鎵ц Shell 鍛戒护骞惰繑鍥炶緭鍑恒€?");
        assertThat(provider.getDescription("en")).startsWith("Executes a given bash command and returns its output.");
        assertThat(properties.keySet()).containsExactly(
                "command",
                "timeout",
                "description",
                "run_in_background",
                "workdir",
                "max_output_chars",
                "shell_type"
        );
        assertThat(castList(schema.get("required"))).containsExactly("command");
        assertThat(castMap(properties.get("command"))).containsEntry("type", "string");
        assertThat(castMap(properties.get("timeout"))).containsEntry("type", "integer");
        assertThat(castMap(properties.get("run_in_background"))).containsEntry("type", "boolean");
        assertThat(castMap(properties.get("shell_type"))).containsEntry("type", "string");
        assertThat(castList(castMap(properties.get("shell_type")).get("enum")))
                .containsExactly("auto", "cmd", "powershell", "bash", "sh");
    }

    @Test
    void validatePassesForBashProvider() {
        ToolMetadataProvider provider = new BashMetadataProvider();
        assertThatCode(provider::validate).doesNotThrowAnyException();
    }
}
