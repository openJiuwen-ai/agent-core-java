/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.executor;

import com.openjiuwen.dev_tools.agent_builder.utils.AgentBuilderConstants;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Build execution history manager.
 * <p>
 * Mirrors Python's {@code HistoryManager} in
 * {@code openjiuwen.dev_tools.agent_builder.executor.history_manager}.
 */
public class HistoryManager {

    /**
     * Mirrors Python's {@code DialogueMessage} dataclass.
     */
    public static final class DialogueMessage {
        private final String content;
        private final String role;
        private final OffsetDateTime timestamp;

        public DialogueMessage(String content, String role, OffsetDateTime timestamp) {
            this.content = content;
            this.role = role;
            this.timestamp = timestamp;
        }

        public String getContent() {
            return content;
        }

        public String getRole() {
            return role;
        }

        public OffsetDateTime getTimestamp() {
            return timestamp;
        }

        public Map<String, Object> toDict() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("role", role);
            result.put("content", content);
            return result;
        }
    }

    /**
     * Mirrors Python's {@code HistoryCache}.
     */
    public static final class HistoryCache {
        private final List<DialogueMessage> history = new ArrayList<>();
        private final int maxHistorySize;

        public HistoryCache() {
            this(AgentBuilderConstants.DEFAULT_MAX_HISTORY_SIZE);
        }

        public HistoryCache(int maxHistorySize) {
            this.maxHistorySize = maxHistorySize;
        }

        public int getMaxHistorySize() {
            return maxHistorySize;
        }

        public List<DialogueMessage> getHistory() {
            return List.copyOf(history);
        }

        public List<Map<String, Object>> getMessages(int num) {
            int limit = num <= 0 ? maxHistorySize : num;
            int fromIndex = Math.max(0, history.size() - limit);
            List<Map<String, Object>> messages = new ArrayList<>();
            for (DialogueMessage message : history.subList(fromIndex, history.size())) {
                messages.add(message.toDict());
            }
            return Collections.unmodifiableList(messages);
        }

        public void addMessage(DialogueMessage message) {
            history.add(Objects.requireNonNull(message, "message"));
            while (history.size() > maxHistorySize) {
                history.remove(0);
            }
        }

        public void clear() {
            history.clear();
        }
    }

    private final HistoryCache dialogueHistory;

    public HistoryManager() {
        this(AgentBuilderConstants.DEFAULT_MAX_HISTORY_SIZE);
    }

    public HistoryManager(int maxHistorySize) {
        this.dialogueHistory = new HistoryCache(maxHistorySize);
    }

    public HistoryCache getDialogueHistory() {
        return dialogueHistory;
    }

    public List<Map<String, Object>> getLatestKMessages(int k) {
        return dialogueHistory.getMessages(k);
    }

    /** Add an entry to history. */
    public void addEntry(Map<String, Object> entry) {
        Objects.requireNonNull(entry, "entry");
        Object content = entry.containsKey("content") ? entry.get("content") : entry.getOrDefault("query", "");
        addMessage(
                String.valueOf(content),
                String.valueOf(entry.getOrDefault("role", "user")));
    }

    public void addMessage(String content, String role) {
        addMessage(content, role, OffsetDateTime.now());
    }

    public void addMessage(String content, String role, OffsetDateTime timestamp) {
        dialogueHistory.addMessage(new DialogueMessage(content, role, timestamp));
    }

    public void addAssistantMessage(String content) {
        addMessage(content, "assistant");
    }

    public void addUserMessage(String content) {
        addMessage(content, "user");
    }

    /** Get all history entries. */
    public List<Map<String, Object>> getHistory() {
        return dialogueHistory.getMessages(-1);
    }

    /** Get recent N entries. */
    public List<Map<String, Object>> getRecent(int n) {
        return dialogueHistory.getMessages(n);
    }

    /** Clear history. */
    public void clear() {
        dialogueHistory.clear();
    }
}
