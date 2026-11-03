/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.tools;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class SearchToolsMetadataProviderTest {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<String> castList(Object value) {
        return (List<String>) value;
    }

    @Test
    void searchToolsMetadataProviderMatchesPythonContract() {
        ToolMetadataProvider provider = new SearchToolsMetadataProvider();
        Map<String, Object> schema = provider.getInputParams("cn");

        assertThat(provider.getName()).isEqualTo("search_tools");
        assertThat(provider.getDescription("cn")).isEqualTo("根据能力、名称、描述或参数提示搜索候选工具。仅用于发现，不会直接调用工具。");
        assertThat(provider.getDescription("en"))
                .isEqualTo("Search candidate tools by capability, name, description, "
                        + "or parameter hints. Discovery only; tools are not directly callable.");
        assertThat(schema.get("type")).isEqualTo("object");
        assertThat(castMap(schema.get("properties")).keySet())
                .containsExactly("query", "limit", "detail_level");
        assertThat(castList(schema.get("required"))).containsExactly("query");
        assertThatCode(provider::validate).doesNotThrowAnyException();
    }
}
