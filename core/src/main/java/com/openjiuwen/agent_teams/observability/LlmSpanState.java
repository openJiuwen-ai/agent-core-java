/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.observability;

/**
 * Per-call state attached to one open LLM span.
 * <p>
 * Mirrors Python's {@code LlmSpanState} in
 * {@code openjiuwen/agent_teams/observability/span_context.py}.
 */
public class LlmSpanState {

    private Object span;
    private long startNs;
    private Object contextToken;
    private Long firstChunkNs;
    private int chunkCount;

    public LlmSpanState() {
    }

    public LlmSpanState(Object span, long startNs, Object contextToken, Long firstChunkNs, int chunkCount) {
        this.span = span;
        this.startNs = startNs;
        this.contextToken = contextToken;
        this.firstChunkNs = firstChunkNs;
        this.chunkCount = chunkCount;
    }

    @SuppressWarnings("unchecked")
    public <T> T getSpan() {
        return (T) span;
    }

    public void setSpan(Object span) {
        this.span = span;
    }

    public long getStartNs() {
        return startNs;
    }

    public void setStartNs(long startNs) {
        this.startNs = startNs;
    }

    @SuppressWarnings("unchecked")
    public <T> T getContextToken() {
        return (T) contextToken;
    }

    public void setContextToken(Object contextToken) {
        this.contextToken = contextToken;
    }

    public Long getFirstChunkNs() {
        return firstChunkNs;
    }

    public void setFirstChunkNs(Long firstChunkNs) {
        this.firstChunkNs = firstChunkNs;
    }

    public int getChunkCount() {
        return chunkCount;
    }

    public void setChunkCount(int chunkCount) {
        this.chunkCount = chunkCount;
    }

    /**
     * Increment and return the next chunk sequence number.
     *
     * @return next observed chunk index, starting at {@code 1}
     */
    public int nextChunkSeq() {
        chunkCount += 1;
        return chunkCount;
    }
}
