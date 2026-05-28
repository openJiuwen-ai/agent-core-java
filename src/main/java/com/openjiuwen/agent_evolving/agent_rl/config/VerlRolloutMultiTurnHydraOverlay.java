/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.config;

/**
 * Verl Rollout Multi-turn Hydra overlay configuration.
 * <p>
 * Mirrors Python's {@code VerlRolloutMultiTurnHydraOverlay} in
 * {@code openjiuwen.agent_evolving.agent_rl.config.offline_config}.
 */
public class VerlRolloutMultiTurnHydraOverlay {

    private String format = "hermes";

    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }
}