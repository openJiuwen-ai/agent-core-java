/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.launcher;

/**
 * Minimal training config for launcher helpers.
 * <p>
 * Mirrors the deterministic fields used from Python's TrainingConfig.
 */
public record TrainingConfig(String gpuIds, int threshold, int scanInterval, String ppoConfig, String loraRepo) {
}
