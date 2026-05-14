/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.launcher;

/**
 * Minimal scheduler handle for launcher shutdown orchestration.
 * <p>
 * Mirrors the subset of Python scheduler behavior consumed by launcher shutdown.
 */
public interface OnlineTrainingSchedulerHandle {

    void stop();
}
