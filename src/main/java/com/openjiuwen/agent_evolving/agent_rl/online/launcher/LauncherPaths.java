/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.launcher;

import java.nio.file.Path;

/**
 * Launcher workspace paths.
 * <p>
 * Mirrors Python's {@code LauncherPaths} in
 * {@code openjiuwen.agent_evolving.agent_rl.online.launcher.runner}.
 */
public record LauncherPaths(
        Path agentCoreRoot,
        Path jiuwenclawRepo,
        Path workspaceRoot,
        Path workspaceEnv,
        Path scriptDir
) {
}
