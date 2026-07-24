/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.tracerotel;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manage invoke_id → {@link OtelSpanState} for agent handlers.
 *
 * <p>Used to establish parent-child relationships: when creating a child span,
 * look up the parent span's invoke_id and use its OTel context as parent context.</p>
 *
 * <p>Mirrors Python's {@code openjiuwen.extensions.tracer_otel.span_manager.OtelAgentSpanManager}.</p>
 *
 * @since 0.1.7
 */
public final class OtelAgentSpanManager {
    private final Map<String, OtelSpanState> spans = new ConcurrentHashMap<>();

    /**
     * Register a span state under the given invoke_id.
     *
     * @param invokeId the invoke id
     * @param state    the span state
     */
    public void push(String invokeId, OtelSpanState state) {
        spans.put(invokeId, state);
    }

    /**
     * Remove and return the span state for the given invoke_id.
     *
     * @param invokeId the invoke id
     * @return the removed span state, or {@code null} if not present
     */
    public OtelSpanState pop(String invokeId) {
        return spans.remove(invokeId);
    }

    /**
     * Return the span state for the given invoke_id without removing it.
     *
     * @param invokeId the invoke id
     * @return the span state, or {@code null} if not present
     */
    public OtelSpanState get(String invokeId) {
        return spans.get(invokeId);
    }
}
