/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.executor;

import java.util.*;

/**
 * Build execution history manager.
 * <p>
 * Mirrors Python's {@code HistoryManager} in
 * {@code openjiuwen.dev_tools.agent_builder.executor.history_manager}.
 */
public class HistoryManager {

    private final List<Map<String, Object>> history = new ArrayList<>();
    private final int maxHistory;

    public HistoryManager() {
        this(100);
    }

    public HistoryManager(int maxHistory) {
        this.maxHistory = maxHistory;
    }

    /** Add an entry to history. */
    public void addEntry(Map<String, Object> entry) {
        history.add(new LinkedHashMap<>(entry));
        while (history.size() > maxHistory) {
            history.remove(0);
        }
    }

    /** Get all history entries. */
    public List<Map<String, Object>> getHistory() {
        return Collections.unmodifiableList(history);
    }

    /** Get recent N entries. */
    public List<Map<String, Object>> getRecent(int n) {
        if (n >= history.size()) return getHistory();
        return Collections.unmodifiableList(history.subList(history.size() - n, history.size()));
    }

    /** Clear history. */
    public void clear() {
        history.clear();
    }
}
