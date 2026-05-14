/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.lsp.servers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Root resolution helpers for Java harness LSP builtin servers.
 *
 * <p>Mirrors Python's {@code nearest_root(...)} helper in
 * {@code openjiuwen.harness.lsp.servers.registry}.
 */
public final class LspRootResolvers {

    private LspRootResolvers() {
    }

    public static LspRootResolver nearestRoot(List<String> includePatterns,
                                              List<String> excludePatterns,
                                              String stopDir) {
        return filePath -> {
            try {
                Path startDir = Paths.get(filePath).toAbsolutePath().normalize().getParent();
                if (startDir == null) {
                    return null;
                }
                Path stop = stopDir == null || stopDir.isBlank()
                        ? Paths.get("").toAbsolutePath().normalize()
                        : Paths.get(stopDir).toAbsolutePath().normalize();

                for (String pattern : includePatterns) {
                    if (Files.exists(startDir.resolve(pattern))) {
                        return startDir.toString();
                    }
                }

                Path current = startDir.getParent();
                while (current != null) {
                    for (String pattern : includePatterns) {
                        if (Files.exists(current.resolve(pattern))) {
                            return current.toString();
                        }
                    }
                    if (current.equals(stop)) {
                        return null;
                    }
                    if (excludePatterns != null) {
                        for (String pattern : excludePatterns) {
                            if (Files.exists(current.resolve(pattern))) {
                                return null;
                            }
                        }
                    }
                    current = current.getParent();
                }
                return null;
            } catch (Exception ignored) {
                return null;
            }
        };
    }
}
