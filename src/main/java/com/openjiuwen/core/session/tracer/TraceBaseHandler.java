/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.tracer;

import com.openjiuwen.core.session.callback.BaseHandler;
import com.openjiuwen.core.session.stream.StreamWriterManager;
import com.openjiuwen.core.session.stream.TraceStreamWriter;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Base class for trace handlers.
 * 
 * <p>Provides common functionality for trace data formatting and emission.
 * 
 * @author OpenJiuwen
 * @since 1.0.0
 */
public abstract class TraceBaseHandler extends BaseHandler {
    
    protected final TraceStreamWriter streamWriter;
    protected final SpanManager spanManager;
    
    /**
     * Creates a new TraceBaseHandler.
     * 
     * @param owner the owner of this handler
     * @param streamWriterManager the stream writer manager
     * @param spanManager the span manager
     */
    protected TraceBaseHandler(Object owner, StreamWriterManager streamWriterManager, SpanManager spanManager) {
        super(owner);
        this.streamWriter = streamWriterManager != null ? streamWriterManager.getTraceWriter() : null;
        this.spanManager = spanManager;
    }
    
    /**
     * Emits data to the stream writer.
     * 
     * @param data the data to emit
     * @return a CompletableFuture that completes when the data is emitted
     */
    public CompletableFuture<Void> emitStreamWriter(Object data) {
        return emitStreamWriterInternal(data);
    }
    
    /**
     * Formats the span data for output.
     * 
     * @param span the span to format
     * @return the formatted data
     */
    protected abstract Map<String, Object> formatData(Span span);
    
    /**
     * Internal method to emit data to the stream writer.
     * 
     * @param span the span to emit
     * @return a CompletableFuture that completes when the data is emitted
     */
    protected CompletableFuture<Void> emitStreamWriterInternal(Object span) {
        if (streamWriter == null) {
            return CompletableFuture.completedFuture(null);
        }
        if (span instanceof Span) {
            return streamWriter.write(formatData((Span) span));
        }
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Sends span data, optionally excluding certain fields.
     * 
     * @param span the span to send
     * @param excludeFields fields to exclude from the span data
     * @return a CompletableFuture that completes when the data is sent
     */
    protected CompletableFuture<Void> sendData(Span span, Set<String> excludeFields) {
        if (excludeFields != null && !excludeFields.isEmpty()) {
            // Create a copy of span data excluding specified fields
            Span cleanSpan = copySpanExcluding(span, excludeFields);
            return emitStreamWriter(cleanSpan);
        }
        return emitStreamWriter(span);
    }
    
    /**
     * Sends span data without exclusions.
     * 
     * @param span the span to send
     * @return a CompletableFuture that completes when the data is sent
     */
    protected CompletableFuture<Void> sendData(Span span) {
        return sendData(span, null);
    }
    
    /**
     * Copies a span excluding certain fields.
     * 
     * @param span the span to copy
     * @param excludeFields fields to exclude
     * @return the copied span
     */
    private Span copySpanExcluding(Span span, Set<String> excludeFields) {
        // For now, we return the same span since Java doesn't have dynamic field exclusion
        // In actual implementation, we might need to create specific copy methods
        return span;
    }
    
    /**
     * Gets the elapsed time as a formatted string.
     * 
     * @param startTime the start time
     * @param endTime the end time
     * @return the elapsed time string (e.g., "100ms" or "1.50s")
     */
    protected String getElapsedTime(Instant startTime, Instant endTime) {
        if (startTime == null || endTime == null) {
            return null;
        }
        Duration elapsed = Duration.between(startTime, endTime);
        long ms = elapsed.toMillis();
        if (ms < 1000) {
            return ms + "ms";
        }
        return String.format("%.2fs", ms / 1000.0);
    }
    
    /**
     * Gets the node status based on span state.
     * 
     * @param span the span to check
     * @return the node status value
     */
    protected String getNodeStatus(Span span) {
        if (span.getError() != null) {
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
    
    /**
     * Gets the span manager.
     * 
     * @return the span manager
     */
    public SpanManager getSpanManager() {
        return spanManager;
    }
}

