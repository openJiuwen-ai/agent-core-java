/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.context;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.context.ContextStats;
import com.openjiuwen.core.context.ContextWindow;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.context.OffloadCapableContext;
import com.openjiuwen.core.context.StatefulContext;
import com.openjiuwen.core.context.processor.ContextProcessor;
import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.context.token.TokenCounter;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Core implementation of {@link ModelContext} backed by a message buffer
 * and supporting processors, offloading, and KV cache management.
 * <p>
 * Mirrors Python's {@code SessionModelContext} from {@code context_engine/context/context.py}.
 */
public class SessionModelContext extends ModelContext implements StatefulContext, OffloadCapableContext {

    private static final String RELOADER_SYSTEM_PROMPT = """
            You may see offloaded content markers in your context: [[OFFLOAD: handle=<id>, type=<type>]].
                        
            When you see an offloaded-content marker and believe retrieving it will help your answer,\s
            feel free to call reload_original_context_messages:
            - Call reload_original_context_messages(offload_handle="<id>", offload_type="<type>") with the exact values from the marker
            - Do not guess or make up the missing content
                        
            Storage types: "in_memory" (session cache).
            """;

    private final String contextId;
    private final String sessionId;
    private final ContextMessageBuffer messageBuffer;
    private final Integer defaultWindowSize;
    private final boolean enableReload;
    private final Integer defaultDialogueRound;
    private final TokenCounter tokenCounter;
    private final List<ContextProcessor> processors;
    private final KVCacheManager kvCacheManager;
    private OffloadMessageBuffer offloadMessageBuffer;
    private final ToolCard reloaderToolCard;

