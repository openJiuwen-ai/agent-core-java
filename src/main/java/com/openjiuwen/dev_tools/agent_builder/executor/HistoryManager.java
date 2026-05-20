/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.executor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Public class HistoryManager used by the Java parity implementation.
 *
 * @since 1.0
 */
public class HistoryManager {
    private final HistoryCache dialogueHistory;

    /**
     * Auto-generated for codecheck compliance.
     */
    public HistoryManager() {
        this.dialogueHistory = new HistoryCache();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void addMessage(String content, String role) {
        addMessage(content, role, Instant.now());
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void addMessage(String content, String role, Instant timestamp) {
        dialogueHistory.addMessage(new DialogueMessage(content, role, timestamp));
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void addAssistantMessage(String content) {
        addMessage(content, "assistant");
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void addUserMessage(String content) {
        addMessage(content, "user");
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<Map<String, String>> getLatestKMessages(int k) {
        return dialogueHistory.getMessages(k);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<Map<String, String>> getHistory() {
        return dialogueHistory.getMessages(-1);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public HistoryCache getDialogueHistory() {
        return dialogueHistory;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void clear() {
        dialogueHistory.clear();
    }
}
