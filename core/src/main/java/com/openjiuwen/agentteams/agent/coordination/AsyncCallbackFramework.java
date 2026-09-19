/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.agent.coordination;

import com.openjiuwen.agentteams.schema.events.CoordinationEvent;
import com.openjiuwen.core.common.logging.Loggers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal synchronous callback framework for coordination event dispatch.
 *
 * <p>Mirrors the subset of Python's {@code AsyncCallbackFramework} that the
 * coordination {@code EventDispatcher} actually uses: {@code register_sync} +
 * {@code trigger}. Exceptions are caught, logged, and swallowed — a failing
 * callback never interrupts the dispatch of remaining callbacks (Python iron
 * rule 3). Callbacks are invoked in registration order (stable, default
 * priority 0). No metrics, no logging flags, no hooks, no filters.
 *
 * @since 2026/7/9
 */
public class AsyncCallbackFramework {
    private final Map<String, List<EventCallback>> callbacks = new LinkedHashMap<>();

    /**
     * Register a callback for an event key.
     *
     * <p>Registration order determines fan-out order on shared event keys.
     *
     * @param eventKey event key (e.g. {@code "message"}, {@code "coordination_poll_task"})
     * @param callback the callback to invoke when the event fires
     */
    public void registerSync(String eventKey, EventCallback callback) {
        callbacks.computeIfAbsent(eventKey, k -> new ArrayList<>()).add(callback);
    }

    /**
     * Trigger all callbacks registered for the given event key.
     *
     * <p>Exceptions are caught, logged, and swallowed — a failing callback
     * never interrupts the dispatch of remaining callbacks. Each callback runs
     * in registration order.
     *
     * @param eventKey the event key to fire
     * @param event the event to pass to each callback
     */
    public void trigger(String eventKey, CoordinationEvent event) {
        List<EventCallback> entries = callbacks.get(eventKey);
        if (entries == null || entries.isEmpty()) {
            return;
        }
        for (EventCallback cb : entries) {
            runCallbackSafely(cb, event, eventKey);
        }
    }

    /**
     * Invoke a single callback with boundary safety net.
     *
     * <p>Catches {@code RuntimeException} broadly so one failing callback
     * never interrupts the dispatch of remaining callbacks (Python iron rule 3).
     *
     * @param cb the callback to invoke
     * @param event the event to pass
     * @param eventKey the event key (for logging)
     */
    @SuppressWarnings("G.ERR.02")
    private static void runCallbackSafely(EventCallback cb, CoordinationEvent event, String eventKey) {
        try {
            cb.on(event);
        } catch (IllegalStateException | NullPointerException
                | IllegalArgumentException | UnsupportedOperationException e) {
            Loggers.AGENT.error(
                    "AsyncCallbackFramework: callback for event '{}' failed: {}",
                    eventKey, e.getMessage(), e);
        }
    }
}
