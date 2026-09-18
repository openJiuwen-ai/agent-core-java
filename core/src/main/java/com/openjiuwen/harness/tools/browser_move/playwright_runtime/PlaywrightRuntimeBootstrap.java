/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.browser_move.playwright_runtime;

import java.nio.file.Path;
import java.util.List;

/**
 * Playwright runtime package bootstrap helpers.
 *
 * <p>Mirrors Python's
 * {@code openjiuwen/harness/tools/browser_move/playwright_runtime/__init__.py}.
 */
public final class PlaywrightRuntimeBootstrap {

    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "REPO_ROOT",
            "SRC_ROOT",
            "build_browser_runtime_mcp_config",
            "browser_tools",
            "controller",
            "register_browser_runtime_mcp_server",
            "restart_local_browser_runtime_server",
            "service",
            "stop_local_browser_runtime_server"
    );

    private PlaywrightRuntimeBootstrap() {
    }

    public static Path resolveRepoRoot() {
        return Path.of("").toAbsolutePath().normalize();
    }

    public static Path resolveSourceRoot() {
        return resolveRepoRoot().resolve("openjiuwen").resolve("harness").resolve("tools").resolve("browser_move");
    }
}
