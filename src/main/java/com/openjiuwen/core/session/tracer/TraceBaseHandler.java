/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.tracer;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.session.callback.BaseHandler;
import com.openjiuwen.core.session.callback.TriggerEvent;
import com.openjiuwen.core.session.stream.StreamWriter;
import com.openjiuwen.core.session.stream.StreamWriterManager;
import com.openjiuwen.core.session.stream.TraceSchema;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Base trace handler providing common span updates and stream writing.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.tracer.handler.TraceBaseHandler}.
 */
public abstract class TraceBaseHandler extends BaseHandler {

    protected final StreamWriter<TraceSchema> streamWriter;
    protected final SpanManager spanManager;

    @SuppressWarnings("unchecked")
    protected TraceBaseHandler(Object owner, StreamWriterManager streamWriterManager, SpanManager spanManager) {
        super(owner);
        this.streamWriter = streamWriterManager != null ? streamWriterManager.getTraceWriter() : null;
        this.spanManager = spanManager;
    }

    /**
     * Format span data for stream emission.
     */
    protected Map<String, Object> formatData(Span span) {
        Map<String, Object> data = new HashMap<>();
        data.put("type", eventName());
        data.put("payload", span);
        return data;
    }

    /**
     * Emit span data to the trace stream writer.
     */
    protected void emitStreamWriter(Span span) {
        if (streamWriter == null) {
            return;
        }
        streamWriter.write(formatData(span));
    }

    /**
     * Send span data to stream.
     */
    protected void sendData(Span span) {
        emitStreamWriter(span != null ? span.snapshot() : null);
    }

    /**
     * Calculate elapsed time string.
     */
    protected String getElapsedTime(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null || endTime == null) return null;
        Duration duration = Duration.between(startTime, endTime);
        long ms = duration.toMillis();
        if (ms < 1000) {
            return ms + "ms";
        }
        return String.format("%.2fs", ms / 1000.0);
    }

    /**
     * Determine node status from span state.
     */
    protected String getNodeStatus(Span span) {
        if (span.getError() != null) {
            return NodeStatus.ERROR.getValue();
        }
        if (span.getOnInvokeData() != null && !span.getOnInvokeData().isEmpty()) {
            return span.getEndTime() != null ? NodeStatus.FINISH.getValue() : NodeStatus.RUNNING.getValue();
        }
        if (span.getEndTime() != null) {
            return NodeStatus.FINISH.getValue();
        }
        return NodeStatus.START.getValue();
    }
}
