/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.executor;

import com.openjiuwen.core.common.logging.LogManager;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.dev_tools.agent_builder.utils.AgentBuilderConstants;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Mirrors Python's {@code HistoryManager} in
 * {@code openjiuwen/dev_tools/agent_builder/executor/history_manager.py}.
 */
public class HistoryManager {

    private static final LoggerProtocol LOGGER = LogManager.getLogger("agent_builder");

    /**
     * Mirrors Python's {@code DialogueMessage} in
     * {@code openjiuwen/dev_tools/agent_builder/executor/history_manager.py}.
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

        public Map<String, String> toDict() {
            Map<String, String> result = new LinkedHashMap<>();
            result.put("role", role);
            result.put("content", content);
            return result;
        }
    }

    /**
     * Mirrors Python's {@code HistoryCache} in
     * {@code openjiuwen/dev_tools/agent_builder/executor/history_manager.py}.
     */
    public static final class HistoryCache {
        private final List<DialogueMessage> history;
        private final int maxHistorySize;

        public HistoryCache() {
            this(AgentBuilderConstants.DEFAULT_MAX_HISTORY_SIZE);
        }

        public HistoryCache(int maxHistorySize) {
            this.history = new ArrayList<>();
            this.maxHistorySize = maxHistorySize;
        }

        public List<DialogueMessage> getHistory() {
            return new ArrayList<>(history);
        }

        public int getMaxHistorySize() {
            return maxHistorySize;
        }

        public List<Map<String, String>> getMessages(int num) {
            int limit = num <= 0 ? maxHistorySize : num;
            List<DialogueMessage> messages =
                    history.size() > limit ? history.subList(history.size() - limit, history.size()) : history;
            List<Map<String, String>> result = new ArrayList<>(messages.size());
            for (DialogueMessage message : messages) {
                result.add(message.toDict());
            }
            return result;
        }

        public void addMessage(DialogueMessage message) {
            history.add(Objects.requireNonNull(message, "message"));
            if (history.size() > maxHistorySize) {
                DialogueMessage removed = history.remove(0);
                LOGGER.debug(
                        "History full, removed oldest message removed_role={} current_size={}",
                        removed.getRole(),
                        history.size());
            }
        }

        public void clear() {
            history.clear();
            LOGGER.debug("Dialog history cleared");
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

    public List<Map<String, String>> getLatestKMessages(int k) {
        return dialogueHistory.getMessages(k);
    }

    public List<Map<String, String>> getHistory() {
        return dialogueHistory.getMessages(-1);
    }

    public void addMessage(String content, String role) {
        addMessage(content, role, null);
    }

    public void addMessage(String content, String role, OffsetDateTime timestamp) {
        DialogueMessage message = new DialogueMessage(
                content,
                role,
                timestamp != null ? timestamp : OffsetDateTime.now(ZoneOffset.UTC));
        dialogueHistory.addMessage(message);
    }

    public void addAssistantMessage(String content) {
        addMessage(content, "assistant");
    }

    public void addUserMessage(String content) {
        addMessage(content, "user");
    }

    public void clear() {
        dialogueHistory.clear();
        LOGGER.debug("Session history cleared");
    }
}
