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
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.harness.workspace.Workspace;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Core implementation of {@link ModelContext} backed by a message buffer
 * and supporting processors, offloading, and KV cache management.
 * <p>
 * Mirrors Python's {@code SessionModelContext} from {@code context_engine/context/context.py}.
 * 
 * @since 0.1.7
 */
public class SessionModelContext extends ModelContext implements StatefulContext, OffloadCapableContext {
    private static final String ACTIVE_COMPRESSION_RESULT_COMPRESSED = "compressed";
    private static final String ACTIVE_COMPRESSION_RESULT_NOOP = "noop";

    /**
     * reload_original_context_messages.
     * 
     * @return the result
     * @since 0.1.7
     */
    private static final String RELOADER_SYSTEM_PROMPT = """
        You may see offloaded content markers in your context: [[OFFLOAD: handle=<id>, type=<type>]].

        When you see an offloaded-content marker and believe retrieving it will help your answer,\s
        feel free to call reload_original_context_messages:
        - Call reload_original_context_messages(offload_handle="<id>", offload_type="<type>") with the exact \
        values from the marker
        - Do not guess or make up the missing content

        Storage types: "in_memory" (session cache).
        """;

    private final String contextId;
    private final String sessionId;
    private final ContextEngineConfig config;
    private final Session sessionRef;
    private final ContextMessageBuffer messageBuffer;
    private final Integer defaultWindowSize;
    private final boolean enableReload;
    private final Integer defaultDialogueRound;
    private final TokenCounter tokenCounter;
    private final List<ContextProcessor> processors;
    private final KVCacheManager kvCacheManager;
    private final Object workspace;
    private final SysOperation sysOperation;
    private final ContextProcessorStateRecorder processorStateRecorder;
    private OffloadMessageBuffer offloadMessageBuffer;
    private final ToolCard reloaderToolCard;

    /**
     * SessionModelContext.
     * 
     * @param contextId contextId
     * @param sessionId sessionId
     * @param config config
     * @param historyMessages historyMessages
     * @param processors processors
     * @param tokenCounter tokenCounter
     * @param sessionRef sessionRef
     * @param workspace workspace
     * @param sysOperation sysOperation
     * @since 0.1.7
     */
    public SessionModelContext(String contextId, String sessionId, ContextEngineConfig config,
            List<BaseMessage> historyMessages, List<ContextProcessor> processors, TokenCounter tokenCounter,
            Session sessionRef, Object workspace, SysOperation sysOperation) {
        this.contextId = contextId;
        this.sessionId = sessionId;
        this.config = config;
        this.sessionRef = sessionRef;
        this.messageBuffer = new ContextMessageBuffer(historyMessages != null ? historyMessages : new ArrayList<>(),
                config.getMaxContextMessageNum());
        this.defaultWindowSize = config.getDefaultWindowMessageNum();
        this.enableReload = config.isEnableReload();
        this.defaultDialogueRound = config.getDefaultWindowRoundNum();
        this.tokenCounter = tokenCounter;
        this.processors = processors != null ? processors : new ArrayList<>();
        this.kvCacheManager = config.isEnableKvCacheRelease() ? new KVCacheManager(sessionId) : null;
        this.workspace = workspace;
        this.sysOperation = sysOperation;
        this.processorStateRecorder =
            new ContextProcessorStateRecorder(sessionId, contextId, () -> this.sessionRef, tokenCounter);
        this.offloadMessageBuffer = new OffloadMessageBuffer();
        this.offloadMessageBuffer.setSysOperation(sysOperation);
        String workspaceDir = workspace instanceof Workspace ws ? ws.getRootPath().toString() : "";
        this.offloadMessageBuffer.setWorkspaceInfo(workspaceDir, sessionId);
        this.reloaderToolCard =
            ToolCard.builder().id("reload_" + sessionId + "_" + contextId).name("reload_original_context_messages")
                .description("Retrieve messages that were previously offloaded from the context window. "
                            + "Provide the exact handle and storage type returned when the content was offloaded; "
                            + "the tool will fetch the complete original message list and inject "
                            + "it back into the conversation, allowing the model to see the full text "
                            + "as if it had never been removed.")
                .inputParams(Map.of("type", "object", "properties", Map.of("offload_handle", Map.of("description",
                            "A unique identifier or file path pointing to the offloaded content.", "type", "string"),
                            "offload_type",
                            Map.of("description",
                                    "The storage backend used when the content was offloaded (e.g., 'in_memory').",
                                    "type", "string")),
                            "required", List.of("offload_handle", "offload_type")))
                .build();
    }

