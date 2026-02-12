// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.contextengine.context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.exception.ErrorBuilder;
import com.openjiuwen.core.contextengine.ContextStats;
import com.openjiuwen.core.contextengine.ContextWindow;
import com.openjiuwen.core.contextengine.ModelContext;
import com.openjiuwen.core.contextengine.token.TokenCounter;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;

/**
 * Session-based implementation of ModelContext.
 *
 * 对应 Python: agent-core/openjiuwen/core/context_engine/context/context.py - SessionModelContext
 */
public class SessionModelContext implements ModelContext {
    
    private final String contextId;
    private final String sessionId;
    private ContextMessageBuffer messageBuffer;
    private final Integer windowSizeLimit;
    private final TokenCounter tokenCounter;
    
    /**
     * Creates a new SessionModelContext.
     *
     * @param contextId       unique identifier for this context
     * @param sessionId       session identifier
     * @param historyMessages initial history messages
     * @param windowSizeLimit maximum window size; null for no limit
     * @param tokenCounter    token counter for statistics; may be null
     */
    public SessionModelContext(String contextId,
                               String sessionId,
                               List<BaseMessage> historyMessages,
                               Integer windowSizeLimit,
                               TokenCounter tokenCounter) {
        this.contextId = contextId;
        this.sessionId = sessionId;
        validateAndInitMessages(historyMessages);
        this.messageBuffer = new ContextMessageBuffer(historyMessages != null ? historyMessages : Collections.emptyList());
        this.windowSizeLimit = windowSizeLimit;
        this.tokenCounter = tokenCounter;
    }
    
    @Override
    public int size() {
        return messageBuffer.size();
    }
    
    @Override
    public String getSessionId() {
        return sessionId;
    }
    
    @Override
    public String getContextId() {
        return contextId;
    }
    
    @Override
    public CompletableFuture<List<BaseMessage>> addMessages(BaseMessage message) {
        return addMessages(Collections.singletonList(message));
    }
    
    @Override
    public CompletableFuture<List<BaseMessage>> addMessages(List<BaseMessage> messages) {
        return CompletableFuture.supplyAsync(() -> {
            validateAndInitMessages(messages);
            messageBuffer.addBack(messages);
            return new ArrayList<>(messages);
        });
    }
    
    @Override
    public List<BaseMessage> popMessages(int size, boolean withHistory) {
        if (size < 0) {
            throw ErrorBuilder.build(
                StatusCode.CONTEXT_EXECUTION_ERROR,
                "pop size should be larger than 0"
            );
        }
        return messageBuffer.popBack(size, withHistory);
    }
    
    @Override
    public List<BaseMessage> getMessages(Integer size, boolean withHistory) {
        if (size != null && size < 0) {
            throw ErrorBuilder.build(
                StatusCode.CONTEXT_EXECUTION_ERROR,
                "get size should be larger than 0"
            );
        }
        return messageBuffer.getBack(size, withHistory);
    }
    
    @Override
    public void setMessages(List<BaseMessage> messages, boolean withHistory) {
        validateAndInitMessages(messages);
        messageBuffer.setMessages(messages, withHistory);
    }
    
    @Override
    public void clearMessages(boolean withHistory) {
        popMessages(size(), withHistory);
    }
    
    @Override
    public CompletableFuture<ContextWindow> getContextWindow(
            List<BaseMessage> systemMessages,
            List<ToolInfo> tools,
            Integer windowSize) {
        return CompletableFuture.supplyAsync(() -> {
            if (windowSize != null && windowSize <= 0) {
                throw ErrorBuilder.build(
                    StatusCode.CONTEXT_EXECUTION_ERROR,
                    "window size should be larger than 0"
                );
            }
            
            List<BaseMessage> actualSystemMessages;
            List<BaseMessage> contextMessages;
            
            // with specific context size
            if (windowSize != null || windowSizeLimit != null) {
                int actualWindowSize = windowSize != null ? windowSize : windowSizeLimit;
                actualSystemMessages = systemMessages != null ? new ArrayList<>(systemMessages) : new ArrayList<>();
                int systemMessagesSize = Math.min(actualSystemMessages.size(), actualWindowSize);
                actualSystemMessages = actualSystemMessages.subList(0, systemMessagesSize);
                int contextMessagesSize = actualWindowSize - systemMessagesSize;
                contextMessages = messageBuffer.getBack(contextMessagesSize, true);
            } else {
                actualSystemMessages = systemMessages != null ? new ArrayList<>(systemMessages) : new ArrayList<>();
                contextMessages = messageBuffer.getBack();
            }
            
            ContextWindow window = new ContextWindow(
                actualSystemMessages,
                contextMessages,
                tools != null ? tools : Collections.emptyList()
            );
            
            validateAndFixContextWindow(window);
            window.setStatistic(statContextWindow(window));
            return window;
        });
    }
    