    public SessionModelContext(
            String contextId,
            String sessionId,
            ContextEngineConfig config,
            List<BaseMessage> historyMessages,
            List<ContextProcessor> processors,
            TokenCounter tokenCounter) {

        this.contextId = contextId;
        this.sessionId = sessionId;
        this.messageBuffer = new ContextMessageBuffer(
                historyMessages != null ? historyMessages : new ArrayList<>(),
                config.getMaxContextMessageNum());
        this.defaultWindowSize = config.getDefaultWindowMessageNum();
        this.enableReload = config.isEnableReload();
        this.defaultDialogueRound = config.getDefaultWindowRoundNum();
        this.tokenCounter = tokenCounter;
        this.processors = processors != null ? processors : new ArrayList<>();
        this.kvCacheManager = config.isEnableKvCacheRelease() ? new KVCacheManager(sessionId) : null;
        this.offloadMessageBuffer = new OffloadMessageBuffer();
        this.reloaderToolCard = ToolCard.builder()
                .id("reload_" + sessionId + "_" + contextId)
                .name("reload_original_context_messages")
                .description("Retrieve messages that were previously offloaded from the context window. "
                        + "Provide the exact handle and storage type returned when the content was offloaded; "
                        + "the tool will fetch the complete original message list and inject "
                        + "it back into the conversation, allowing the model to see the full text "
                        + "as if it had never been removed.")
                .inputParams(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "offload_handle", Map.of(
                                        "description", "A unique identifier or file path pointing to the offloaded content.",
                                        "type", "string"),
                                "offload_type", Map.of(
                                        "description", "The storage backend used when the content was offloaded (e.g., 'in_memory').",
                                        "type", "string")),
                        "required", List.of("offload_handle", "offload_type")))
                .build();
    }

    @Override
    public int size() {
        return messageBuffer.size();
    }

    @Override
    public String sessionId() {
        return sessionId;
    }

    @Override
    public String contextId() {
        return contextId;
    }

    @Override
    public List<BaseMessage> addMessages(List<BaseMessage> messages) {
        validateMessages(messages);
        List<BaseMessage> messagesToAdd = new ArrayList<>(messages);

        for (ContextProcessor processor : processors) {
            try {
                if (processor.triggerAddMessages(this, messagesToAdd)) {
                    Loggers.CONTEXT_ENGINE.info(
                            "trigger context processor " + processor.processorType() + " on ADD");
                    ContextProcessor.ProcessResult result =
                            processor.onAddMessages(this, messagesToAdd);
                    messagesToAdd = result.messages();
                }
            } catch (Exception e) {
                Loggers.CONTEXT_ENGINE.warning(
                        "Failed to process ADD messages by using processor "
                                + processor.processorType() + ", reason: " + e.getMessage());
            }
        }

        messageBuffer.addBack(messagesToAdd);
        return messagesToAdd;
    }

    @Override
    public List<BaseMessage> popMessages(int size, boolean withHistory) {
        if (size < 0) {
            throw ErrorHelper.buildError(StatusCode.CONTEXT_EXECUTION_ERROR,
                    "error_msg", "pop size should be larger than 0");
        }
        return messageBuffer.popBack(size, withHistory);
    }

    @Override
    public List<BaseMessage> getMessages(Integer size, boolean withHistory) {
        if (size != null && size < 0) {
            throw ErrorHelper.buildError(StatusCode.CONTEXT_EXECUTION_ERROR,
                    "error_msg", "get size should be larger than 0");
        }
        return messageBuffer.getBack(size, withHistory);
    }

    @Override
    public void setMessages(List<BaseMessage> messages, boolean withHistory) {
        validateMessages(messages);
        messageBuffer.setMessages(messages, withHistory);
    }

    @Override
    public void clearMessages(boolean withHistory) {
        popMessages(size(), withHistory);
        offloadMessageBuffer = new OffloadMessageBuffer();
    }

    @Override
    public ContextWindow getContextWindow(
            List<BaseMessage> systemMessages,
            List<ToolInfo> tools,
            Integer windowSize,
            Integer dialogueRound,
            Map<String, Object> kwargs) {

        if (windowSize != null && windowSize <= 0) {
            throw ErrorHelper.buildError(StatusCode.CONTEXT_EXECUTION_ERROR,
                    "error_msg", "window size should be larger than 0");
        }
        if (dialogueRound != null && dialogueRound <= 0) {
            throw ErrorHelper.buildError(StatusCode.CONTEXT_EXECUTION_ERROR,
                    "error_msg", "dialogue round should be larger than 0");
        }

        List<BaseMessage> sysMsgs = systemMessages != null ? new ArrayList<>(systemMessages) : new ArrayList<>();
        if (enableReload) {
            sysMsgs.add(new SystemMessage(RELOADER_SYSTEM_PROMPT));
        }

        WindowMessages windowMessages = getWindowMessages(sysMsgs, windowSize, dialogueRound);

        ContextWindow window = ContextWindow.builder()
                .systemMessages(windowMessages.systemMessages())
                .contextMessages(windowMessages.contextMessages())
                .tools(tools != null ? tools : new ArrayList<>())
                .build();

        Map<String, Object> effectiveKwargs = new HashMap<>(kwargs != null ? kwargs : Map.of());
        effectiveKwargs.put("window_size", windowSize);

        for (ContextProcessor processor : processors) {
            try {
                if (processor.triggerGetContextWindow(this, window)) {
                    Loggers.CONTEXT_ENGINE.info(
                            "trigger context processor " + processor.processorType() + " on GET");
                    ContextProcessor.ProcessResult result =
                            processor.onGetContextWindow(this, window);
                    if (result.contextWindow() != null) {
                        window = result.contextWindow();
                    }
                }
            } catch (Exception e) {
                Loggers.CONTEXT_ENGINE.warning(
                        "Failed to process GET messages by using processor "
                                + processor.processorType() + ", reason: " + e.getMessage());
            }
        }

        validateAndFixContextWindow(window);
        if (kvCacheManager != null) {
            Object model = effectiveKwargs.get("model");
            kvCacheManager.release(window, model);
        }
        window.setStatistic(statContextWindow(window));
        return window;
    }

    @Override
    public ContextStats statistic() {
        List<BaseMessage> messages = getMessages();
        ContextStats stat = new ContextStats();
        statMessages(stat, messages);
        return stat;
    }

    @Override
    public TokenCounter tokenCounter() {
        return tokenCounter;
    }

    @Override
    public Tool reloaderTool() {
        // Return a simple tool implementation that reloads offloaded messages
        return new ReloaderTool(reloaderToolCard, offloadMessageBuffer);
    }

    /**
     * Offload messages to the in-memory buffer.
     */
    public void offloadMessages(String offloadHandle, List<BaseMessage> messages) {
        offloadMessageBuffer.offload(offloadHandle, "in_memory", messages);
    }

    /**
     * Save context state for persistence.
     */
    public Map<String, Object> saveState() {
        Map<String, Object> state = new HashMap<>();
        state.put("messages", messageBuffer.getBack());
        state.put("offload_messages", offloadMessageBuffer.getAll());
        return state;
    }

    /**
     * Load context state from persistence.
     */
    @SuppressWarnings("unchecked")
    public void loadState(Map<String, Object> state) {
        Map<String, Object> contextState = (Map<String, Object>) state.getOrDefault(contextId, Map.of());
        List<BaseMessage> messages = (List<BaseMessage>) contextState.getOrDefault("messages", new ArrayList<>());
        validateMessages(messages);
        messageBuffer.rebuild(messages);

        Map<String, List<BaseMessage>> offloadMsgs =
                (Map<String, List<BaseMessage>>) contextState.get("offload_messages");
        offloadMessageBuffer = new OffloadMessageBuffer();
        if (offloadMsgs != null) {
            for (var entry : offloadMsgs.entrySet()) {
                validateMessages(entry.getValue());
            }
            offloadMessageBuffer = new OffloadMessageBuffer(offloadMsgs);
        }
    }

    // ==================== Private Helpers ====================

    private WindowMessages getWindowMessages(
            List<BaseMessage> systemMessages,
            Integer windowSize,
            Integer dialogueRound) {

        List<BaseMessage> contextMessages;
        Integer effectiveRound = dialogueRound != null ? dialogueRound : defaultDialogueRound;

        if (effectiveRound != null) {
            contextMessages = messageBuffer.getBack();
            int roundIndex = ContextUtils.findLastNDialogueRound(contextMessages, effectiveRound);
            if (roundIndex >= 0 && roundIndex < contextMessages.size()) {
                contextMessages = new ArrayList<>(contextMessages.subList(roundIndex, contextMessages.size()));
            }
        } else {
            contextMessages = messageBuffer.getBack();
        }

        Integer effectiveWindowSize = windowSize != null ? windowSize : defaultWindowSize;
        if (effectiveWindowSize != null) {
            int sysSize = Math.min(systemMessages.size(), effectiveWindowSize);
            systemMessages = new ArrayList<>(
                    systemMessages.subList(systemMessages.size() - sysSize, systemMessages.size()));

            int contextSize = effectiveWindowSize - sysSize;
            if (contextSize > 0) {
                int start = Math.max(0, contextMessages.size() - contextSize);
                contextMessages = new ArrayList<>(contextMessages.subList(start, contextMessages.size()));
            } else {
                contextMessages = new ArrayList<>();
            }
        }

        return new WindowMessages(systemMessages, contextMessages);
    }

    private ContextStats statContextWindow(ContextWindow contextWindow) {
        List<BaseMessage> messages = contextWindow.getMessages();
        List<ToolInfo> tools = contextWindow.getToolList();
        ContextStats stat = new ContextStats();
        statMessages(stat, messages);
        statTools(stat, tools);
        stat.setTotalDialogues(ContextUtils.findAllDialogueRound(messages).size());
        return stat;
    }

    private void statTools(ContextStats stat, List<ToolInfo> tools) {
        stat.setTools(tools.size());
        int toolTokens = 0;
        if (tokenCounter != null) {
            toolTokens = tokenCounter.countTools(tools);
        }
        stat.setToolTokens(toolTokens);
        stat.setTotalTokens(stat.getTotalTokens() + toolTokens);
    }

    private void statMessages(ContextStats stat, List<BaseMessage> messages) {
        stat.setTotalMessages(messages.size());
        for (BaseMessage msg : messages) {
            int tokens = tokenCounter != null ? tokenCounter.countMessages(List.of(msg)) : 0;
            switch (msg.getRole()) {
                case "assistant" -> {
                    stat.setAssistantMessages(stat.getAssistantMessages() + 1);
                    stat.setAssistantMessageTokens(stat.getAssistantMessageTokens() + tokens);
                }
                case "user" -> {
                    stat.setUserMessages(stat.getUserMessages() + 1);
                    stat.setUserMessageTokens(stat.getUserMessageTokens() + tokens);
                }
                case "system" -> {
                    stat.setSystemMessages(stat.getSystemMessages() + 1);
                    stat.setSystemMessageTokens(stat.getSystemMessageTokens() + tokens);
                }
                case "tool" -> {
                    stat.setToolMessages(stat.getToolMessages() + 1);
                    stat.setToolMessageTokens(stat.getToolMessageTokens() + tokens);
                }
                default -> { /* ignore unknown roles */ }
            }
        }
        stat.setTotalTokens(stat.getTotalTokens()
                + stat.getAssistantMessageTokens()
                + stat.getUserMessageTokens()
                + stat.getSystemMessageTokens()
                + stat.getToolMessageTokens());
        stat.setTotalDialogues(ContextUtils.findAllDialogueRound(messages).size());
    }

    private static void validateMessages(List<BaseMessage> messages) {
        if (messages == null) {
            return;
        }
        for (Object msg : messages) {
            if (!(msg instanceof BaseMessage)) {
                throw ErrorHelper.buildError(StatusCode.CONTEXT_MESSAGE_INVALID,
                        "error_msg", "messages should be a list of BaseMessage");
            }
        }
    }

    private static void validateAndFixContextWindow(ContextWindow contextWindow) {
        List<BaseMessage> messages = contextWindow.getContextMessages();
        if (messages == null || messages.isEmpty()) {
            return;
        }

        int firstNonTool = 0;
        while (firstNonTool < messages.size() && messages.get(firstNonTool) instanceof ToolMessage) {
            firstNonTool++;
        }

        if (firstNonTool == messages.size()) {
            contextWindow.setContextMessages(new ArrayList<>());
            return;
        }

        if (firstNonTool > 0) {
            contextWindow.setContextMessages(new ArrayList<>(messages.subList(firstNonTool, messages.size())));
        }
    }

    private record WindowMessages(List<BaseMessage> systemMessages, List<BaseMessage> contextMessages) {
    }

    /**
     * Simple tool implementation for reloading offloaded messages.
     */
    private static class ReloaderTool extends Tool {

        private final OffloadMessageBuffer offloadBuffer;

        ReloaderTool(ToolCard card, OffloadMessageBuffer offloadBuffer) {
            super(card);
            this.offloadBuffer = offloadBuffer;
        }

        @Override
        public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
            String offloadHandle = (String) inputs.get("offload_handle");
            String offloadType = (String) inputs.get("offload_type");

            List<BaseMessage> reloadedMessages = offloadBuffer.reload(offloadHandle, offloadType);
            if (reloadedMessages == null || reloadedMessages.isEmpty()) {
                return "Failed to reload messages with offload_handle=" + offloadHandle
                        + " and offload_type=" + offloadType;
            }
            return ContextUtils.formatReloadedMessages(offloadHandle, reloadedMessages);
        }

        @Override
        public java.util.Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) {
            throw new UnsupportedOperationException("ReloaderTool does not support streaming");
        }
    }
}