    /**
     * SessionModelContext.
     * 
     * @param contextId contextId
     * @param sessionId sessionId
     * @param config config
     * @param historyMessages historyMessages
     * @param processors processors
     * @param tokenCounter tokenCounter
     * @since 0.1.7
     */
    public SessionModelContext(String contextId, String sessionId, ContextEngineConfig config,
            List<BaseMessage> historyMessages, List<ContextProcessor> processors, TokenCounter tokenCounter) {
        this(contextId, sessionId, config, historyMessages, processors, tokenCounter, null, null, null);
    }

    /**
     * size.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public int size() {
        return messageBuffer.size();
    }

    /**
     * sessionId.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String sessionId() {
        return sessionId;
    }

    /**
     * contextId.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String contextId() {
        return contextId;
    }

    /**
     * sessionRef.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Session sessionRef() {
        return sessionRef;
    }

    /**
     * workspaceDir.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String workspaceDir() {
        if (workspace == null) {
            return "";
        }
        try {
            var rootMethod = workspace.getClass().getMethod("getRootPath");
            Object value = rootMethod.invoke(workspace);
            return value != null ? String.valueOf(value) : "";
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return "";
        }
    }

    /**
     * sysOperation.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public SysOperation sysOperation() {
        return sysOperation;
    }

    /**
     * addMessages.
     * 
     * @param messages messages
     * @return the result
     * @since 0.1.7
     */
    @Override
    public List<BaseMessage> addMessages(List<BaseMessage> messages) {
        validateMessages(messages);
        List<BaseMessage> messagesToAdd = new ArrayList<>(messages);

        for (ContextProcessor processor : processors) {
            try {
                if (processor.triggerAddMessages(this, messagesToAdd)) {
                    Loggers.CONTEXT_ENGINE.info("trigger context processor " + processor.processorType() + " on ADD");
                    ContextProcessor.ProcessResult result = processor.onAddMessages(this, messagesToAdd);
                    messagesToAdd = result.messages();
                }
            } catch (RuntimeException e) {
                Loggers.CONTEXT_ENGINE.warning("Failed to process ADD messages by using processor "
                        + processor.processorType() + ", reason: " + e.getMessage());
            }
        }

        messageBuffer.addBack(messagesToAdd);
        return messagesToAdd;
    }

    /**
     * popMessages.
     * 
     * @param size size
     * @param withHistory withHistory
     * @return the result
     * @since 0.1.7
     */
    @Override
    public List<BaseMessage> popMessages(int size, boolean withHistory) {
        if (size < 0) {
            throw ErrorHelper.buildError(StatusCode.CONTEXT_EXECUTION_ERROR, "error_msg",
                    "pop size should be larger than 0");
        }
        return messageBuffer.popBack(size, withHistory);
    }

    /**
     * getMessages.
     * 
     * @param size size
     * @param withHistory withHistory
     * @return the result
     * @since 0.1.7
     */
    @Override
    public List<BaseMessage> getMessages(Integer size, boolean withHistory) {
        if (size != null && size < 0) {
            throw ErrorHelper.buildError(StatusCode.CONTEXT_EXECUTION_ERROR, "error_msg",
                    "get size should be larger than 0");
        }
        return messageBuffer.getBack(size, withHistory);
    }

    /**
     * setMessages.
     * 
     * @param messages messages
     * @param withHistory withHistory
     * @since 0.1.7
     */
    @Override
    public void setMessages(List<BaseMessage> messages, boolean withHistory) {
        validateMessages(messages);
        messageBuffer.setMessages(messages, withHistory);
    }

    /**
     * clearMessages.
     * 
     * @param withHistory withHistory
     * @since 0.1.7
     */
    @Override
    public void clearMessages(boolean withHistory) {
        popMessages(size(), withHistory);
        offloadMessageBuffer = new OffloadMessageBuffer();
    }

