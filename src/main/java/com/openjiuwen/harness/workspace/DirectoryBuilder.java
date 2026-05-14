/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.workspace;

import com.openjiuwen.core.sysop.BaseFsOperation;
import com.openjiuwen.core.sysop.SysOperation;

import java.util.List;
import java.util.Map;

/**
 * Minimal directory builder for harness workspace scaffolding.
 *
 * <p>Mirrors Python's {@code DirectoryBuilder} in
 * {@code openjiuwen.harness.workspace.directory_builder}.
 *
 * <p>Creates directories and seeded files through {@link SysOperation} rather
 * than direct filesystem calls.
 */
public class DirectoryBuilder {

    private final SysOperation sysOperation;
    private final String rootPath;

    public DirectoryBuilder(SysOperation sysOperation, String rootPath) {
        this.sysOperation = sysOperation;
        this.rootPath = rootPath == null ? "" : rootPath;
    }

    public void build(List<Map<String, Object>> directories) {
        if (directories == null) {
            return;
        }
        for (Map<String, Object> node : directories) {
            createRecursively(node, "");
        }
    }

    @SuppressWarnings("unchecked")
    private void createRecursively(Map<String, Object> node, String parentPath) {
        String relativePath = asString(node.get("path"));
        validateSafePath(relativePath);
        String fullPath = join(parentPath.isBlank() ? rootPath : parentPath, relativePath);
        boolean isFile = Boolean.TRUE.equals(node.get("is_file"));
        String defaultContent = asString(node.get("default_content"));

        BaseFsOperation fs = sysOperation.fs();
        if (isFile) {
            fs.writeFile(fullPath, defaultContent, "text", false, false,
                    true, null, "UTF-8", Map.of());
        } else {
            fs.writeFile(join(fullPath, ".workspace"), "", "text", false, false,
                    true, null, "UTF-8", Map.of());
        }

        Object childrenObj = node.get("children");
        if (childrenObj instanceof List<?> children) {
            for (Object child : children) {
                if (child instanceof Map<?, ?> childMap) {
                    createRecursively((Map<String, Object>) childMap, fullPath);
                }
            }
        }
    }

    private static String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String join(String left, String right) {
        if (left == null || left.isBlank()) {
            return right == null ? "" : right;
        }
        if (right == null || right.isBlank()) {
            return left;
        }
        return left.endsWith("/") ? left + right : left + "/" + right;
    }

    private static void validateSafePath(String path) {
        if (path == null || path.isBlank()) {
            return;
        }
        String normalized = path.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.startsWith("../") || normalized.contains("/../")
                || normalized.matches("^[A-Za-z]:.*") || normalized.startsWith("//")) {
            throw new IllegalArgumentException("Unsafe path detected: " + path);
        }
    }
}
