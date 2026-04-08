/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.context.context;

import com.openjiuwen.core.foundation.llm.schema.BaseMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages the context message buffer, supporting history tracking and size limits.
 * <p>
 * Mirrors Python's {@code ContextMessageBuffer} from {@code context_engine/context/message_buffer.py}.
 */
public class ContextMessageBuffer {

    private final Integer maxBufferSize;
    private List<BaseMessage> contextMessages;
    private int historyMessagesSize;

    public ContextMessageBuffer(List<BaseMessage> historyMessages, Integer maxBufferSize) {
        this.maxBufferSize = maxBufferSize;
        rebuild(historyMessages);
    }

    /**
     * Return the effective size of the buffer.
     */
    public int size() {
        if (maxBufferSize != null) {
            return Math.min(contextMessages.size(), maxBufferSize);
        }
        return contextMessages.size();
    }

    /**
     * Append messages to the back of the buffer.
     */
    public void addBack(List<BaseMessage> messages) {
        contextMessages.addAll(messages);
        ifNeedResize();
    }

    /**
     * Get messages from the back of the buffer.
     *
     * @param size        number of messages to get; null for all
     * @param withHistory whether to include history messages
     * @return list of messages
     */
    public List<BaseMessage> getBack(Integer size, boolean withHistory) {
        List<BaseMessage> available;
        if (maxBufferSize == null) {
            available = new ArrayList<>(contextMessages);
        } else {
            int start = Math.max(0, contextMessages.size() - maxBufferSize);
            available = new ArrayList<>(contextMessages.subList(start, contextMessages.size()));
        }

        if (size == null) {
            if (withHistory) {
                return available;
            }
            int historyInAvailable = Math.min(historyMessagesSize, available.size());
            return new ArrayList<>(available.subList(historyInAvailable, available.size()));
        }

        int totalSize = available.size();
        int contextSize = totalSize - Math.min(historyMessagesSize, totalSize);
        int effectiveSize = withHistory ? Math.min(size, totalSize) : Math.min(size, contextSize);
        return new ArrayList<>(available.subList(totalSize - effectiveSize, totalSize));
    }

    /**
     * Get all messages from the back.
     */
    public List<BaseMessage> getBack() {
        return getBack(null, true);
    }

    /**
     * Pop messages from the back of the buffer.
     *
     * @param size        number of messages to pop
     * @param withHistory whether to also pop from history
     * @return the popped messages
     */
    public List<BaseMessage> popBack(int size, boolean withHistory) {
        return popBack(Integer.valueOf(size), withHistory);
    }

    /**
     * Pop messages from the back of the buffer.
     * Mirrors Python's {@code pop_back(size=None, with_history=True)}.
     *
     * @param size        number of messages to pop; {@code null} pops all
     * @param withHistory whether to also pop from history
     * @return the popped messages
     */
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

    /**
     * Replace messages in the buffer.
     *
     * @param messages    the new messages
     * @param withHistory if true, replace entire buffer; if false, only replace non-history part
     */
    public void setMessages(List<BaseMessage> messages, boolean withHistory) {
        if (withHistory) {
            contextMessages = new ArrayList<>(messages);
            historyMessagesSize = 0;
            return;
        }
        List<BaseMessage> historyMessages = new ArrayList<>(
                contextMessages.subList(0, Math.min(historyMessagesSize, contextMessages.size())));
        contextMessages = new ArrayList<>(historyMessages);
        contextMessages.addAll(messages);
    }

    /**
     * Rebuild the buffer from a new list of history messages.
     */
    public void rebuild(List<BaseMessage> historyMessages) {
        if (historyMessages == null) {
            historyMessages = new ArrayList<>();
        }
        if (maxBufferSize != null) {
            int start = Math.max(0, historyMessages.size() - maxBufferSize);
            contextMessages = new ArrayList<>(historyMessages.subList(start, historyMessages.size()));
            historyMessagesSize = Math.min(contextMessages.size(), maxBufferSize);
        } else {
            contextMessages = new ArrayList<>(historyMessages);
            historyMessagesSize = contextMessages.size();
        }
    }

    private void ifNeedResize() {
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
        historyMessagesSize = historyMessagesSize - maxBufferSize;
    }
}
