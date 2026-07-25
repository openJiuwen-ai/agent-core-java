/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections.tools;

import java.util.List;
import java.util.Map;

/**
 * MCP resource metadata providers.
 *
 * @since 0.1.12
 */
final class McpMetadataProviders {
    private McpMetadataProviders() {
    }

    static final class ListMcpResourcesMetadataProvider implements ToolMetadataProvider {
        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
        public String getName() {
            return "list_mcp_resources";
        }

        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
        public String getDescription(String language) {
            return text(language,
                    "列出指定 MCP 服务器上可用的资源列表。",
                    "List available resources exposed by the specified MCP server.");
        }

        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
        public Map<String, Object> getInputParams(String language) {
            return ToolSchemaSupport.objectSchema(
                    ToolSchemaSupport.properties(new Object[] {
                            "server_id", ToolSchemaSupport.property("string", text(language,
                                    "MCP 服务器的 server_id", "The server_id of the MCP server"))
                    }),
                    List.of("server_id")
            );
        }
    }

    static final class ReadMcpResourceMetadataProvider implements ToolMetadataProvider {
        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
        public String getName() {
            return "read_mcp_resource";
        }

        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
        public String getDescription(String language) {
            return text(language,
                    "读取指定 MCP 服务器上某个资源的内容。",
                    "Read the content of a specific resource from the specified MCP server.");
        }

        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
        public Map<String, Object> getInputParams(String language) {
            return ToolSchemaSupport.objectSchema(
                    ToolSchemaSupport.properties(new Object[] {
                            "server_id", ToolSchemaSupport.property("string", text(language,
                                    "MCP 服务器的 server_id", "The server_id of the MCP server")),
                            "uri", ToolSchemaSupport.property("string", text(language,
                                    "要读取的资源 URI", "The URI of the resource to read"))
                    }),
                    List.of("server_id", "uri")
            );
        }
    }

    private static String text(String language, String cn, String en) {
        return ToolSchemaSupport.localized(language, cn, en);
    }
}
