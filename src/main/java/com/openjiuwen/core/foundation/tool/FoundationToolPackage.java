/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Package facade for foundation-tool exports.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.foundation.tool} package facade in
 * {@code openjiuwen/core/foundation/tool/__init__.py}.</p>
 */
public final class FoundationToolPackage {

    public static final String PYTHON_MODULE = "openjiuwen/core/foundation/tool/__init__.py";

    public static final List<String> BASIC_SYMBOLS = List.of(
            "Input",
            "Output",
            "tool"
    );

    public static final List<String> TOOL_SYMBOLS = List.of(
            "Tool",
            "LocalFunction",
            "RestfulApi",
            "MCPTool"
    );

    public static final List<String> TOOL_INFO_SYMBOLS = List.of(
            "ToolCard",
            "RestfulApiCard",
            "ToolInfo"
    );

    public static final List<String> MCP_TOOL_SYMBOLS = List.of(
            "McpToolCard",
            "McpServerConfig"
    );

    public static final List<String> MCP_CLIENT_SYMBOLS = List.of(
            "McpClient",
            "SseClient",
            "StdioClient",
            "OpenApiClient",
            "PlaywrightClient",
            "StreamableHttpClient"
    );

    public static final List<String> FORM_HANDLER_SYMBOLS = List.of(
            "FormHandler",
            "FormHandlerManager"
    );

    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "Input",
            "Output",
            "tool",
            "Tool",
            "LocalFunction",
            "RestfulApi",
            "MCPTool",
            "ToolCard",
            "RestfulApiCard",
            "ToolInfo",
            "McpToolCard",
            "McpServerConfig",
            "McpClient",
            "SseClient",
            "StdioClient",
            "OpenApiClient",
            "PlaywrightClient",
            "StreamableHttpClient",
            "FormHandler",
            "FormHandlerManager"
    );

    public static final Map<String, List<String>> EXPORT_GROUPS = buildExportGroups();
    public static final Map<String, String> EXPORT_SOURCES = buildExportSources();

    private FoundationToolPackage() {
    }

    /**
     * Mirrors Python's {@code __all__}.
     *
     * @return exported symbol names in Python order
     */
    public static List<String> all() {
        return EXPORTED_SYMBOLS;
    }

    /**
     * Checks whether a symbol is re-exported by the Python package facade.
     *
     * @param symbolName symbol name
     * @return {@code true} when the symbol is part of Python {@code __all__}
     */
    public static boolean exports(String symbolName) {
        return EXPORTED_SYMBOLS.contains(symbolName);
    }

    /**
     * Returns the Python source object imported by the package facade.
     *
     * @param symbolName symbol name
     * @return dotted Python source object, or {@code null} when absent
     */
    public static String sourceFor(String symbolName) {
        return EXPORT_SOURCES.get(symbolName);
    }

    private static Map<String, List<String>> buildExportGroups() {
        Map<String, List<String>> groups = new LinkedHashMap<>();
        groups.put("basic", BASIC_SYMBOLS);
        groups.put("tools", TOOL_SYMBOLS);
        groups.put("toolInfo", TOOL_INFO_SYMBOLS);
        groups.put("mcpTool", MCP_TOOL_SYMBOLS);
        groups.put("mcpClient", MCP_CLIENT_SYMBOLS);
        groups.put("formHandler", FORM_HANDLER_SYMBOLS);
        return Collections.unmodifiableMap(groups);
    }

    private static Map<String, String> buildExportSources() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("Input", "openjiuwen.core.foundation.tool.base.Input");
        sources.put("Output", "openjiuwen.core.foundation.tool.base.Output");
        sources.put("tool", "openjiuwen.core.foundation.tool.tool.tool");
        sources.put("Tool", "openjiuwen.core.foundation.tool.base.Tool");
        sources.put("LocalFunction", "openjiuwen.core.foundation.tool.function.function.LocalFunction");
        sources.put("RestfulApi", "openjiuwen.core.foundation.tool.service_api.restful_api.RestfulApi");
        sources.put("MCPTool", "openjiuwen.core.foundation.tool.mcp.base.MCPTool");
        sources.put("ToolCard", "openjiuwen.core.foundation.tool.base.ToolCard");
        sources.put("RestfulApiCard", "openjiuwen.core.foundation.tool.service_api.restful_api.RestfulApiCard");
        sources.put("ToolInfo", "openjiuwen.core.foundation.tool.schema.ToolInfo");
        sources.put("McpToolCard", "openjiuwen.core.foundation.tool.mcp.base.McpToolCard");
        sources.put("McpServerConfig", "openjiuwen.core.foundation.tool.mcp.base.McpServerConfig");
        sources.put("McpClient", "openjiuwen.core.foundation.tool.mcp.client.mcp_client.McpClient");
        sources.put("SseClient", "openjiuwen.core.foundation.tool.mcp.client.sse_client.SseClient");
        sources.put("StdioClient", "openjiuwen.core.foundation.tool.mcp.client.stdio_client.StdioClient");
        sources.put("OpenApiClient", "openjiuwen.core.foundation.tool.mcp.client.openapi_client.OpenApiClient");
        sources.put("PlaywrightClient", "openjiuwen.core.foundation.tool.mcp.client.playwright_client.PlaywrightClient");
        sources.put("StreamableHttpClient",
                "openjiuwen.core.foundation.tool.mcp.client.streamable_http_client.StreamableHttpClient");
        sources.put("FormHandler", "openjiuwen.core.foundation.tool.form_handler.form_handler_manager.FormHandler");
        sources.put("FormHandlerManager",
                "openjiuwen.core.foundation.tool.form_handler.form_handler_manager.FormHandlerManager");
        return Collections.unmodifiableMap(sources);
    }
}
