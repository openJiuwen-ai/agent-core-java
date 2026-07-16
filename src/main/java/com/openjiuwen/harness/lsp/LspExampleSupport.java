/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.lsp;

import com.openjiuwen.harness.lsp.core.LSPServerManager;
import com.openjiuwen.harness.tools.LspTool;
import com.openjiuwen.harness.tools.lsp.LspOperation;
import com.openjiuwen.harness.tools.lsp.LspToolSupport;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Shared helper for Java LSP examples.
 * 
 * @since 0.1.7
 */
public final class LspExampleSupport {
    /**
     * LspExampleSupport.
     * 
     * @since 0.1.7
     */
    private LspExampleSupport() {
    }

    /**
     * buildToolSchema.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static Map<String, Object> buildToolSchema() {
        return LspToolSupport.buildLspTool();
    }

    /**
     * supportedOperations.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static List<String> supportedOperations() {
        return java.util.Arrays.stream(LspOperation.values()).map(LspOperation::value).toList();
    }

    /**
     * runDefinitionDemo.
     * 
     * @param workspace workspace
     * @param relativePath relativePath
     * @return the result
     * @since 0.1.7
     */
    public static Map<String, Object> runDefinitionDemo(Path workspace, String relativePath) {
        LspTool tool = new LspTool(workspace.toString());
        return cast(tool.invoke(Map.of("operation", "goToDefinition", "file_path", relativePath)).getData());
    }

    /**
     * newManager.
     * 
     * @param workspace workspace
     * @return the result
     * @since 0.1.7
     */
    public static LSPServerManager newManager(Path workspace) {
        LSPServerManager manager = new LSPServerManager();
        manager.setWorkspaceRoot(workspace.toAbsolutePath().normalize().toString());
        return manager;
    }

    @SuppressWarnings("unchecked")
    /**
     * cast.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private static Map<String, Object> cast(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }
}
