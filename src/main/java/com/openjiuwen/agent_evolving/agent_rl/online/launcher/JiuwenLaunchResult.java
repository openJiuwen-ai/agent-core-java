/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.launcher;

/**
 * JiuwenClaw launch result.
 * <p>
 * Mirrors Python's tuple return of app + optional web process.
 */
public record JiuwenLaunchResult(LauncherProcess appProc, LauncherProcess webProc) {
}
