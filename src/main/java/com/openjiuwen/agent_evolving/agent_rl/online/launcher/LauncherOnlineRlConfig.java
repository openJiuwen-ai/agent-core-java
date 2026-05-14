/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.launcher;

/**
 * Minimal launcher runtime config aggregate.
 * <p>
 * Mirrors the deterministic subset of Python's online launcher config objects.
 */
public record LauncherOnlineRlConfig(
        boolean demo,
        VllmServiceConfig inference,
        JudgeServiceConfig judge,
        GatewayServiceConfig gateway,
        TrajectoryConfig trajectory,
        TrainingConfig training,
        JiuwenConfig jiuwen
) {
}
