// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.contextengine.context;

import java.util.ArrayList;
import java.util.List;

import com.openjiuwen.core.foundation.llm.schema.BaseMessage;

/**
 * Message buffer for context management.
 * Manages a list of messages with history boundary tracking.
 *
 * 对应 Python: agent-core/openjiuwen/core/context_engine/context/message_buffer.py - ContextMessageBuffer
 */
public class ContextMessageBuffer {
    
    private List<BaseMessage> contextMessages;
    private int historyMessagesSize;
    
    /**
     * Creates a new buffer with the given history messages.
     *
     * @param historyMessages initial history messages
     */
    public ContextMessageBuffer(List<? extends BaseMessage> historyMessages) {
        this.contextMessages = historyMessages != null 
            ? new ArrayList<>(historyMessages) 
            : new ArrayList<>();
        this.historyMessagesSize = this.contextMessages.size();
    }
    
    /**
     * Returns the total size of messages in the buffer.
     *
     * @return number of messages
     */
    public int size() {
        return contextMessages.size();
    }
    
    /**
     * Add a single message to the back of the buffer.
     *
     * @param message message to add
     */
    public void addBack(BaseMessage message) {
        contextMessages.add(message);
    }
    
    /**
     * Add multiple messages to the back of the buffer.
     *
     * @param messages messages to add
     */
    public void addBack(List<? extends BaseMessage> messages) {
        if (messages != null) {
            contextMessages.addAll(messages);
        }
    }
    
    /**
     * Get messages from the back of the buffer.
     *
     * @param size        number of messages to retrieve; null for all
     * @param withHistory if true, include history messages
     * @return list of messages
     */
    public List<BaseMessage> getBack(Integer size, boolean withHistory) {
        List<BaseMessage> result = new ArrayList<>(contextMessages);
        
        if (size == null) {
            return withHistory 
                ? result 
                : result.subList(historyMessagesSize, result.size());
        }
        
        int totalSize = result.size();
        int contextSize = totalSize - historyMessagesSize;
        int actualSize = withHistory 
            ? Math.min(size, totalSize) 
            : Math.min(size, contextSize);
        
        return new ArrayList<>(result.subList(totalSize - actualSize, totalSize));
    }
    
    /**
     * Get all messages from the back of the buffer including history.
     *
     * @return list of all messages
     */
    public List<BaseMessage> getBack() {
        return getBack(null, true);
    }
    
    /**
     * Pop messages from the back of the buffer.
     *
     * @param size        number of messages to pop; null for all
     * @param withHistory if true, include history messages
     * @return list of popped messages
     */
    public List<BaseMessage> popBack(Integer size, boolean withHistory) {
        List<BaseMessage> poppedMessages = getBack(size, withHistory);
        int poppedSize = poppedMessages.size();
        contextMessages = new ArrayList<>(contextMessages.subList(0, this.size() - poppedSize));
        return poppedMessages;
    }
    
    /**
     * Set messages in the buffer.
     *
     * @param messages    new messages
     * @param withHistory if true, replace all messages and reset history boundary;
     *                    if false, preserve history messages and replace only new segment
     */
    public void setMessages(List<BaseMessage> messages, boolean withHistory) {
        if (withHistory) {
            this.contextMessages = messages != null ? new ArrayList<>(messages) : new ArrayList<>();
            this.historyMessagesSize = 0;
            return;
        }
        
        List<BaseMessage> historyMessages = new ArrayList<>(
            contextMessages.subList(0, historyMessagesSize)
        );
        this.contextMessages = new ArrayList<>(historyMessages);
        if (messages != null) {
            this.contextMessages.addAll(messages);
        }
    }
    
    /**
     * Gets the history messages size.
     *
     * @return number of history messages
     */
    public int getHistoryMessagesSize() {
        return historyMessagesSize;
    }
}

