/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.external;

import com.openjiuwen.core.common.logging.Loggers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * OpenJiuwen external memory provider based on LongTermMemory.
 * <p>
 * Mirrors Python's {@code OpenJiuwenMemoryProvider} from
 * {@code core/memory/external/openjiuwen_memory_provider.py}.
 * <p>
 * TODO: Integrate with actual LongTermMemory infrastructure when available.
 */
public class OpenJiuwenMemoryProvider extends MemoryProvider {

    private static final int DEFAULT_RECALL_USER_MEM_NUM = 5;
    private static final int DEFAULT_RECALL_HISTORY_MEM_NUM = 3;

    private final Map<String, Object> config;

    public OpenJiuwenMemoryProvider(Map<String, Object> config) {
        this.config = config != null ? config : new HashMap<>();
    }

    @Override
    public String name() {
        return "openjiuwen";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public CompletableFuture<Void> initialize(Map<String, Object> kwargs) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public List<Map<String, Object>> getToolSchemas() {
        return List.of();
    }

    @Override
    public CompletableFuture<String> handleToolCall(String toolName, Map<String, Object> args) {
        return CompletableFuture.completedFuture("");
    }

    @Override
    public CompletableFuture<String> prefetch(String query, Map<String, Object> kwargs) {
        // TODO: Implement actual LongTermMemory search
        return CompletableFuture.completedFuture("");
    }

    @Override
    public CompletableFuture<Void> syncTurn(String userMsg, String assistantMsg, Map<String, Object> kwargs) {
        return CompletableFuture.completedFuture(null);
    }
}
