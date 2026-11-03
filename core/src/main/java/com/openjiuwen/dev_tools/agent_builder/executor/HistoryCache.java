/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.executor;

import java.util.ArrayList;
import java.util.List;

/**
 * Public class HistoryCache used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
public class HistoryCache {
    /**
     * DEFAULT_MAX_HISTORY_SIZE.
     * 
     * @since 0.1.7
     */
    public static final int DEFAULT_MAX_HISTORY_SIZE = 100;

    private final int maxHistorySize;

    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private final List<DialogueMessage> history = new ArrayList<>();

    /**
     * HistoryCache.
     * 
     * @since 0.1.7
     */
    public HistoryCache() {
        this(DEFAULT_MAX_HISTORY_SIZE);
    }

    /**
     * HistoryCache.
     * 
     * @param maxHistorySize maxHistorySize
     * @since 0.1.7
     */
    public HistoryCache(int maxHistorySize) {
        this.maxHistorySize = maxHistorySize;
    }

    /**
     * addMessage.
     * 
     * @param message message
     * @since 0.1.7
     */
    public void addMessage(DialogueMessage message) {
        history.add(message);
        while (history.size() > maxHistorySize) {
            history.remove(0);
        }
    }

    /**
     * getHistory.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<DialogueMessage> getHistory() {
        return new ArrayList<>(history);
    }

    /**
     * getMessages.
     * 
     * @param k k
     * @return the result
     * @since 0.1.7
     */
    public List<java.util.Map<String, String>> getMessages(int k) {
        if (k < 0 || k >= history.size()) {
            return history.stream().map(DialogueMessage::toMap).toList();
        }
        return history.subList(history.size() - k, history.size()).stream().map(DialogueMessage::toMap).toList();
    }

    /**
     * getMaxHistorySize.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getMaxHistorySize() {
        return maxHistorySize;
    }

    /**
     * clear.
     * 
     * @since 0.1.7
     */
    public void clear() {
        history.clear();
    }
}
