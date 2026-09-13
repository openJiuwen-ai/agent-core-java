/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security.shellast;

/**
 * Pluggable shell command parser backend.
 *
 * <p>A tree-sitter based backend can be registered as a {@link java.util.ServiceLoader}
 * service in a future iteration; the default backend is the {@link ConservativeShellScanner},
 * which needs no external dependency.
 *
 * @since 0.1.15
 */
public interface ShellAstBackend {
    /**
     * Parse a shell command for permission checks.
     *
     * @param command raw command text
     * @return parse result with kind/subcommands/flags
     * @since 0.1.15
     */
    ShellAstParseResult parse(String command);
}
