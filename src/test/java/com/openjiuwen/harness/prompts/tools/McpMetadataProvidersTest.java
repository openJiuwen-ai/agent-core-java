/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.tools;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class McpMetadataProvidersTest {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<String> castList(Object value) {
        return (List<String>) value;
    }

    @Test
    void listMcpResourcesMetadataProviderMatchesPythonContract() {
        ToolMetadataProvider provider = new ListMcpResourcesMetadataProvider();
        Map<String, Object> schema = provider.getInputParams("cn");

        assertThat(provider.getName()).isEqualTo("list_mcp_resources");
        assertThat(provider.getDescription("cn")).isEqualTo("列出指定 MCP 服务器上可用的资源列表。");
        assertThat(provider.getDescription("en")).isEqualTo("List available resources exposed by the specified MCP server.");
        assertThat(schema.get("type")).isEqualTo("object");
        assertThat(castMap(schema.get("properties")).keySet()).containsExactly("server_id");
        assertThat(castList(schema.get("required"))).containsExactly("server_id");
        assertThatCode(provider::validate).doesNotThrowAnyException();
    }

    @Test
    void readMcpResourceMetadataProviderMatchesPythonContract() {
        ToolMetadataProvider provider = new ReadMcpResourceMetadataProvider();
        Map<String, Object> schema = provider.getInputParams("cn");

        assertThat(provider.getName()).isEqualTo("read_mcp_resource");
        assertThat(provider.getDescription("cn")).isEqualTo("读取指定 MCP 服务器上某个资源的内容。");
        assertThat(provider.getDescription("en")).isEqualTo("Read the content of a specific resource from the specified MCP server.");
        assertThat(schema.get("type")).isEqualTo("object");
        assertThat(castMap(schema.get("properties")).keySet()).containsExactlyInAnyOrderElementsOf(
                java.util.List.of("server_id", "uri"));
        assertThat(castList(schema.get("required"))).containsExactlyElementsOf(java.util.List.of("server_id", "uri"));
        assertThatCode(provider::validate).doesNotThrowAnyException();
    }
}