    /**
     * compressContext.
     * 
     * @param processorTypes processorTypes
     * @param kwargs kwargs
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String compressContext(List<String> processorTypes, Map<String, Object> kwargs) {
        List<ContextProcessor> selectedProcessors = selectProcessors(processorTypes, true);
        if (selectedProcessors.isEmpty()) {
            return ACTIVE_COMPRESSION_RESULT_NOOP;
        }

        boolean isChanged = false;
        Map<String, Object> effectiveKwargs = new HashMap<>(kwargs != null ? kwargs : Map.of());
        Integer contextMax = ContextUtils.resolveContextMax(resolveContextModelName(effectiveKwargs),
                config.getContextWindowTokens(), config.getModelContextWindowTokens());

        for (ContextProcessor processor : selectedProcessors) {
            String operationId = java.util.UUID.randomUUID().toString().replace("-", "");
            double startedAt = System.currentTimeMillis() / 1000.0;
            List<BaseMessage> beforeMessages = new ArrayList<>(getMessages());

            processorStateRecorder.emit(this,
                    processorStateRecorder.buildState(new ContextProcessorStateInput(operationId, "started",
                            "active_compress", "manual", processor, "processor_triggered", beforeMessages, null,
                            startedAt, null, null, List.of(), true, contextMax)));

            try {
                ContextProcessor.ProcessResult result = processor.onAddMessages(this, List.of());
                List<Integer> modifiedIndices =
                    result != null && result.event() != null ? result.event().getMessagesToModify() : List.of();
                List<BaseMessage> afterMessages = new ArrayList<>(getMessages());
                String status = modifiedIndices.isEmpty() ? "noop" : "completed";
                processorStateRecorder.emit(this,
                        processorStateRecorder.buildState(
                                new ContextProcessorStateInput(operationId, status, "active_compress", "manual",
                                        processor, modifiedIndices.isEmpty() ? "processor_noop" : "processor_completed",
                                        beforeMessages, afterMessages, startedAt, System.currentTimeMillis() / 1000.0,
                                        null, modifiedIndices, true, contextMax)));
                if (!modifiedIndices.isEmpty()) {
                    isChanged = true;
                }
            } catch (RuntimeException exception) {
                processorStateRecorder.emit(this,
                        processorStateRecorder.buildState(new ContextProcessorStateInput(operationId, "failed",
                                "active_compress", "manual", processor, "processor_error", beforeMessages,
                                new ArrayList<>(getMessages()), startedAt, System.currentTimeMillis() / 1000.0,
                                exception.getMessage(), List.of(), true, contextMax)));
            }
        }

        return isChanged ? ACTIVE_COMPRESSION_RESULT_COMPRESSED : ACTIVE_COMPRESSION_RESULT_NOOP;
    }

    /**
     * getContextWindow.
     * 
     * @param systemMessages systemMessages
     * @param tools tools
     * @param windowSize windowSize
     * @param dialogueRound dialogueRound
     * @param kwargs kwargs
     * @return the result
     * @since 0.1.7
     */
    @Override
    public ContextWindow getContextWindow(List<BaseMessage> systemMessages, List<ToolInfo> tools, Integer windowSize,
            Integer dialogueRound, Map<String, Object> kwargs) {
        if (windowSize != null && windowSize <= 0) {
            throw ErrorHelper.buildError(StatusCode.CONTEXT_EXECUTION_ERROR, "error_msg",
                    "window size should be larger than 0");
        }
        if (dialogueRound != null && dialogueRound <= 0) {
            throw ErrorHelper.buildError(StatusCode.CONTEXT_EXECUTION_ERROR, "error_msg",
                    "dialogue round should be larger than 0");
        }

        List<BaseMessage> sysMsgs = systemMessages != null ? new ArrayList<>(systemMessages) : new ArrayList<>();
        if (enableReload) {
            sysMsgs.add(new SystemMessage(RELOADER_SYSTEM_PROMPT));
        }

        WindowMessages windowMessages = getWindowMessages(sysMsgs, windowSize, dialogueRound);

        ContextWindow window = ContextWindow.builder().systemMessages(windowMessages.systemMessages())
                .contextMessages(windowMessages.contextMessages()).tools(tools != null ? tools : new ArrayList<>())
                .build();

        Map<String, Object> effectiveKwargs = new HashMap<>(kwargs != null ? kwargs : Map.of());
        effectiveKwargs.put("window_size", windowSize);

        for (ContextProcessor processor : processors) {
            try {
                if (processor.triggerGetContextWindow(this, window)) {
                    Loggers.CONTEXT_ENGINE.info("trigger context processor " + processor.processorType() + " on GET");
                    String operationId = java.util.UUID.randomUUID().toString().replace("-", "");
                    double startedAt = System.currentTimeMillis() / 1000.0;
                    List<BaseMessage> beforeMessages = new ArrayList<>(window.getContextMessages());
                    Integer contextMax = ContextUtils.resolveContextMax(resolveContextModelName(effectiveKwargs),
                            config.getContextWindowTokens(), config.getModelContextWindowTokens());
                    processorStateRecorder.emit(this,
                            processorStateRecorder.buildState(new ContextProcessorStateInput(operationId, "started",
                                    "get_context_window", "auto", processor, "processor_triggered", beforeMessages,
                                    null, startedAt, null, null, List.of(), false, contextMax)));
                    ContextProcessor.ProcessResult result = processor.onGetContextWindow(this, window);
                    List<Integer> modifiedIndices =
                        result != null && result.event() != null ? result.event().getMessagesToModify() : List.of();
                    if (result.contextWindow() != null) {
                        window = result.contextWindow();
                    }
                    String status = modifiedIndices.isEmpty() ? "noop" : "completed";
                    processorStateRecorder.emit(this,
                            processorStateRecorder.buildState(new ContextProcessorStateInput(operationId, status,
                                    "get_context_window", "auto", processor,
                                    modifiedIndices.isEmpty() ? "processor_noop" : "processor_completed",
                                    beforeMessages, new ArrayList<>(window.getContextMessages()), startedAt,
                                    System.currentTimeMillis() / 1000.0, null, modifiedIndices, false, contextMax)));
                }
            } catch (RuntimeException e) {
                Loggers.CONTEXT_ENGINE.warning("Failed to process GET messages by using processor "
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

    /**
     * statistic.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public ContextStats statistic() {
        List<BaseMessage> messages = getMessages();
        ContextStats stat = new ContextStats();
        statMessages(stat, messages);
        return stat;
    }

    /**
     * tokenCounter.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public TokenCounter tokenCounter() {
        return tokenCounter;
    }

    /**
     * reloaderTool.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Tool reloaderTool() {
        // Return a simple tool implementation that reloads offloaded messages
        return new ReloaderTool(reloaderToolCard, offloadMessageBuffer);
    }

    /**
     * Offload messages to the in-memory buffer.
     * 
     * @param offloadHandle offloadHandle
     * @param messages messages
     * @since 0.1.7
     */
    public void offloadMessages(String offloadHandle, List<BaseMessage> messages) {
        offloadMessageBuffer.offload(offloadHandle, "in_memory", messages);
    }

    /**
     * reloadFromBuffer.
     * 
     * @param offloadHandle offloadHandle
     * @param offloadType offloadType
     * @return the result
     * @since 0.1.7
     */
    public List<BaseMessage> reloadFromBuffer(String offloadHandle, String offloadType) {
        return offloadMessageBuffer.reload(offloadHandle, offloadType);
    }

    /**
     * Save context state for persistence.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> saveState() {
        Map<String, Object> state = new HashMap<>();
        state.put("messages", messageBuffer.getBack());
        state.put("offload_messages", offloadMessageBuffer.getAll());
        return state;
    }

    /**
     * loadState.
     * 
     * @param state state
     * @since 0.1.7
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

    /**
     * getWindowMessages.
     * 
     * @param systemMessages systemMessages
     * @param windowSize windowSize
     * @param dialogueRound dialogueRound
     * @return the result
     * @since 0.1.7
     */
    private WindowMessages getWindowMessages(List<BaseMessage> systemMessages, Integer windowSize,
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
            systemMessages =
                new ArrayList<>(systemMessages.subList(systemMessages.size() - sysSize, systemMessages.size()));

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

    /**
     * statContextWindow.
     * 
     * @param contextWindow contextWindow
     * @return the result
     * @since 0.1.7
     */
    private ContextStats statContextWindow(ContextWindow contextWindow) {
        List<BaseMessage> messages = contextWindow.getMessages();
        List<ToolInfo> tools = contextWindow.getToolList();
        ContextStats stat = new ContextStats();
        statMessages(stat, messages);
        statTools(stat, tools);
        stat.setTotalDialogues(ContextUtils.findAllDialogueRound(messages).size());
        return stat;
    }

    /**
     * statTools.
     * 
     * @param stat stat
     * @param tools tools
     * @since 0.1.7
     */
    private void statTools(ContextStats stat, List<ToolInfo> tools) {
        stat.setTools(tools.size());
        int toolTokens = 0;
        if (tokenCounter != null) {
            toolTokens = tokenCounter.countTools(tools);
        }
        stat.setToolTokens(toolTokens);
        stat.setTotalTokens(stat.getTotalTokens() + toolTokens);
    }

    /**
     * statMessages.
     * 
     * @param stat stat
     * @param messages messages
     * @since 0.1.7
     */
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
                default -> {
                    /* ignore unknown roles */ }
            }
        }
        stat.setTotalTokens(stat.getTotalTokens() + stat.getAssistantMessageTokens() + stat.getUserMessageTokens()
                + stat.getSystemMessageTokens() + stat.getToolMessageTokens());
        stat.setTotalDialogues(ContextUtils.findAllDialogueRound(messages).size());
    }

    /**
     * validateMessages.
     * 
     * @param messages messages
     * @since 0.1.7
     */
    private static void validateMessages(List<BaseMessage> messages) {
        if (messages == null) {
            return;
        }
        for (Object msg : messages) {
            if (!(msg instanceof BaseMessage)) {
                throw ErrorHelper.buildError(StatusCode.CONTEXT_MESSAGE_INVALID, "error_msg",
                        "messages should be a list of BaseMessage");
            }
        }
    }

    /**
     * validateAndFixContextWindow.
     * 
     * @param contextWindow contextWindow
     * @since 0.1.7
     */
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

    /**
     * WindowMessages.
     * 
     * @param systemMessages systemMessages
     * @param contextMessages contextMessages
     * @since 0.1.7
     */
    private record WindowMessages(List<BaseMessage> systemMessages, List<BaseMessage> contextMessages) {
    }

    /**
     * selectProcessors.
     * 
     * @param processorTypes processorTypes
     * @param isCompressionOnly isCompressionOnly
     * @return the result
     * @since 0.1.7
     */
    private List<ContextProcessor> selectProcessors(List<String> processorTypes, boolean isCompressionOnly) {
        List<ContextProcessor> selected = new ArrayList<>(processors);
        if (processorTypes != null && !processorTypes.isEmpty()) {
            selected.removeIf(processor -> !processorTypes.contains(processor.processorType()));
        }
        if (isCompressionOnly) {
            selected.removeIf(processor -> !ContextUtils.isCompressionProcessor(processor));
        }
        return selected;
    }

    /**
     * resolveContextModelName.
     * 
     * @param kwargs kwargs
     * @return the result
     * @since 0.1.7
     */
    private String resolveContextModelName(Map<String, Object> kwargs) {
        Object value = kwargs.get("model_name");
        if (value instanceof String text && !text.isBlank()) {
            return text;
        }
        Object fallback = kwargs.get("model");
        if (fallback instanceof String text && !text.isBlank()) {
            return text;
        }
        return config.getModelName() != null ? config.getModelName() : "";
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

        /**
         * invoke.
         * 
         * @param inputs inputs
         * @param kwargs kwargs
         * @return the result
         * @since 0.1.7
         */
        @Override
        public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
            String offloadHandle = (String) inputs.get("offload_handle");
            String offloadType = (String) inputs.get("offload_type");

            List<BaseMessage> reloadedMessages = offloadBuffer.reload(offloadHandle, offloadType);
            if (reloadedMessages == null || reloadedMessages.isEmpty()) {
                return "Failed to reload messages with offload_handle=" + offloadHandle + " and offload_type="
                        + offloadType;
            }
            return ContextUtils.formatReloadedMessages(offloadHandle, reloadedMessages);
        }

        /**
         * stream.
         * 
         * @param inputs inputs
         * @param kwargs kwargs
         * @return Iterator<Object>
         * @since 0.1.7
         */
        @Override
        public java.util.Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) {
            throw new UnsupportedOperationException("ReloaderTool does not support streaming");
        }
    }
}
