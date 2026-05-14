/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.lsp.core.utils;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Git ignore filtering helpers for LSP navigation results.
 *
 * <p>Mirrors Python's {@code git_ignore.py} in {@code openjiuwen.harness.lsp.core.utils}.
 */
public final class GitIgnoreFilter {

    private static final List<String> IGNORED_SEGMENTS = List.of(
            ".git", "node_modules", "__pycache__", "target", "build", "dist", "out", ".idea", ".vscode"
    );

    private GitIgnoreFilter() {
    }

    public static boolean isIgnored(String pathText) {
        if (pathText == null || pathText.isBlank()) {
            return false;
        }
        Path path = Paths.get(pathText).normalize();
        for (Path part : path) {
            if (IGNORED_SEGMENTS.contains(part.toString())) {
                return true;
            }
        }
        return false;
    }

    public static <T> List<T> filter(List<T> items, java.util.function.Function<T, String> pathExtractor) {
        if (items == null || items.isEmpty() || pathExtractor == null) {
            return items == null ? List.of() : items;
        }
        List<T> filtered = new ArrayList<>();
        for (T item : items) {
            String path = pathExtractor.apply(item);
            if (!isIgnored(path)) {
                filtered.add(item);
            }
        }
        return filtered;
    }
}
