/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.observability;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;

/**
 * Per-call state attached to one open LLM span.
 *
 * <p>Holds the OTel span, its context scope, the monotonic start timestamp,
 * streaming chunk tracking fields, and accumulated content for deferred
 * attribute setting.</p>
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_teams.observability.span_context.LlmSpanState}.</p>
 *
 * @since 0.1.7
 */
public final class LlmSpanState {
    private final Span span;
    private final long startNanos;
    private final Scope scope;
    private final boolean isStreaming;

    private Long firstChunkNanos;
    private int chunkCount;
    private String accumulatedContent = "";
    private String accumulatedReasoning = "";

    /**
     * Construct an LLM span state.
     *
     * @param span       the open OTel span for this LLM call
     * @param startNanos monotonic-nanosecond timestamp of when the span was opened
     * @param scope      the context scope returned by {@code span.makeCurrent()}
     * @param isStreaming  whether this is a streaming LLM call
     * @since 0.1.7
     */
    public LlmSpanState(Span span, long startNanos, Scope scope, boolean isStreaming) {
        this.span = span;
        this.startNanos = startNanos;
        this.scope = scope;
        this.isStreaming = isStreaming;
    }

    /**
     * Increment and return the next chunk sequence number.
     *
     * @return the new chunk count
     * @since 0.1.7
     */
    public int nextChunkSeq() {
        chunkCount++;
        return chunkCount;
    }

    /**
     * Get the span.
     *
     * @return the OTel span
     * @since 0.1.7
     */
    public Span getSpan() {
        return span;
    }

    /**
     * Get the start timestamp in nanoseconds.
     *
     * @return the monotonic start nanos
     * @since 0.1.7
     */
    public long getStartNanos() {
        return startNanos;
    }

    /**
     * Get the context scope.
     *
     * @return the scope, or {@code null} if none
     * @since 0.1.7
     */
    public Scope getScope() {
        return scope;
    }

    /**
     * Whether this is a streaming LLM call.
     *
     * @return true if streaming
     * @since 0.1.7
     */
    public boolean isStreaming() {
        return isStreaming;
    }

    /**
     * Get the first-chunk timestamp.
     *
     * @return the monotonic nanos of the first chunk, or {@code null} if not yet received
     * @since 0.1.7
     */
    public Long getFirstChunkNanos() {
        return firstChunkNanos;
    }

    /**
     * Set the first-chunk timestamp.
     *
     * @param firstChunkNanos the monotonic nanos of the first chunk
     * @since 0.1.7
     */
    public void setFirstChunkNanos(Long firstChunkNanos) {
        this.firstChunkNanos = firstChunkNanos;
    }

    /**
     * Get the accumulated content from streaming chunks.
     *
     * @return the accumulated content string
     * @since 0.1.7
     */
    public String getAccumulatedContent() {
        return accumulatedContent;
    }

    /**
     * Append content to the accumulated stream content.
     *
     * @param delta the content delta to append
     * @since 0.1.7
     */
    public void appendContent(String delta) {
        if (delta != null && !delta.isEmpty()) {
            accumulatedContent += delta;
        }
    }

    /**
     * Get the accumulated reasoning content from streaming chunks.
     *
     * @return the accumulated reasoning string
     * @since 0.1.7
     */
    public String getAccumulatedReasoning() {
        return accumulatedReasoning;
    }

    /**
     * Append reasoning content to the accumulated stream reasoning.
     *
     * @param delta the reasoning delta to append
     * @since 0.1.7
     */
    public void appendReasoning(String delta) {
        if (delta != null && !delta.isEmpty()) {
            accumulatedReasoning += delta;
        }
    }
}
