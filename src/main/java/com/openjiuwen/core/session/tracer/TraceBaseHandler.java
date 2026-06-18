/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.tracer;

import com.openjiuwen.core.session.stream.StreamWriter;
import com.openjiuwen.core.session.stream.StreamWriterManager;
import com.openjiuwen.core.session.stream.TraceSchema;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Mirrors Python's {@code TraceBaseHandler} in
 * {@code openjiuwen/core/session/tracer/handler.py}.
 */
public abstract class TraceBaseHandler {

    protected final StreamWriter<TraceSchema> streamWriter;
    protected final SpanManager spanManager;

    protected TraceBaseHandler(StreamWriterManager streamWriterManager, SpanManager spanManager) {
        this.streamWriter = streamWriterManager == null ? null : streamWriterManager.getTraceWriter();
        this.spanManager = spanManager;
    }

    public void emitStreamWriter(Object data) {
        emitStreamWriterInternal(data);
    }

    protected abstract Map<String, Object> formatData(Span span);

    protected void emitStreamWriterInternal(Object data) {
        if (streamWriter == null) {
            return;
        }
        streamWriter.write(data);
    }

    protected void sendData(Span span) {
        sendData(span, Set.of());
    }

    protected void sendData(Span span, Set<String> exclude) {
        if (span == null) {
            return;
        }
        Span snapshot = span.snapshot();
        if (exclude != null && !exclude.isEmpty()) {
            applyExclude(snapshot, exclude);
        }
        emitStreamWriterInternal(formatData(snapshot));
    }

    protected static String getElapsedTime(LocalDateTime startTime, LocalDateTime endTime) {
        Duration elapsed = Duration.between(startTime, endTime);
        long milliseconds = elapsed.toMillis();
        if (milliseconds < 1000) {
            return milliseconds + "ms";
        }
        return String.format(java.util.Locale.ROOT, "%.2fs", milliseconds / 1000.0);
    }

    protected static String getNodeStatus(Span span) {
        if (span.getError() != null && !span.getError().isEmpty()) {
            return NodeStatus.ERROR.getValue();
        }
        if (span instanceof TraceWorkflowSpan workflowSpan
                && workflowSpan.getInnerError() != null
                && !workflowSpan.getInnerError().isEmpty()) {
            return NodeStatus.ERROR.getValue();
        }
        if (span.getOnInvokeData() != null && !span.getOnInvokeData().isEmpty()) {
            return span.getEndTime() == null ? NodeStatus.RUNNING.getValue() : NodeStatus.FINISH.getValue();
        }
        if (span.getEndTime() != null) {
            return NodeStatus.FINISH.getValue();
        }
        return NodeStatus.START.getValue();
    }

    private static void applyExclude(Span span, Set<String> exclude) {
        if (exclude.contains("outputs")) {
            span.setOutputs(null);
        }
        if (span instanceof TraceWorkflowSpan workflowSpan) {
            if (exclude.contains("stream_outputs") || exclude.contains("streamOutputs")) {
                workflowSpan.setStreamOutputs(null);
            }
        }
    }

    protected static Map<String, Object> copyMap(Map<?, ?> source) {
        if (source == null) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }
}
