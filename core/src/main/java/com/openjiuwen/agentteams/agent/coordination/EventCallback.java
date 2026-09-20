/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.agent.coordination;

import com.openjiuwen.agentteams.schema.events.CoordinationEvent;

/**
 * Synchronous callback contract for coordination events.
 *
 * <p>Mirrors Python {@code EventCallback = Callable[[CoordinationEvent], Awaitable[None]]}
 * but synchronous — Java's {@code EventBus} processes events on a single
 * thread. Exceptions thrown by callbacks are swallowed by the
 * {@link AsyncCallbackFramework} (logged and continued) so a failing callback
 * never interrupts the dispatch of remaining callbacks.
 *
 * @since 2026/7/9
 */
@FunctionalInterface
public interface EventCallback {
    /**
     * Handle a coordination event.
     *
     * @param event the coordination event to process
     */
    void on(CoordinationEvent event);
}
