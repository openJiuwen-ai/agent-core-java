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

    /**
     * Returns the latest stored message without count-based tool-group realignment.
     *
     * @return the last buffer entry, or {@code null} when empty
     */
    public BaseMessage peekLast() {
        if (contextMessages.isEmpty()) {
            return null;
        }
        return contextMessages.get(contextMessages.size() - 1);
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
        // Never return a slice that starts mid tool-call group (issue #200 / IntentRecognizer getMessages(N)).
        return ContextUtils.alignedTail(visibleMessages, effectiveSize);
    }

    public List<BaseMessage> popBack(int size, boolean withHistory) {
        return popBack(Integer.valueOf(size), withHistory);
    }

    public List<BaseMessage> popBack(Integer size, boolean withHistory) {
        // Drain the full physical buffer without alignment when the caller asks for
        // everything stored (null size or size covering all physical messages).
        if (withHistory && (size == null || size >= contextMessages.size())) {
            return clear(true);
        }
        if (!withHistory && size == null) {
            return clear(false);
        }

        List<BaseMessage> visibleMessages = maxBufferSize == null
                ? new ArrayList<>(contextMessages)
                : tail(contextMessages, maxBufferSize);
        int totalSize = visibleMessages.size();
        int contextSizeInVisible = totalSize - Math.min(historyMessagesSize, totalSize);
        int effectiveSize = withHistory ? Math.min(size, totalSize) : Math.min(size, contextSizeInVisible);
        // Align like getBack, but remove exactly what is returned so tool results are
        // never deleted without being handed to the caller.
        // Incomplete leading tool fragments stay in the buffer with their assistant.
        List<BaseMessage> poppedMessages = ContextUtils.alignedTail(visibleMessages, effectiveSize);
        int removeCount = poppedMessages.size();

        int contextSize = contextMessages.size() - historyMessagesSize;
        if (withHistory && removeCount > contextSize) {
            historyMessagesSize = Math.max(0, historyMessagesSize - (removeCount - contextSize));
        }

        if (removeCount > 0) {
            contextMessages = new ArrayList<>(contextMessages.subList(0, contextMessages.size() - removeCount));
        }
        return poppedMessages;
    }

    /**
     * Drains stored messages without tool-group realignment.
     *
     * @param withHistory {@code true} clears the entire buffer; {@code false} keeps the history prefix
     * @return messages removed from storage
     * @since 0.1.17
     */
    public List<BaseMessage> clear(boolean withHistory) {
        if (withHistory) {
            List<BaseMessage> removed = new ArrayList<>(contextMessages);
            contextMessages = new ArrayList<>();
            historyMessagesSize = 0;
            return removed;
        }
        int historyEnd = Math.min(historyMessagesSize, contextMessages.size());
        List<BaseMessage> removed = new ArrayList<>(contextMessages.subList(historyEnd, contextMessages.size()));
        contextMessages = new ArrayList<>(contextMessages.subList(0, historyEnd));
        return removed;
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
        // Drop at least the oldest maxBufferSize messages; skip further if the cut
        // would leave orphan ToolMessages at the head of the retained buffer.
        int cut = ContextUtils.skipLeadingOrphanToolMessages(contextMessages, maxBufferSize);
        int dropped = cut;
        contextMessages = new ArrayList<>(contextMessages.subList(cut, contextMessages.size()));
        if (historyMessagesSize == 0 || dropped >= historyMessagesSize) {
            historyMessagesSize = 0;
            return;
        }
        historyMessagesSize -= dropped;
    }

    private static List<BaseMessage> tail(List<BaseMessage> messages, int size) {
        return ContextUtils.alignedTail(messages, size);
    }
}
