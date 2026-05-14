/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.rail;

/**
 * Minimal uploader seam for RL online rail.
 * <p>
 * Mirrors Python's asynchronous uploader contract used by {@code RLOnlineRail}.
 */
public interface TrajectoryUploader {

    void enqueue(OnlineRlBatch batch);
}
