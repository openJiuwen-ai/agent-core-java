/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.agent.coordination;

/**
 * Composite host contract combining round control and lifecycle control.
 *
 * <p>Mirrors Python {@code DispatcherHost} protocol
 * ({@code agent/coordination/dispatcher.py:134-136}). Implemented by the owning
 * {@code TeamAgent} so coordination handlers can drive round behavior and
 * lifecycle effects through a single object. Poll control no longer goes
 * through the host; handlers receive a {@link PollController} directly from
 * the {@code EventBus}.
 *
 * @since 2026/7/9
 */
public interface DispatcherHost extends AgentRoundController, TeamLifecycleController {
}
