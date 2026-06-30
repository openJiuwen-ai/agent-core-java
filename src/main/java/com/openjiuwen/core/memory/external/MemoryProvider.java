/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.external;

import java.util.List;
import java.util.Map;

/**
 * External memory provider interface.
 */
public interface MemoryProvider {
    String getName();

    boolean isAvailable();

    void initialize(Map<String, Object> kwargs) throws Exception;

    List<Map<String, Object>> getToolSchemas();

    String handleToolCall(String toolName, Map<String, Object> args) throws Exception;

    String prefetch(String query, Map<String, Object> kwargs) throws Exception;

    void syncTurn(String userMsg, String assistantMsg, Map<String, Object> kwargs) throws Exception;

    default String systemPromptBlock() {
        return "";
    }

    default void shutdown() throws Exception {
    }

    default void onSessionEnd(List<Map<String, Object>> messages) throws Exception {
    }

    default boolean isInitialized() {
        return false;
    }
}
