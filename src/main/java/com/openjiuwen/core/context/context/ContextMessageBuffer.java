/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.context;

import com.openjiuwen.core.foundation.llm.schema.BaseMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * Rolling context-message buffer with history tracking.
 *
 * <p>Mirrors Python's {@code ContextMessageBuffer} in
 * {@code openjiuwen/core/context_engine/context/message_buffer.py}.</p>
 */
public class ContextMessageBuffer {
    private final Integer maxBufferSize;
    private List<BaseMessage> contextMessages = new ArrayList<>();
    private int historyMessagesSize;

    public ContextMessageBuffer(List<BaseMessage> historyMessages, Integer maxBufferSize) {
        this.maxBufferSize = maxBufferSize;
        rebulid(historyMessages);
    }

    public int size() {
        if (maxBufferSize != null) {
            return Math.min(contextMessages.size(), maxBufferSize);
        }
        return contextMessages.size();
    }

    public void addBack(BaseMessage message) {
        contextMessages.add(message);
        resizeIfNeeded();
    }

    public void addBack(List<BaseMessage> messages) {
        contextMessages.addAll(messages);
        resizeIfNeeded();
    }

    public List<BaseMessage> getBack() {
        return getBack(null, true);
    }

    public List<BaseMessage> getBack(Integer size, boolean withHistory) {
        List<BaseMessage> visibleMessages = maxBufferSize == null
                ? new ArrayList<>(contextMessages)
                : tail(contextMessages, maxBufferSize);
        if (size == null) {
            return withHistory
                    ? visibleMessages
                    : new ArrayList<>(visibleMessages.subList(Math.min(historyMessagesSize, visibleMessages.size()),
                    visibleMessages.size()));
        }

        int totalSize = visibleMessages.size();
        int contextSize = totalSize - Math.min(historyMessagesSize, totalSize);
        int effectiveSize = withHistory ? Math.min(size, totalSize) : Math.min(size, contextSize);
        return new ArrayList<>(visibleMessages.subList(totalSize - effectiveSize, totalSize));
    }

    public List<BaseMessage> popBack(int size, boolean withHistory) {
        return popBack(Integer.valueOf(size), withHistory);
    }

    public List<BaseMessage> popBack(Integer size, boolean withHistory) {
        List<BaseMessage> poppedMessages = getBack(size, withHistory);
        int poppedSize = poppedMessages.size();
        int contextSize = contextMessages.size() - historyMessagesSize;

        if (withHistory && poppedSize > contextSize) {
            historyMessagesSize = Math.max(0, historyMessagesSize - (poppedSize - contextSize));
        }

        contextMessages = new ArrayList<>(contextMessages.subList(0, contextMessages.size() - poppedSize));
        return poppedMessages;
    }

    public void setMessages(List<BaseMessage> messages, boolean withHistory) {
        if (withHistory) {
            contextMessages = new ArrayList<>(messages);
            historyMessagesSize = 0;
            return;
        }
        List<BaseMessage> historyMessages = new ArrayList<>(contextMessages.subList(0,
                Math.min(historyMessagesSize, contextMessages.size())));
        historyMessages.addAll(messages);
        contextMessages = historyMessages;
    }

    public void rebulid(List<BaseMessage> historyMessages) {
        List<BaseMessage> safeHistoryMessages = historyMessages == null ? List.of() : historyMessages;
        if (maxBufferSize != null) {
            contextMessages = tail(safeHistoryMessages, maxBufferSize);
            historyMessagesSize = Math.min(contextMessages.size(), maxBufferSize);
            return;
        }
        contextMessages = new ArrayList<>(safeHistoryMessages);
        historyMessagesSize = contextMessages.size();
    }

    public void rebuild(List<BaseMessage> historyMessages) {
        rebulid(historyMessages);
    }

    private void resizeIfNeeded() {
        if (maxBufferSize == null) {
            return;
        }
        if (contextMessages.size() <= maxBufferSize * 2) {
            return;
        }
        contextMessages = new ArrayList<>(contextMessages.subList(maxBufferSize, contextMessages.size()));
        if (historyMessagesSize == 0 || maxBufferSize > historyMessagesSize) {
            historyMessagesSize = 0;
            return;
        }
        historyMessagesSize -= maxBufferSize;
    }

    private static List<BaseMessage> tail(List<BaseMessage> messages, int size) {
        if (size <= 0) {
            return new ArrayList<>();
        }
        int start = Math.max(0, messages.size() - size);
        return new ArrayList<>(messages.subList(start, messages.size()));
    }
}
