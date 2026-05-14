/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.judge;

/**
 * Lightweight judge scoring client seam.
 * <p>
 * Mirrors the judge-scoring call surface consumed by online gateway components.
 */
public interface JudgeGatewayClient {

    ScoreResponse score(ScoreRequest request);
}
