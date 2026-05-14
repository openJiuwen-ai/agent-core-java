/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.launcher;

/**
 * Minimal trajectory config for launcher helpers.
 * <p>
 * Mirrors the deterministic fields used from Python's TrajectoryConfig.
 */
public record TrajectoryConfig(int batchSize, String mode) {
}
