/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.external;

import java.util.List;
import java.util.Map;

/**
 * External memory provider interface.
 * 
 * @since 0.1.7
 */
public interface MemoryProvider {
    /**
     * getName.
     * 
     * @return the result
     * @since 0.1.7
     */
    String getName();

    /**
     * isAvailable.
     * 
     * @return the result
     * @since 0.1.7
     */
    boolean isAvailable();

    /**
     * initialize.
     * 
     * @param kwargs kwargs
     * @throws Exception Exception
     * @since 0.1.7
     */
    void initialize(Map<String, Object> kwargs) throws Exception;

    /**
     * getToolSchemas.
     * 
     * @return the result
     * @since 0.1.7
     */
    List<Map<String, Object>> getToolSchemas();

    /**
     * handleToolCall.
     * 
     * @param toolName toolName
     * @param args args
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    String handleToolCall(String toolName, Map<String, Object> args) throws Exception;

    /**
     * prefetch.
     * 
     * @param query query
     * @param kwargs kwargs
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    String prefetch(String query, Map<String, Object> kwargs) throws Exception;

    /**
     * syncTurn.
     * 
     * @param userMsg userMsg
     * @param assistantMsg assistantMsg
     * @param kwargs kwargs
     * @throws Exception Exception
     * @since 0.1.7
     */
    void syncTurn(String userMsg, String assistantMsg, Map<String, Object> kwargs) throws Exception;

    /**
     * systemPromptBlock.
     * 
     * @return the result
     * @since 0.1.7
     */
    default String systemPromptBlock() {
        return "";
    }

    /**
     * shutdown.
     * 
     * @throws Exception Exception
     * @since 0.1.7
     */
    default void shutdown() throws Exception {
    }

    /**
     * onSessionEnd.
     * 
     * @param messages messages
     * @throws Exception Exception
     * @since 0.1.7
     */
    default void onSessionEnd(List<Map<String, Object>> messages) throws Exception {
    }

    /**
     * isInitialized.
     * 
     * @return the result
     * @since 0.1.7
     */
    default boolean isInitialized() {
        return false;
    }
}
