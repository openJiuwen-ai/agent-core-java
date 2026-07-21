/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.agent.coordination;

/**
 * Periodic-poll control surface owned by the coordination event bus.
 *
 * <p>Mirrors Python {@code PollController} protocol
 * ({@code agent/coordination/dispatcher.py:114-131}). Handlers reach into this
 * protocol directly instead of bouncing the pause/resume call through the host;
 * the bus already knows how to suspend its mailbox/task poll tasks, no
 * TeamAgent indirection needed.
 *
 * @since 2026/7/9
 */
public interface PollController {
    /**
     * Pause periodic polling on the event bus.
     */
    void pausePolls();

    /**
     * Resume periodic polling on the event bus.
     */
    void resumePolls();
}
