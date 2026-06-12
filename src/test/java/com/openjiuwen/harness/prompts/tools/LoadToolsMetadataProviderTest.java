/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.tools;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class LoadToolsMetadataProviderTest {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<String> castList(Object value) {
        return (List<String>) value;
    }

    @Test
    void loadToolsMetadataProviderMatchesPythonContract() {
        ToolMetadataProvider provider = new LoadToolsMetadataProvider();
        Map<String, Object> schema = provider.getInputParams("cn");

        assertThat(provider.getName()).isEqualTo("load_tools");
        assertThat(provider.getDescription("cn")).isEqualTo("将选定的真实工具加载到当前 session 可见工具集合中。");
        assertThat(provider.getDescription("en")).isEqualTo("Load selected real tools into the current session-visible tool set.");
        assertThat(schema.get("type")).isEqualTo("object");
        assertThat(castMap(schema.get("properties")).keySet()).containsExactlyInAnyOrderElementsOf(
                java.util.List.of("tool_names", "replace"));
        assertThat(castList(schema.get("required"))).containsExactlyElementsOf(java.util.List.of("tool_names"));
        assertThatCode(provider::validate).doesNotThrowAnyException();
    }
}
