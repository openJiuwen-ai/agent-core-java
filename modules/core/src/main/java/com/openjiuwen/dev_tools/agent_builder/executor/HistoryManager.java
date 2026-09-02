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
 * @since 0.1.7
 */
public class HistoryManager {
    private final HistoryCache dialogueHistory;

    /**
     * HistoryManager.
     * 
     * @since 0.1.7
     */
    public HistoryManager() {
        this.dialogueHistory = new HistoryCache();
    }

    /**
     * addMessage.
     * 
     * @param content content
     * @param role role
     * @since 0.1.7
     */
    public void addMessage(String content, String role) {
        addMessage(content, role, Instant.now());
    }

    /**
     * addMessage.
     * 
     * @param content content
     * @param role role
     * @param timestamp timestamp
     * @since 0.1.7
     */
    public void addMessage(String content, String role, Instant timestamp) {
        dialogueHistory.addMessage(new DialogueMessage(content, role, timestamp));
    }

    /**
     * addAssistantMessage.
     * 
     * @param content content
     * @since 0.1.7
     */
    public void addAssistantMessage(String content) {
        addMessage(content, "assistant");
    }

    /**
     * addUserMessage.
     * 
     * @param content content
     * @since 0.1.7
     */
    public void addUserMessage(String content) {
        addMessage(content, "user");
    }

    /**
     * getLatestKMessages.
     * 
     * @param k k
     * @return the result
     * @since 0.1.7
     */
    public List<Map<String, String>> getLatestKMessages(int k) {
        return dialogueHistory.getMessages(k);
    }

    /**
     * getHistory.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<Map<String, String>> getHistory() {
        return dialogueHistory.getMessages(-1);
    }

    /**
     * getDialogueHistory.
     * 
     * @return the result
     * @since 0.1.7
     */
    public HistoryCache getDialogueHistory() {
        return dialogueHistory;
    }

    /**
     * clear.
     * 
     * @since 0.1.7
     */
    public void clear() {
        dialogueHistory.clear();
    }
}
