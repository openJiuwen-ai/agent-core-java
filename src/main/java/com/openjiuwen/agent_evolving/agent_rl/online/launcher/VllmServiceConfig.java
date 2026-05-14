/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.launcher;

/**
 * Minimal vLLM service config for launcher helpers.
 * <p>
 * Mirrors the deterministic fields used from Python's VLLMServiceConfig.
 */
public record VllmServiceConfig(
        String modelPath,
        String modelName,
        String host,
        int port,
        String gpuIds,
        int tp,
        String existingUrl,
        double healthTimeout
) {
}
