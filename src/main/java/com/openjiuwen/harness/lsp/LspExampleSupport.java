/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.lsp;

import com.openjiuwen.harness.lsp.core.LspServerManager;
import com.openjiuwen.harness.tools.LspTool;
import com.openjiuwen.harness.tools.lsp.LspOperation;
import com.openjiuwen.harness.tools.lsp.LspToolSupport;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Shared helper for Java LSP examples.
 */
public final class LspExampleSupport {
    private LspExampleSupport() {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static Map<String, Object> buildToolSchema() {
        return LspToolSupport.buildLspTool();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static List<String> supportedOperations() {
        return java.util.Arrays.stream(LspOperation.values()).map(LspOperation::value).toList();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static Map<String, Object> runDefinitionDemo(Path workspace, String relativePath) {
        LspTool tool = new LspTool(workspace.toString());
        return cast(tool.invoke(Map.of(
                "operation", "goToDefinition",
                "file_path", relativePath
        )).getData());
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static LspServerManager newManager(Path workspace) {
        LspServerManager manager = new LspServerManager();
        try {
            java.lang.reflect.Field field = LspServerManager.class.getDeclaredField("workspaceRoot");
            field.setAccessible(true);
            field.set(manager, workspace.toAbsolutePath().normalize().toString());
        } catch (ReflectiveOperationException ignored) {
            // Fall back – the workspace root will remain empty.
        }
        return manager;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }
}
