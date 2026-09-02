/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.tracerotel;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;

import java.time.LocalDateTime;

/**
 * Wraps an OTel span with its context scope, invoke_id, and cached start_time.
 *
 * <p>Mirrors Python's {@code openjiuwen.extensions.tracer_otel.span_manager.OtelSpanState}.</p>
 *
 * @since 0.1.7
 */
public final class OtelSpanState {
    /** The OTel span. */
    private final Span span;

    /** The scope token returned by {@code Context.makeCurrent()} (nullable for workflow spans). */
    private final Scope scope;

    /** The invoke_id this span is associated with. */
    private final String invokeId;

    /** Cached start_time for elapsed-time calculation. */
    private final LocalDateTime startTime;

    /**
     * Construct a span state.
     *
     * @param span      the OTel span
     * @param scope     the context scope (may be {@code null})
     * @param invokeId  the invoke id
     * @param startTime the cached start time (may be {@code null})
     */
    public OtelSpanState(Span span, Scope scope, String invokeId, LocalDateTime startTime) {
        this.span = span;
        this.scope = scope;
        this.invokeId = invokeId;
        this.startTime = startTime;
    }

    public Span getSpan() {
        return span;
    }

    public Scope getScope() {
        return scope;
    }

    public String getInvokeId() {
        return invokeId;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }
}
