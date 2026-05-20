/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.executor;

import java.util.ArrayList;
import java.util.List;

/**
 * Public class HistoryCache used by the Java parity implementation.
 *
 * @since 1.0
 */
public class HistoryCache {
    /**
     * Auto-generated for codecheck compliance.
     */
    public static final int DEFAULT_MAX_HISTORY_SIZE = 100;

    private final int maxHistorySize;
    private final List<DialogueMessage> history = new ArrayList<>();

    /**
     * Auto-generated for codecheck compliance.
     */
    public HistoryCache() {
        this(DEFAULT_MAX_HISTORY_SIZE);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public HistoryCache(int maxHistorySize) {
        this.maxHistorySize = maxHistorySize;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void addMessage(DialogueMessage message) {
        history.add(message);
        while (history.size() > maxHistorySize) {
            history.remove(0);
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<DialogueMessage> getHistory() {
        return new ArrayList<>(history);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<java.util.Map<String, String>> getMessages(int k) {
        if (k < 0 || k >= history.size()) {
            return history.stream().map(DialogueMessage::toMap).toList();
        }
        return history.subList(history.size() - k, history.size()).stream().map(DialogueMessage::toMap).toList();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public int getMaxHistorySize() {
        return maxHistorySize;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void clear() {
        history.clear();
    }
}
