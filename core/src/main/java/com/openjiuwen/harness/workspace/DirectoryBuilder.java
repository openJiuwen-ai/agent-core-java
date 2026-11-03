/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.workspace;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Builds workspace directory metadata into files and marker directories.
 *
 * <p>Mirrors Python's {@code DirectoryBuilder} in
 * {@code openjiuwen/harness/workspace/directory_builder.py}.</p>
 */
public class DirectoryBuilder {

    private static final Pattern WINDOWS_DRIVE = Pattern.compile("^[A-Za-z]:.*");

    private final Path rootPath;

    public DirectoryBuilder() {
        this("");
    }

    public DirectoryBuilder(String rootPath) {
        this.rootPath = rootPath == null || rootPath.isBlank() ? null : Path.of(rootPath);
    }

    public void build(List<Map<String, Object>> directories) throws IOException {
        if (directories == null) {
            return;
        }
        for (Map<String, Object> node : directories) {
            createDirectoryRecursive(node, "");
        }
    }

    public static boolean isSafePath(String path) {
        if (path == null || path.isEmpty()) {
            return true;
        }
        if (Path.of(path).isAbsolute()) {
            return false;
        }
        if (path.startsWith("/") || path.startsWith("\\")) {
            return false;
        }
        if (WINDOWS_DRIVE.matcher(path).matches() || path.startsWith("\\\\")) {
            return false;
        }
        Path normalized = Path.of(path.replace('\\', '/')).normalize();
        for (Path part : normalized) {
            if ("..".equals(part.toString())) {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private void createDirectoryRecursive(Map<String, Object> node, String parentPath) throws IOException {
        if (node == null) {
            return;
        }
        String relativePath = stringValue(node.getOrDefault("path", ""));
        if (!isSafePath(relativePath)) {
            throw new IllegalArgumentException("Unsafe path detected: " + relativePath);
        }

        Path fullPath = resolveFullPath(parentPath, relativePath);
        boolean isFile = Boolean.TRUE.equals(node.get("is_file"));
        String defaultContent = stringValue(node.getOrDefault("default_content", ""));
        if (isFile) {
            if (!Files.exists(fullPath)) {
                Path parent = fullPath.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.writeString(fullPath, defaultContent, StandardCharsets.UTF_8);
            }
        } else {
            Path markerFile = fullPath.resolve(".workspace");
            Files.createDirectories(markerFile.getParent());
            if (!Files.exists(markerFile)) {
                Files.writeString(markerFile, "", StandardCharsets.UTF_8);
            }
        }

        Object children = node.get("children");
        if (children instanceof List<?> list) {
            for (Object child : list) {
                if (child instanceof Map<?, ?> childMap) {
                    createDirectoryRecursive((Map<String, Object>) childMap, fullPath.toString());
                }
            }
        }
    }

    private Path resolveFullPath(String parentPath, String relativePath) {
        if (parentPath != null && !parentPath.isBlank()) {
            return Path.of(parentPath).resolve(relativePath).normalize();
        }
        if (rootPath != null) {
            return rootPath.resolve(relativePath).normalize();
        }
        return Path.of(relativePath).normalize();
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
