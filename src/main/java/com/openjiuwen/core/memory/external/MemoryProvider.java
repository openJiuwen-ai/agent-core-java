/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.external;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Memory provider interface.
 *
 * <p>Mirrors Python's {@code MemoryProvider} in {@code openjiuwen/core/memory/external/provider.py}.</p>
 */
public abstract class MemoryProvider {

    /**
     * Provider name.
     *
     * @return provider name
     */
    public abstract String getName();

    /**
     * Check if configured and ready without network calls.
     *
     * @return true when the provider is available
     */
    public abstract boolean isAvailable();

    /**
     * Initialize the provider.
     *
     * @param kwargs initialization kwargs
     * @return completion future
     */
    public abstract CompletableFuture<Void> initialize(Map<String, Object> kwargs);

    /**
     * Provider tool schemas.
     *
     * @return tool schema list
     */
    public abstract List<Map<String, Object>> getToolSchemas();

    /**
     * Handle one tool call.
     *
     * @param toolName tool name
     * @param args call arguments
     * @return tool output text
     */
    public abstract CompletableFuture<String> handleToolCall(String toolName, Map<String, Object> args);

    /**
     * Prefetch memory content.
     *
     * @param query search query
     * @param kwargs prefetch kwargs
     * @return rendered memory block
     */
    public abstract CompletableFuture<String> prefetch(String query, Map<String, Object> kwargs);

    /**
     * Sync one conversation turn.
     *
     * @param userMsg user message
     * @param assistantMsg assistant message
     * @param kwargs sync kwargs
     * @return completion future
     */
    public abstract CompletableFuture<Void> syncTurn(String userMsg, String assistantMsg, Map<String, Object> kwargs);

    public CompletableFuture<Void> initialize() {
        return initialize(Map.of());
    }

    public CompletableFuture<String> prefetch(String query) {
        return prefetch(query, Map.of());
    }

    public CompletableFuture<Void> syncTurn(String userMsg, String assistantMsg) {
        return syncTurn(userMsg, assistantMsg, Map.of());
    }

    /**
     * Return each provider's system prompt guide.
     *
     * @return system prompt block
     */
    public String systemPromptBlock() {
        return "";
    }

    /**
     * Shut the provider down.
     *
     * @return completion future
     */
    public CompletableFuture<Void> shutdown() {
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Session-end hook.
     *
     * @param messages session messages
     * @return completion future
     */
    public CompletableFuture<Void> onSessionEnd(List<Map<String, Object>> messages) {
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Whether initialize has completed.
     *
     * @return initialization state
     */
    public boolean isInitialized() {
        return false;
    }
}
