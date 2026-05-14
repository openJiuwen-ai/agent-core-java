/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.launcher;

import java.util.List;

/**
 * Launch runtime resolution result.
 * <p>
 * Mirrors Python's {@code LaunchRuntime} in
 * {@code openjiuwen.agent_evolving.agent_rl.online.launcher.services}.
 */
public record LaunchRuntime(
        String inferenceUrl,
        String judgeUrl,
        String gatewayBaseUrl,
        String gatewayApiUrl,
        String loraRepo,
        boolean skipVllm,
        boolean skipJudge,
        boolean reuseInferenceForJudge,
        String judgeLabel,
        List<PortCheck> portsToCheck
) {
}
