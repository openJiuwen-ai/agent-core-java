/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.external;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Memory provider interface.
 * <p>
 * Mirrors Python's {@code MemoryProvider} ABC from
 * <code>memory/external/provider.py</code>.
 */
public abstract class MemoryProvider {

    /** Provider name. */
    public abstract String name();

    /** Check if configured and ready. No network calls. */
    public abstract boolean isAvailable();

    /** Initialize the provider. */
    public abstract CompletableFuture<Void> initialize(Map<String, Object> kwargs);

    /** Get tool schemas for this provider. */
    public abstract List<Map<String, Object>> getToolSchemas();

    /** Handle a tool call. */
    public abstract CompletableFuture<String> handleToolCall(String toolName, Map<String, Object> args);

    /** Prefetch memory for a query. */
    public abstract CompletableFuture<String> prefetch(String query, Map<String, Object> kwargs);

    /** Sync a turn (user message + assistant response). */
    public abstract CompletableFuture<Void> syncTurn(String userMsg, String assistantMsg, Map<String, Object> kwargs);

    /** Return each provider's guide for system prompts. */
    public String systemPromptBlock() {
        return "";
    }

    /** Shut down the provider. */
    public CompletableFuture<Void> shutdown() {
        return CompletableFuture.completedFuture(null);
    }

    /** Called when a session ends. */
    public CompletableFuture<Void> onSessionEnd(List<Map<String, Object>> messages) {
        return CompletableFuture.completedFuture(null);
    }

    /** Whether the provider has been initialized. */
    public boolean isInitialized() {
        return false;
    }
}
