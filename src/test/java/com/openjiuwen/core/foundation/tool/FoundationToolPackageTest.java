/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's package facade in
 * {@code openjiuwen/core/foundation/tool/__init__.py}.
 */
class FoundationToolPackageTest {

    private static final List<String> PYTHON_ALL = List.of(
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

    @Test
    void exposesExactPythonModulePathAndAllOrder() {
        assertEquals("openjiuwen/core/foundation/tool/__init__.py", FoundationToolPackage.PYTHON_MODULE);
        assertEquals(PYTHON_ALL, FoundationToolPackage.EXPORTED_SYMBOLS);
        assertEquals(PYTHON_ALL, FoundationToolPackage.all());
    }

    @Test
    void exportsReportsKnownSymbolsOnly() {
        assertTrue(FoundationToolPackage.exports("Tool"));
        assertTrue(FoundationToolPackage.exports("StreamableHttpClient"));
        assertFalse(FoundationToolPackage.exports("MissingTool"));
    }

    @Test
    void groupsPreservePythonCommentSections() {
        assertEquals(List.of("Input", "Output", "tool"), FoundationToolPackage.EXPORT_GROUPS.get("basic"));
        assertEquals(List.of("Tool", "LocalFunction", "RestfulApi", "MCPTool"),
                FoundationToolPackage.EXPORT_GROUPS.get("tools"));
        assertEquals(List.of("McpClient", "SseClient", "StdioClient", "OpenApiClient", "PlaywrightClient",
                "StreamableHttpClient"), FoundationToolPackage.EXPORT_GROUPS.get("mcpClient"));
        assertEquals(List.of("FormHandler", "FormHandlerManager"),
                FoundationToolPackage.EXPORT_GROUPS.get("formHandler"));
    }

    @Test
    void sourceMapPreservesPythonReExportOrigins() {
        assertEquals("openjiuwen.core.foundation.tool.base.Tool", FoundationToolPackage.sourceFor("Tool"));
        assertEquals("openjiuwen.core.foundation.tool.mcp.client.mcp_client.McpClient",
                FoundationToolPackage.sourceFor("McpClient"));
        assertEquals("openjiuwen.core.foundation.tool.form_handler.form_handler_manager.FormHandlerManager",
                FoundationToolPackage.sourceFor("FormHandlerManager"));
    }

    @Test
    void exportedCollectionsAreImmutable() {
        assertThrows(UnsupportedOperationException.class,
                () -> FoundationToolPackage.EXPORTED_SYMBOLS.add("Unexpected"));
        assertThrows(UnsupportedOperationException.class,
                () -> FoundationToolPackage.EXPORT_GROUPS.put("extra", List.of()));
        assertSame(FoundationToolPackage.EXPORTED_SYMBOLS, FoundationToolPackage.all());
    }
}