    @Override
    public ContextStats statistic() {
        List<BaseMessage> messages = getMessages();
        ContextStats stat = new ContextStats();
        statMessages(stat, messages);
        return stat;
    }
    
    /**
     * Called when context is saved to persist the current state.
     */
    public void onSave() {
        List<BaseMessage> messages = messageBuffer.getBack();
        messageBuffer = new ContextMessageBuffer(messages);
    }
    
    private ContextStats statContextWindow(ContextWindow contextWindow) {
        List<BaseMessage> messages = contextWindow.getMessages();
        List<ToolInfo> tools = contextWindow.getTools();
        ContextStats stat = new ContextStats();
        statMessages(stat, messages);
        statTools(stat, tools);
        return stat;
    }
    
    private void statTools(ContextStats stat, List<ToolInfo> tools) {
        int toolTokenCount = 0;
        if (tokenCounter != null && tools != null && !tools.isEmpty()) {
            toolTokenCount = tokenCounter.countTools(tools);
        }
        
        stat.setTools(tools != null ? tools.size() : 0);
        stat.setToolTokens(toolTokenCount);
        stat.addTotalTokens(toolTokenCount);
    }
    
    private void statMessages(ContextStats stat, List<BaseMessage> messages) {
        stat.setTotalMessages(messages.size());
        
        int systemTokens = 0;
        int userTokens = 0;
        int assistantTokens = 0;
        int toolTokens = 0;
        
        for (BaseMessage msg : messages) {
            int tokenCount = countMessage(msg);
            
            switch (msg.getRole()) {
                case "assistant" -> {
                    stat.setAssistantMessages(stat.getAssistantMessages() + 1);
                    assistantTokens += tokenCount;
                }
                case "user" -> {
                    stat.setUserMessages(stat.getUserMessages() + 1);
                    userTokens += tokenCount;
                }
                case "system" -> {
                    stat.setSystemMessages(stat.getSystemMessages() + 1);
                    systemTokens += tokenCount;
                }
                case "tool" -> {
                    stat.setToolMessages(stat.getToolMessages() + 1);
                    toolTokens += tokenCount;
                }
                default -> { }
            }
        }
        
        stat.setSystemMessageTokens(systemTokens);
        stat.setUserMessageTokens(userTokens);
        stat.setAssistantMessageTokens(assistantTokens);
        stat.setToolMessageTokens(toolTokens);
        stat.addTotalTokens(systemTokens + userTokens + assistantTokens + toolTokens);
    }
    
    private int countMessage(BaseMessage message) {
        if (tokenCounter != null) {
            return tokenCounter.countMessages(Collections.singletonList(message));
        }
        return 0;
    }
    
    private void validateAndInitMessages(Object messages) {
        if (messages == null) {
            return;
        }
        
        if (messages instanceof BaseMessage) {
            return;
        }
        
        if (messages instanceof List<?> list) {
            for (Object msg : list) {
                if (!(msg instanceof BaseMessage)) {
                    throw ErrorBuilder.build(
                        StatusCode.CONTEXT_MESSAGE_INVALID,
                        "messages should be a BaseMessage or a list of BaseMessage"
                    );
                }
            }
            return;
        }
        
        throw ErrorBuilder.build(
            StatusCode.CONTEXT_MESSAGE_INVALID,
            "messages should be a BaseMessage or a list of BaseMessage"
        );
    }
    
    private static void validateAndFixContextWindow(ContextWindow contextWindow) {
        List<BaseMessage> messages = contextWindow.getContextMessages();
        if (messages == null || messages.isEmpty()) {
            return;
        }
        
        // locate the first non-ToolMessage
        int firstNonTool = 0;
        while (firstNonTool < messages.size() && messages.get(firstNonTool) instanceof ToolMessage) {
            firstNonTool++;
        }
        
        // entirely tool messages → invalid window
        if (firstNonTool == messages.size()) {
            contextWindow.setContextMessages(new ArrayList<>());
            return;
        }
        
        // slice away leading tool messages (if any)
        if (firstNonTool > 0) {
            contextWindow.setContextMessages(new ArrayList<>(messages.subList(firstNonTool, messages.size())));
        }
    }
}

