/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security.shellast;

import java.util.Iterator;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

/**
 * Entry point for shell command parsing used by the tiered permission policy.
 *
 * <p>Mirrors Python {@code openjiuwen.harness.security.shell_ast.parse_shell_for_permission}.
 * A tree-sitter backend can be supplied via the {@link ShellAstBackend} service loader in a
 * future iteration; the default fallback is {@link ConservativeShellScanner}.
 *
 * @since 0.1.15
 */
public final class ShellAst {

    private ShellAst() {
    }

    /**
     * Parse a shell command for permission checks.
     *
     * @param command raw command text
     * @return parse result
     * @since 0.1.15
     */
    public static ShellAstParseResult parse(String command) {
        return loadBackend().parse(command);
    }

    private static ShellAstBackend loadBackend() {
        try {
            Iterator<ShellAstBackend> backends = ServiceLoader.load(ShellAstBackend.class).iterator();
            if (backends.hasNext()) {
                return backends.next();
            }
        } catch (ServiceConfigurationError t) {
            // Fall through to the conservative scanner.
        }
        return ConservativeShellScanner.INSTANCE;
    }
}
