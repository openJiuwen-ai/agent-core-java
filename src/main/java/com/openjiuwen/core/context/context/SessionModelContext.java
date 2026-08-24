/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.context;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.context.ContextStats;
import com.openjiuwen.core.context.ContextWindow;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Session-scoped model context backed by a rolling message buffer.
 *
 * <p>Mirrors Python's {@code SessionModelContext} in
 * {@code openjiuwen/core/context_engine/context/context.py}.</p>
 */
public class SessionModelContext implements ModelContext {
    public static final String ACTIVE_COMPRESSION_RESULT_BUSY = "busy";
    public static final String ACTIVE_COMPRESSION_RESULT_COMPRESSED = "compressed";
    public static final String ACTIVE_COMPRESSION_RESULT_NOOP = "noop";
    public static final String CONTEXT_MESSAGE_ID_KEY = "context_message_id";
    public static final int DEFAULT_CONTEXT_MAX_TOKENS = 200000;

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final String RELOADER_SYSTEM_PROMPT = """
            You may see offloaded content markers in your context: [[OFFLOAD: handle=<id>, type=<type>]].
                        
            When you see an offloaded-content marker and believe retrieving it will help your answer,\s
            feel free to call reload_original_context_messages:
            - Call reload_original_context_messages(offload_handle="<id>", offload_type="<type>") with the exact values from the marker
            - Do not guess or make up the missing content
                        
            Storage types: "in_memory" (session cache).
            """;
    private static final Map<String, Integer> MODEL_DEFAULT_CONTEXT_WINDOW_TOKENS = buildDefaultContextWindowTokens();

    private final String contextId;
    private final String sessionId;
    private final ContextMessageBuffer messageBuffer;
    private final Integer defaultWindowSize;
    private final boolean enableReload;
    private final Integer contextWindowTokens;
    private final String modelName;
    private final Map<String, Integer> modelContextWindowTokens;
    private final WorkspacePort workspace;
    private final SysOperationPort sysOperation;
    private final Integer defaultDialogueRound;
    private final TokenCounterPort tokenCounter;
    private final List<ContextProcessorPort> processors;
    private final ContextProcessorStateRecorder processorStateRecorder;
    private final ReentrantLock processorLock = new ReentrantLock();
    private final KvCacheManagerPort kvCacheManager;
    private final ToolInfo reloaderToolCard;

    private Object sessionRef;
    private boolean activeCompressionInProgress;
    private OffloadMessageBuffer offloadMessageBuffer;

    public SessionModelContext(String contextId, String sessionId, ContextEngineConfig config) {
        this(contextId, sessionId, config, List.of(), List.of(), null, null, null, null, null, null);
    }

    public SessionModelContext(String contextId, String sessionId, ContextEngineConfig config,
                               List<BaseMessage> historyMessages, List<ContextProcessorPort> processors,
                               TokenCounterPort tokenCounter) {
        this(contextId, sessionId, config, historyMessages, processors, tokenCounter, null, null, null, null, null);
    }

    public SessionModelContext(String contextId, String sessionId, ContextEngineConfig config,
                               List<BaseMessage> historyMessages, List<ContextProcessorPort> processors,
                               TokenCounterPort tokenCounter, Object sessionRef, WorkspacePort workspace,
                               SysOperationPort sysOperation, KvCacheManagerPort kvCacheManager,
                               ModelContextWindowTokenProvider tokenProvider) {
        ContextEngineConfig safeConfig = config == null ? new ContextEngineConfig() : config;
        validateMessages(historyMessages == null ? List.of() : historyMessages);
        List<BaseMessage> ensuredHistory = ensureContextMessageIds(historyMessages == null ? List.of() : historyMessages);
        this.contextId = contextId;
        this.sessionId = sessionId;
        this.messageBuffer = new ContextMessageBuffer(ensuredHistory, safeConfig.getMaxContextMessageNum());
        this.defaultWindowSize = safeConfig.getDefaultWindowMessageNum();
        this.enableReload = safeConfig.isEnableReload();
        this.contextWindowTokens = safeConfig.getContextWindowTokens();
        this.modelName = safeConfig.getModelName();
        this.modelContextWindowTokens = buildModelContextWindowTokens(safeConfig, tokenProvider);
        this.workspace = workspace;
        this.sysOperation = sysOperation;
        this.sessionRef = sessionRef;
        this.defaultDialogueRound = safeConfig.getDefaultWindowRoundNum();
        this.tokenCounter = tokenCounter;
        this.processors = processors == null ? new ArrayList<>() : new ArrayList<>(processors);
        this.processorStateRecorder = new ContextProcessorStateRecorder(
                sessionId, contextId, this::getSessionRef, adaptStateTokenCounter(tokenCounter), 100, null);
        this.kvCacheManager = safeConfig.isEnableKvCacheRelease() ? kvCacheManager : null;
        this.offloadMessageBuffer = new OffloadMessageBuffer();
        bindOffloadBuffer();
        this.reloaderToolCard = ToolInfo.builder()
                .name("reload_original_context_messages")
                .description("Retrieve messages that were previously offloaded from the context window."
                        + "Provide the exact handle and storage type returned when the content was offloaded;"
                        + "the tool will fetch the complete original message list and inject "
                        + "it back into the conversation, allowing the model to see the full text "
                        + "as if it had never been removed.")
                .parameters(buildReloaderInputParams())
                .build();
    }

    @Override
    public int length() {
        return messageBuffer.size();
    }

    public String workspaceDir() {
        return workspace == null ? "" : workspace.rootPath();
    }

    public Object getSessionRef() {
        return sessionRef;
    }

    public void setSessionRef(Object sessionRef) {
        this.sessionRef = sessionRef;
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
    public CompletionStage<List<BaseMessage>> addMessages(BaseMessage message) {
        return addMessages(List.of(message));
    }

    @Override
    public CompletionStage<List<BaseMessage>> addMessages(List<BaseMessage> messages) {
        validateMessages(messages);
        List<BaseMessage> messagesToAdd = removeAdjacentDuplicateMessages(ensureContextMessageIds(messages));
        if (messagesToAdd.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }
        if (activeCompressionInProgress && processorLock.isLocked()) {
            messageBuffer.addBack(messagesToAdd);
            return CompletableFuture.completedFuture(messagesToAdd);
        }

        processorLock.lock();
        try {
            ProcessorRunResult result = runAddProcessors(messagesToAdd, false, null, false, Map.of());
            messageBuffer.addBack(result.messagesToAdd());
            return CompletableFuture.completedFuture(result.messagesToAdd());
        } finally {
            processorLock.unlock();
        }
    }

    public CompletionStage<Object> compressContext(List<String> processorTypes, Map<String, Object> kwargs) {
        Map<String, Object> effectiveKwargs = new LinkedHashMap<>(kwargs == null ? Map.of() : kwargs);
        boolean returnState = toBoolean(effectiveKwargs.remove("return_state"));
        if (processorLock.isLocked()) {
            int historyStart = processorStateRecorder.history().size();
            emitCompressionState(new ContextProcessorStateInput(
                    UUID.randomUUID().toString().replace("-", ""),
                    "skipped",
                    "active_compress",
                    "manual",
                    null,
                    "busy",
                    getMessages(null, true),
                    null,
                    Instant.now().toEpochMilli() / 1000.0d,
                    Instant.now().toEpochMilli() / 1000.0d,
                    null,
                    List.of(),
                    true,
                    resolveContextMax(resolveContextModelName(effectiveKwargs), contextWindowTokens,
                            modelContextWindowTokens),
                    "",
                    null
            ));
            return CompletableFuture.completedFuture(buildActiveCompressionResult(
                    ACTIVE_COMPRESSION_RESULT_BUSY, returnState, historyStart));
        }

        processorLock.lock();
        try {
            activeCompressionInProgress = true;
            int historyStart = processorStateRecorder.history().size();
            List<ContextProcessorPort> selected = selectProcessors(processorTypes, false);
            if (selected.isEmpty()) {
                emitCompressionState(new ContextProcessorStateInput(
                        UUID.randomUUID().toString().replace("-", ""),
                        "skipped",
                        "active_compress",
                        "manual",
                        null,
                        "no_matching_processor",
                        getMessages(null, true),
                        null,
                        Instant.now().toEpochMilli() / 1000.0d,
                        Instant.now().toEpochMilli() / 1000.0d,
                        null,
                        List.of(),
                        true,
                        resolveContextMax(resolveContextModelName(effectiveKwargs), contextWindowTokens,
                                modelContextWindowTokens),
                        "",
                        null
                ));
                return CompletableFuture.completedFuture(buildActiveCompressionResult(
                        ACTIVE_COMPRESSION_RESULT_NOOP, returnState, historyStart));
            }

            ProcessorRunResult result = runAddProcessors(List.of(), true, processorTypes, true, effectiveKwargs);
            String activeResult = result.changed()
                    ? ACTIVE_COMPRESSION_RESULT_COMPRESSED
                    : ACTIVE_COMPRESSION_RESULT_NOOP;
            return CompletableFuture.completedFuture(buildActiveCompressionResult(activeResult, returnState,
                    historyStart));
        } finally {
            activeCompressionInProgress = false;
            processorLock.unlock();
        }
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

    public List<BaseMessage> getMessages() {
        return getMessages(null, true);
    }

    @Override
    public void setMessages(List<BaseMessage> messages, boolean withHistory) {
        validateMessages(messages);
        messageBuffer.setMessages(ensureContextMessageIds(messages), withHistory);
    }

    @Override
    public CompletionStage<Void> clearMessages(boolean withHistory) {
        popMessages(length(), withHistory);
        offloadMessageBuffer = new OffloadMessageBuffer();
        bindOffloadBuffer();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<ContextWindow> getContextWindow(List<BaseMessage> systemMessages,
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

        List<BaseMessage> sysMsgs = new ArrayList<>(systemMessages == null ? List.of() : systemMessages);
        if (enableReload) {
            sysMsgs.add(new SystemMessage(RELOADER_SYSTEM_PROMPT));
        }

        WindowMessages windowMessages = getWindowMessages(sysMsgs, windowSize, dialogueRound);
        ContextWindow window = new ContextWindow(windowMessages.systemMessages(), windowMessages.contextMessages(),
                tools == null ? List.of() : tools, new ContextStats());
        Map<String, Object> effectiveKwargs = new LinkedHashMap<>(kwargs == null ? Map.of() : kwargs);
        effectiveKwargs.put("window_size", windowSize);

        String trigger = stringValue(effectiveKwargs.get("compression_trigger"), "passive");
        int contextMax = resolveContextMax(resolveContextModelName(effectiveKwargs), contextWindowTokens,
                modelContextWindowTokens);
        for (ContextProcessorPort processor : processors) {
            String operationId = null;
            Double startedAt = null;
            List<BaseMessage> beforeMessages = null;
            try {
                if (await(processor.triggerGetContextWindow(this, window, effectiveKwargs))) {
                    operationId = UUID.randomUUID().toString().replace("-", "");
                    startedAt = Instant.now().toEpochMilli() / 1000.0d;
                    beforeMessages = window.getContextMessages();
                    emitCompressionState(new ContextProcessorStateInput(operationId, "started", "get_context_window",
                            trigger, processor, "processor_triggered", beforeMessages, null, startedAt, null, null,
                            List.of(), false, contextMax, "", null));
                    ProcessResult result = await(processor.onGetContextWindow(this, window, effectiveKwargs));
                    ContextProcessorEventPort event = result == null ? null : result.event();
                    if (result != null && result.contextWindow() != null) {
                        window = result.contextWindow();
                    }
                    emitCompressionState(new ContextProcessorStateInput(operationId,
                            event == null ? "noop" : "completed",
                            "get_context_window",
                            trigger,
                            processor,
                            event == null ? "processor_noop" : "processor_completed",
                            beforeMessages,
                            window.getContextMessages(),
                            startedAt,
                            Instant.now().toEpochMilli() / 1000.0d,
                            null,
                            event == null ? List.of() : event.messagesToModify(),
                            false,
                            contextMax,
                            event == null ? "" : stringValue(event.compactSummary(), ""),
                            event == null ? null : event.compressionUsage()));
                }
            } catch (RuntimeException ignored) {
                emitCompressionState(new ContextProcessorStateInput(
                        operationId == null ? UUID.randomUUID().toString().replace("-", "") : operationId,
                        "failed",
                        "get_context_window",
                        trigger,
                        processor,
                        "processor_error",
                        beforeMessages == null ? window.getContextMessages() : beforeMessages,
                        window.getContextMessages(),
                        startedAt == null ? Instant.now().toEpochMilli() / 1000.0d : startedAt,
                        Instant.now().toEpochMilli() / 1000.0d,
                        ignored.getMessage(),
                        List.of(),
                        false,
                        contextMax,
                        "",
                        null
                ));
                // Python logs and continues when a context processor fails.
            }
        }

        validateAndFixContextWindow(window);
        if (kvCacheManager != null) {
            kvCacheManager.release(window, effectiveKwargs.get("model"));
        }
        window.setStatistic(statContextWindow(window));
        return CompletableFuture.completedFuture(window);
    }

    @Override
    public ContextStats statistic() {
        ContextStats stat = new ContextStats();
        statMessages(stat, getMessages(null, true));
        return stat;
    }

    @Override
    public TokenCounterPort tokenCounter() {
        return tokenCounter;
    }

    @Override
    public ToolPort reloaderTool() {
        return new ReloaderTool(reloaderToolCard, offloadMessageBuffer);
    }

    public void offloadMessages(String offloadHandle, List<BaseMessage> messages) {
        validateMessages(messages);
        offloadMessageBuffer.offload(offloadHandle, "in_memory", ensureContextMessageIds(messages));
    }

    public Map<String, Object> saveState() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("messages", messageBuffer.getBack(null, true));
        state.put("offload_messages", offloadMessageBuffer.getAll());
        return state;
    }

    public void loadState(Map<String, Object> state) {
        Map<String, Object> contextState = asStringObjectMap(state == null ? null : state.get(contextId));
        List<BaseMessage> messages = asMessageList(contextState.get("messages"));
        validateMessages(messages);
        messageBuffer.rebuild(ensureContextMessageIds(messages));

        Map<String, List<BaseMessage>> offloadMessages = asOffloadMessages(contextState.get("offload_messages"));
        offloadMessageBuffer = new OffloadMessageBuffer(offloadMessages);
        bindOffloadBuffer();
    }

    public List<Map<String, Object>> compressionHistory() {
        return processorStateRecorder.history();
    }

    private String resolveContextModelName(Map<String, Object> kwargs) {
        Object value = kwargs == null ? null : kwargs.get("model_name");
        return value instanceof String text && !text.isBlank() ? text : modelName;
    }

    private ProcessorRunResult runAddProcessors(List<BaseMessage> messagesToAdd, boolean force,
                                                List<String> processorTypes, boolean compressionOnly,
                                                Map<String, Object> kwargs) {
        List<BaseMessage> currentMessagesToAdd = new ArrayList<>(messagesToAdd);
        boolean changed = false;
        String phase = force ? "active_compress" : "add_messages";
        String trigger = stringValue(kwargs == null ? null : kwargs.get("compression_trigger"),
                force ? "manual" : "passive");
        for (ContextProcessorPort processor : selectProcessors(processorTypes, compressionOnly)) {
            String operationId = null;
            Double startedAt = null;
            List<BaseMessage> beforeMessages = null;
            try {
                boolean shouldRun = force || await(processor.triggerAddMessages(this, currentMessagesToAdd, kwargs));
                if (!shouldRun) {
                    continue;
                }
                // Align with Python SessionModelContext._run_add_processors log line.
                Loggers.CONTEXT_ENGINE.info(
                        "{} context processor {} on ADD",
                        force ? "force trigger" : "trigger",
                        processor.processorType());
                operationId = UUID.randomUUID().toString().replace("-", "");
                startedAt = Instant.now().toEpochMilli() / 1000.0d;
                beforeMessages = concatenate(getMessages(null, true), currentMessagesToAdd);
                int contextMax = resolveContextMax(resolveContextModelName(kwargs), contextWindowTokens,
                        modelContextWindowTokens);
                emitCompressionState(new ContextProcessorStateInput(operationId, "started", phase, trigger,
                        processor, "processor_triggered", beforeMessages, null, startedAt, null, null, List.of(),
                        force, contextMax, "", null));

                ProcessResult result = await(processor.onAddMessages(this, currentMessagesToAdd, force, kwargs));
                if (result != null && result.messages() != null) {
                    currentMessagesToAdd = new ArrayList<>(result.messages());
                }
                ContextProcessorEventPort event = result == null ? null : result.event();
                List<BaseMessage> afterMessages = concatenate(getMessages(null, true), currentMessagesToAdd);
                emitCompressionState(new ContextProcessorStateInput(operationId,
                        event == null ? "noop" : "completed",
                        phase,
                        trigger,
                        processor,
                        event == null ? "processor_noop" : "processor_completed",
                        beforeMessages,
                        afterMessages,
                        startedAt,
                        Instant.now().toEpochMilli() / 1000.0d,
                        null,
                        event == null ? List.of() : event.messagesToModify(),
                        force,
                        contextMax,
                        event == null ? "" : stringValue(event.compactSummary(), ""),
                        event == null ? null : event.compressionUsage()));
                if (event != null) {
                    changed = true;
                }
            } catch (RuntimeException ex) {
                emitCompressionState(new ContextProcessorStateInput(
                        operationId == null ? UUID.randomUUID().toString().replace("-", "") : operationId,
                        "failed",
                        phase,
                        trigger,
                        processor,
                        "processor_error",
                        beforeMessages == null ? concatenate(getMessages(null, true), currentMessagesToAdd)
                                : beforeMessages,
                        concatenate(getMessages(null, true), currentMessagesToAdd),
                        startedAt == null ? Instant.now().toEpochMilli() / 1000.0d : startedAt,
                        Instant.now().toEpochMilli() / 1000.0d,
                        ex.getMessage(),
                        List.of(),
                        force,
                        resolveContextMax(resolveContextModelName(kwargs), contextWindowTokens,
                                modelContextWindowTokens),
                        "",
                        null
                ));
            }
        }
        return new ProcessorRunResult(changed, currentMessagesToAdd);
    }

    private void emitCompressionState(ContextProcessorStateInput input) {
        com.openjiuwen.core.context.context.ContextProcessorStateInput stateInput =
                new com.openjiuwen.core.context.context.ContextProcessorStateInput(
                        input.operationId(),
                        input.status(),
                        input.phase(),
                        input.trigger(),
                        input.processor(),
                        input.reason(),
                        input.beforeMessages(),
                        input.afterMessages(),
                        input.startedAt(),
                        input.endedAt(),
                        input.error(),
                        input.messagesToModify(),
                        input.force(),
                        input.contextMax(),
                        input.compactSummary(),
                        asStringObjectMap(input.compressionUsage())
                );
        ContextProcessorStateRecorder.ContextCompressionState state = processorStateRecorder.buildState(stateInput);
        processorStateRecorder.emit(this, state);
    }

    private Object buildActiveCompressionResult(String result, boolean includeState, int historyStart) {
        if (!includeState) {
            return result;
        }
        List<Map<String, Object>> history = processorStateRecorder.history();
        List<Map<String, Object>> activeHistory = historyStart > 0
                ? new ArrayList<>(history.subList(Math.min(historyStart, history.size()), history.size()))
                : history;
        Map<String, Object> state = normalizeActiveCompressionResultState(
                selectActiveCompressionResultState(activeHistory));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("result", result);
        if (state != null) {
            payload.put("state", state);
            Object compactSummary = state.get("compact_summary");
            if (compactSummary instanceof String text && !text.isBlank()) {
                payload.put("compact_summary", text);
            }
        }
        return payload;
    }

    private static Map<String, Object> selectActiveCompressionResultState(List<Map<String, Object>> history) {
        for (int i = history.size() - 1; i >= 0; i--) {
            Map<String, Object> state = history.get(i);
            Object compactSummary = state.get("compact_summary");
            if ("completed".equals(state.get("status")) && compactSummary instanceof String text
                    && !text.isBlank()) {
                return state;
            }
        }
        return history.isEmpty() ? null : history.get(history.size() - 1);
    }

    private static Map<String, Object> normalizeActiveCompressionResultState(Map<String, Object> state) {
        if (state == null) {
            return null;
        }
        Map<String, Object> normalized = new LinkedHashMap<>(state);
        if (!normalized.containsKey("reason")) {
            Object summary = normalized.get("summary");
            if (summary instanceof String text && text.startsWith("Context processor skipped: ")) {
                normalized.put("reason", text.substring("Context processor skipped: ".length()));
            }
        }
        Object usage = normalized.get("compression_usage");
        if (usage instanceof Map<?, ?> usageMap) {
            normalized.put("compression_usage", compactCompressionUsage(usageMap));
        }
        return normalized;
    }

    private static Map<String, Object> compactCompressionUsage(Map<?, ?> usageMap) {
        Map<String, Object> compact = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : usageMap.entrySet()) {
            String key = String.valueOf(entry.getKey());
            Object value = entry.getValue();
            if (isDefaultCompressionUsageValue(value)) {
                continue;
            }
            compact.put(key, value);
        }
        return compact;
    }

    private static boolean isDefaultCompressionUsageValue(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof Number number) {
            return Double.compare(number.doubleValue(), 0.0d) == 0;
        }
        if (value instanceof String text) {
            return text.isBlank();
        }
        if (value instanceof List<?> list) {
            return list.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return map.isEmpty();
        }
        return false;
    }

    private List<ContextProcessorPort> selectProcessors(List<String> processorTypes, boolean compressionOnly) {
        List<ContextProcessorPort> selected = new ArrayList<>(processors);
        if (processorTypes != null) {
            selected.removeIf(processor -> !processorTypes.contains(processor.processorType()));
        }
        if (compressionOnly) {
            selected.removeIf(processor -> !processor.compressionProcessor());
        }
        return selected;
    }

    private WindowMessages getWindowMessages(List<BaseMessage> systemMessages, Integer windowSize,
                                             Integer dialogueRound) {
        List<BaseMessage> contextMessages = messageBuffer.getBack(null, true);
        Integer effectiveRound = dialogueRound == null ? defaultDialogueRound : dialogueRound;
        if (effectiveRound != null) {
            int roundIndex = findLastNDialogueRound(contextMessages, effectiveRound);
            if (roundIndex >= 0 && roundIndex < contextMessages.size()) {
                contextMessages = new ArrayList<>(contextMessages.subList(roundIndex, contextMessages.size()));
            }
        }

        Integer effectiveWindowSize = windowSize == null ? defaultWindowSize : windowSize;
        if (effectiveWindowSize != null) {
            int systemMessagesSize = Math.min(systemMessages.size(), effectiveWindowSize);
            systemMessages = tail(systemMessages, systemMessagesSize);
            int contextMessagesSize = effectiveWindowSize - systemMessagesSize;
            contextMessages = contextMessagesSize > 0 ? tail(contextMessages, contextMessagesSize) : new ArrayList<>();
        }

        return new WindowMessages(systemMessages, contextMessages);
    }

    private ContextStats statContextWindow(ContextWindow contextWindow) {
        List<BaseMessage> messages = contextWindow.getMessages();
        List<ToolInfo> tools = contextWindow.getTools();
        ContextStats stat = new ContextStats();
        statMessages(stat, messages);
        statTools(stat, tools);
        stat.setTotalDialogues(findAllDialogueRound(messages).size());
        return stat;
    }

    private void statTools(ContextStats stat, List<ToolInfo> tools) {
        stat.setTools(tools.size());
        int toolTokens = 0;
        for (ToolInfo toolInfo : tools) {
            toolTokens += countToolTokens(toolInfo);
        }
        stat.setToolTokens(toolTokens);
        stat.setTotalTokens(stat.getTotalTokens() + toolTokens);
    }

    private int countToolTokens(ToolInfo toolInfo) {
        if (tokenCounter instanceof ToolTokenCounterPort counter) {
            return counter.countTools(List.of(toolInfo));
        }
        String textContent = stringValue(toolInfo.getName(), "") + " " + stringValue(toolInfo.getDescription(), "");
        if (toolInfo.getParameters() != null && !toolInfo.getParameters().isEmpty()) {
            textContent += writeJson(toolInfo.getParameters());
        }
        return textContent.length() / 4;
    }

    private int countSingleMessageTokens(BaseMessage message) {
        if (tokenCounter != null) {
            return tokenCounter.countTokens(List.of(message));
        }
        Object content = message.getContent();
        if (content instanceof String text) {
            return text.length() / 4;
        }
        if (content instanceof List<?> parts) {
            int total = 0;
            for (Object part : parts) {
                if (part instanceof String text) {
                    total += text.length() / 4;
                } else if (part instanceof Map<?, ?> map && map.get("text") instanceof String text) {
                    total += text.length() / 4;
                }
            }
            return total;
        }
        return 0;
    }

    private Integer getLastAssistantUsageTokens(List<BaseMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            BaseMessage message = messages.get(i);
            if (message instanceof AssistantMessage assistantMessage && assistantMessage.getUsageMetadata() != null
                    && assistantMessage.getUsageMetadata().getTotalTokens() > 0) {
                return assistantMessage.getUsageMetadata().getTotalTokens();
            }
        }
        return null;
    }

    private void statMessages(ContextStats stat, List<BaseMessage> messages) {
        stat.setTotalMessages(messages.size());
        stat.setTotalDialogues(findAllDialogueRound(messages).size());
        for (BaseMessage message : messages) {
            switch (stringValue(message.getRole(), "")) {
                case "assistant" -> stat.setAssistantMessages(stat.getAssistantMessages() + 1);
                case "user" -> stat.setUserMessages(stat.getUserMessages() + 1);
                case "system" -> stat.setSystemMessages(stat.getSystemMessages() + 1);
                case "tool" -> stat.setToolMessages(stat.getToolMessages() + 1);
                default -> {
                }
            }
        }

        Integer usageTokens = getLastAssistantUsageTokens(messages);
        if (usageTokens != null) {
            stat.setTotalTokens(usageTokens);
            return;
        }

        for (BaseMessage message : messages) {
            int messageTokens = countSingleMessageTokens(message);
            switch (stringValue(message.getRole(), "")) {
                case "assistant" -> stat.setAssistantMessageTokens(stat.getAssistantMessageTokens() + messageTokens);
                case "user" -> stat.setUserMessageTokens(stat.getUserMessageTokens() + messageTokens);
                case "system" -> stat.setSystemMessageTokens(stat.getSystemMessageTokens() + messageTokens);
                case "tool" -> stat.setToolMessageTokens(stat.getToolMessageTokens() + messageTokens);
                default -> {
                }
            }
        }

        stat.setTotalTokens(stat.getTotalTokens()
                + stat.getAssistantMessageTokens()
                + stat.getUserMessageTokens()
                + stat.getSystemMessageTokens()
                + stat.getToolMessageTokens());
    }

    private static void validateMessages(List<BaseMessage> messages) {
        if (messages == null) {
            throw ErrorHelper.buildError(StatusCode.CONTEXT_MESSAGE_INVALID,
                    "error_msg", "messages should be a BaseMessage or a list of BaseMessage");
        }
        for (Object message : messages) {
            if (!(message instanceof BaseMessage)) {
                throw ErrorHelper.buildError(StatusCode.CONTEXT_MESSAGE_INVALID,
                        "error_msg", "messages should be a BaseMessage or a list of BaseMessage");
            }
        }
    }

    private static List<BaseMessage> ensureContextMessageIds(List<BaseMessage> messages) {
        List<BaseMessage> ensured = new ArrayList<>(messages);
        for (BaseMessage message : ensured) {
            Map<String, Object> metadata = message.getMetadata();
            if (metadata == null) {
                metadata = new LinkedHashMap<>();
                message.setMetadata(metadata);
            }
            Object existingId = metadata.get(CONTEXT_MESSAGE_ID_KEY);
            if (!(existingId instanceof String text) || text.isBlank()) {
                metadata.put(CONTEXT_MESSAGE_ID_KEY, UUID.randomUUID().toString().replace("-", ""));
            }
        }
        return ensured;
    }

    private List<BaseMessage> removeAdjacentDuplicateMessages(List<BaseMessage> messages) {
        List<BaseMessage> result = new ArrayList<>();
        BaseMessage previous = lastContextMessage();
        for (BaseMessage message : messages) {
            if (isRepeatedQuestionerFeedbackAfterExtraction(previous, message)) {
                continue;
            }
            if (previous != null && previous.equals(message)) {
                continue;
            }
            if (isAssistantMessage(previous) && isAssistantMessage(message)) {
                if (result.isEmpty()) {
                    popMessages(1, true);
                } else {
                    result.remove(result.size() - 1);
                }
            }
            result.add(message);
            previous = message;
        }
        return result;
    }

    private boolean isRepeatedQuestionerFeedbackAfterExtraction(BaseMessage previous, BaseMessage message) {
        if (!isAssistantMessage(previous) || !isUserMessage(message)) {
            return false;
        }
        List<BaseMessage> existing = messageBuffer.getBack(4, true);
        return existing.size() == 4
                && isUserMessage(existing.get(0))
                && isQuestionerPrompt(existing.get(1))
                && isUserMessage(existing.get(2))
                && existing.get(2).equals(message)
                && isJsonObjectContent(previous);
    }

    private static boolean isAssistantMessage(BaseMessage message) {
        return message != null && "assistant".equals(message.getRole());
    }

    private static boolean isUserMessage(BaseMessage message) {
        return message != null && "user".equals(message.getRole());
    }

    private static boolean isQuestionerPrompt(BaseMessage message) {
        if (!isAssistantMessage(message) || !(message.getContent() instanceof String content)) {
            return false;
        }
        return content.contains("请您提供") || content.contains("Please provide");
    }

    private static boolean isJsonObjectContent(BaseMessage message) {
        if (!(message != null && message.getContent() instanceof String content)) {
            return false;
        }
        String trimmed = content.trim();
        return trimmed.startsWith("{") && trimmed.endsWith("}");
    }

    private BaseMessage lastContextMessage() {
        List<BaseMessage> existing = messageBuffer.getBack(1, true);
        return existing.isEmpty() ? null : existing.get(existing.size() - 1);
    }

    private static void validateAndFixContextWindow(ContextWindow contextWindow) {
        List<BaseMessage> messages = contextWindow.getContextMessages();
        if (messages.isEmpty()) {
            return;
        }
        int firstNonTool = 0;
        while (firstNonTool < messages.size() && messages.get(firstNonTool) instanceof ToolMessage) {
            firstNonTool++;
        }
        if (firstNonTool == messages.size()) {
            contextWindow.setContextMessages(List.of());
            return;
        }
        if (firstNonTool > 0) {
            contextWindow.setContextMessages(messages.subList(firstNonTool, messages.size()));
        }
    }

    private static int resolveContextMax(String modelName, Integer fallbackContextWindowTokens,
                                         Map<String, Integer> modelContextWindowTokens) {
        if (fallbackContextWindowTokens != null && fallbackContextWindowTokens > 0) {
            return fallbackContextWindowTokens;
        }
        if (modelName != null && !modelName.isBlank()) {
            Integer configuredValue = modelContextWindowTokens == null ? null : modelContextWindowTokens.get(modelName);
            if (configuredValue != null && configuredValue > 0) {
                return configuredValue;
            }
            Integer builtinValue = MODEL_DEFAULT_CONTEXT_WINDOW_TOKENS.get(modelName);
            if (builtinValue != null && builtinValue > 0) {
                return builtinValue;
            }
        }
        return DEFAULT_CONTEXT_MAX_TOKENS;
    }

    private static List<List<Integer>> findAllDialogueRound(List<BaseMessage> messages) {
        List<List<Integer>> rounds = new ArrayList<>();
        int i = messages.size() - 1;
        while (i >= 0) {
            Integer assistantIndex = null;
            int roundEnd = i;
            while (i >= 0 && !"assistant".equals(messages.get(i).getRole())) {
                i--;
            }
            if (i >= 0) {
                BaseMessage message = messages.get(i);
                if (!hasToolCalls(message)) {
                    assistantIndex = i;
                }
                i--;
            } else {
                i = roundEnd;
            }

            while (i >= 0 && !"user".equals(messages.get(i).getRole())) {
                i--;
            }
            if (i < 0) {
                break;
            }
            int foundUserIndex = i;
            int userIndex = findContiguousUserGroupStart(messages, foundUserIndex);

            if (rounds.isEmpty()) {
                for (int lastRoundIndex = messages.size() - 1; lastRoundIndex > foundUserIndex; lastRoundIndex--) {
                    if ("user".equals(messages.get(lastRoundIndex).getRole())) {
                        rounds.add(List.of(findContiguousUserGroupStart(messages, lastRoundIndex), -1));
                        break;
                    }
                }
            }
            rounds.add(List.of(userIndex, assistantIndex == null ? -1 : assistantIndex));
            i = userIndex - 1;
        }
        return rounds;
    }

    private static int findLastNDialogueRound(List<BaseMessage> messages, int n) {
        List<List<Integer>> rounds = findAllDialogueRound(messages);
        if (rounds.isEmpty()) {
            return -1;
        }
        return rounds.get(Math.min(n, rounds.size()) - 1).get(0);
    }

    private static int findContiguousUserGroupStart(List<BaseMessage> messages, int userIndex) {
        int index = userIndex;
        while (index - 1 >= 0 && "user".equals(messages.get(index - 1).getRole())) {
            index--;
        }
        return index;
    }

    private static boolean hasToolCalls(BaseMessage message) {
        return message instanceof AssistantMessage assistant
                && assistant.getToolCalls() != null
                && !assistant.getToolCalls().isEmpty();
    }

    private static List<BaseMessage> concatenate(List<BaseMessage> first, List<BaseMessage> second) {
        List<BaseMessage> result = new ArrayList<>(first);
        result.addAll(second);
        return result;
    }

    private static <T> T await(CompletionStage<T> stage) {
        return stage.toCompletableFuture().join();
    }

    private static List<BaseMessage> tail(List<BaseMessage> values, int size) {
        if (size <= 0) {
            return new ArrayList<>();
        }
        int start = Math.max(0, values.size() - size);
        return new ArrayList<>(values.subList(start, values.size()));
    }

    private static boolean toBoolean(Object value) {
        if (value instanceof Boolean flag) {
            return flag;
        }
        if (value instanceof String text) {
            return Boolean.parseBoolean(text);
        }
        return value != null;
    }

    private void bindOffloadBuffer() {
        offloadMessageBuffer.setSysOperation(sysOperation == null ? null : sysOperation::readFile);
        offloadMessageBuffer.setWorkspaceInfo(workspaceDir(), sessionId);
    }

    private static ContextProcessorStateRecorder.TokenCounterPort adaptStateTokenCounter(
            ModelContext.TokenCounterPort tokenCounter) {
        if (tokenCounter == null) {
            return null;
        }
        return new ContextProcessorStateRecorder.TokenCounterPort() {
            @Override
            public Integer countMessages(List<BaseMessage> messages) {
                return tokenCounter.countTokens(messages == null ? List.of() : messages);
            }

            @Override
            public Integer count(Object content) {
                return tokenCounter.countTokens(List.of(new BaseMessage("user", content == null ? "" : content)));
            }
        };
    }

    private static String stringValue(Object value, String defaultValue) {
        return value == null ? defaultValue : String.valueOf(value);
    }

    private static String writeJson(Object value) {
        try {
            return JSON_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return String.valueOf(value);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asStringObjectMap(Object value) {
        if (value instanceof Map<?, ?> rawMap) {
            Map<String, Object> result = new LinkedHashMap<>();
            rawMap.forEach((key, mapValue) -> result.put(String.valueOf(key), mapValue));
            return result;
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private static List<BaseMessage> asMessageList(Object value) {
        if (value instanceof List<?> list) {
            List<BaseMessage> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof BaseMessage message) {
                    result.add(message);
                    continue;
                }
                BaseMessage parsed = messageFromMap(item);
                if (parsed != null) {
                    result.add(parsed);
                }
            }
            return result;
        }
        return new ArrayList<>();
    }

    private static BaseMessage messageFromMap(Object item) {
        if (!(item instanceof Map<?, ?> rawMap)) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        rawMap.forEach((key, value) -> map.put(String.valueOf(key), value));
        String role = stringValue(map.get("role"), "user");
        Object content = map.getOrDefault("content", "");
        BaseMessage message;
        if ("assistant".equals(role)) {
            AssistantMessage assistantMessage = new AssistantMessage(stringValue(content, ""));
            if (map.get("tool_calls") instanceof List<?> toolCalls) {
                assistantMessage.setToolCallsRaw(toolCalls);
            }
            message = assistantMessage;
        } else if ("tool".equals(role)) {
            ToolMessage toolMessage = new ToolMessage("", stringValue(map.get("tool_call_id"), ""));
            toolMessage.setContent(content == null ? "" : content);
            message = toolMessage;
        } else if ("system".equals(role)) {
            message = new SystemMessage(stringValue(content, ""));
        } else {
            message = new UserMessage(stringValue(content, ""));
        }
        if (map.get("name") instanceof String name) {
            message.setName(name);
        }
        if (map.get("metadata") instanceof Map<?, ?> metadataMap) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadataMap.forEach((key, metadataValue) -> metadata.put(String.valueOf(key), metadataValue));
            message.setMetadata(metadata);
        }
        return message;
    }

    private static Map<String, List<BaseMessage>> asOffloadMessages(Object value) {
        if (!(value instanceof Map<?, ?> rawMap)) {
            return new LinkedHashMap<>();
        }
        Map<String, List<BaseMessage>> result = new LinkedHashMap<>();
        rawMap.forEach((key, mapValue) -> result.put(String.valueOf(key), asMessageList(mapValue)));
        return result;
    }

    private static Map<String, Object> buildReloaderInputParams() {
        Map<String, Object> offloadHandle = new LinkedHashMap<>();
        offloadHandle.put("description", "A unique identifier or file path pointing to the offloaded content. "
                + "Accepts either a UUID string for memory-based storage.");
        offloadHandle.put("type", "string");
        Map<String, Object> offloadType = new LinkedHashMap<>();
        offloadType.put("description", "The storage backend used when the content was offloaded. Must be one of: "
                + "'in_memory': Content was stored in in-memory cache.");
        offloadType.put("type", "string");
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("offload_handle", offloadHandle);
        properties.put("offload_type", offloadType);
        Map<String, Object> inputParams = new LinkedHashMap<>();
        inputParams.put("type", "object");
        inputParams.put("properties", properties);
        inputParams.put("required", List.of("offload_handle", "offload_type"));
        return inputParams;
    }

    private static Map<String, Integer> buildModelContextWindowTokens(ContextEngineConfig config,
                                                                      ModelContextWindowTokenProvider provider) {
        Map<String, Integer> resolvedTokens = new LinkedHashMap<>();
        if (config.isEnableOpenrouterModelContextWindowTokens() && provider != null) {
            resolvedTokens.putAll(provider.fetch(config.getOpenrouterRequestTimeout()));
        }
        if (config.getModelContextWindowTokens() != null) {
            resolvedTokens.putAll(config.getModelContextWindowTokens());
        }
        return resolvedTokens;
    }

    private static Map<String, Integer> buildDefaultContextWindowTokens() {
        Map<String, Integer> values = new HashMap<>();
        values.put("glm-5.1", 200000);
        values.put("glm-5", 200000);
        values.put("glm-5-turbo", 200000);
        values.put("glm-4.7", 200000);
        values.put("glm-4.7-flash", 200000);
        values.put("glm-4.7-flashx", 200000);
        values.put("glm-4-long", 1000000);
        values.put("glm-4", 128000);
        values.put("glm-4-9b-chat-1m", 1048576);
        values.put("gpt-5.5", 1050000);
        values.put("gpt-5.4", 1050000);
        values.put("gpt-5.4-mini", 400000);
        values.put("gpt-5.4-nano", 400000);
        values.put("gpt-5", 400000);
        values.put("gpt-5-mini", 400000);
        values.put("gpt-5-nano", 400000);
        values.put("gpt-4.1", 1047576);
        values.put("gpt-4.1-mini", 1047576);
        values.put("gpt-4.1-nano", 1047576);
        values.put("gpt-4o", 128000);
        values.put("gpt-4o-mini", 128000);
        values.put("gpt-4-turbo", 128000);
        values.put("gpt-3.5-turbo", 16384);
        values.put("deepseek-v4-pro", 1000000);
        values.put("deepseek-v4-flash", 1000000);
        values.put("deepseek-v3", 128000);
        values.put("deepseek-chat", 65536);
        values.put("claude-opus-4-7", 1000000);
        values.put("claude-opus-4-6", 1000000);
        values.put("claude-sonnet-4-6", 1000000);
        values.put("claude-haiku-4-5", 200000);
        values.put("claude-opus-4.6", 1000000);
        values.put("claude-sonnet-4.6", 1000000);
        values.put("claude-haiku-4.5", 200000);
        values.put("gemini-3-pro-preview", 1048576);
        values.put("gemini-3-flash-preview", 1048576);
        values.put("gemini-2.5-pro", 1048576);
        values.put("gemini-2.5-flash", 1048576);
        values.put("llama-4-maverick", 1000000);
        values.put("llama-4-scout", 10000000);
        values.put("qwen3-max", 262144);
        values.put("qwen3.5-plus", 1000000);
        values.put("qwen3.5-flash", 1000000);
        values.put("qwen3-coder-plus", 1000000);
        values.put("qwen3-coder-next", 262144);
        values.put("qwen-max", 262144);
        values.put("qwen-plus", 1000000);
        values.put("qwen-flash", 1000000);
        values.put("qwen-turbo", 8192);
        values.put("qwen-long", 1000000);
        values.put("kimi-k2.5", 262144);
        values.put("MiniMax-M2.7", 204800);
        values.put("MiniMax-M2.7-highspeed", 204800);
        values.put("MiniMax-M2.5", 204800);
        values.put("MiniMax-M2.5-highspeed", 204800);
        values.put("grok-4.3", 1000000);
        values.put("grok-4.3-latest", 1000000);
        values.put("grok-latest", 1000000);
        return Collections.unmodifiableMap(values);
    }

    /**
     * Workspace adapter used only for the root path needed by offload reload.
     *
     * <p>Mirrors Python's dynamic {@code workspace} collaborator in
     * {@code openjiuwen/core/context_engine/context/context.py}.</p>
     */
    public interface WorkspacePort {
        String rootPath();
    }

    /**
     * Narrow filesystem adapter used by filesystem offload reload.
     *
     * <p>Mirrors Python's dynamic {@code sys_operation.fs().read_file(...)} collaborator in
     * {@code openjiuwen/core/context_engine/context/context.py}.</p>
     */
    public interface SysOperationPort {
        Optional<String> readFile(String path);
    }

    /**
     * Optional provider for OpenRouter model context windows.
     *
     * <p>Mirrors Python's {@code ContextUtils.fetch_openrouter_model_context_window_tokens} use in
     * {@code openjiuwen/core/context_engine/context/context.py}.</p>
     */
    public interface ModelContextWindowTokenProvider {
        Map<String, Integer> fetch(double timeoutSeconds);
    }

    /**
     * Token counter extension for counting tools in addition to messages.
     *
     * <p>Mirrors Python's {@code TokenCounter} in
     * {@code openjiuwen/core/context_engine/context/context.py}.</p>
     */
    public interface ToolTokenCounterPort extends TokenCounterPort {
        int countTools(List<ToolInfo> tools);
    }

    /**
     * Narrow context processor adapter.
     *
     * <p>Mirrors Python's {@code ContextProcessor} collaborator in
     * {@code openjiuwen/core/context_engine/context/context.py}.</p>
     */
    public interface ContextProcessorPort
            extends com.openjiuwen.core.context.context.ContextProcessorStateInput.ContextProcessorPort {
        String processorType();

        default boolean compressionProcessor() {
            String type = processorType() == null ? "" : processorType().toLowerCase();
            return type.contains("compressor") || type.contains("compact");
        }

        default CompletionStage<Boolean> triggerAddMessages(SessionModelContext context,
                                                            List<BaseMessage> messages,
                                                            Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(false);
        }

        default CompletionStage<ProcessResult> onAddMessages(SessionModelContext context,
                                                             List<BaseMessage> messages,
                                                             boolean force,
                                                             Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(new ProcessResult(null, messages, null));
        }

        default CompletionStage<Boolean> triggerGetContextWindow(SessionModelContext context,
                                                                 ContextWindow window,
                                                                 Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(false);
        }

        default CompletionStage<ProcessResult> onGetContextWindow(SessionModelContext context,
                                                                  ContextWindow window,
                                                                  Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(new ProcessResult(null, null, window));
        }
    }

    /**
     * Narrow processor event adapter.
     *
     * <p>Mirrors Python's processor event object in
     * {@code openjiuwen/core/context_engine/context/context.py}.</p>
     */
    public interface ContextProcessorEventPort {
        default List<Integer> messagesToModify() {
            return List.of();
        }

        default String compactSummary() {
            return "";
        }

        default Object compressionUsage() {
            return null;
        }
    }

    /**
     * KV cache release adapter.
     *
     * <p>Mirrors Python's {@code KVCacheManager.release} collaborator in
     * {@code openjiuwen/core/context_engine/context/context.py}.</p>
     */
    public interface KvCacheManagerPort {
        void release(ContextWindow contextWindow, Object model);
    }

    /**
     * Result returned by context processor adapters.
     *
     * <p>Mirrors Python's processor return tuple in
     * {@code openjiuwen/core/context_engine/context/context.py}.</p>
     */
    public record ProcessResult(ContextProcessorEventPort event, List<BaseMessage> messages,
                                ContextWindow contextWindow) {
    }

    /**
     * Compression-state input DTO.
     *
     * <p>Mirrors Python's {@code ContextProcessorStateInput} use in
     * {@code openjiuwen/core/context_engine/context/context.py}.</p>
     */
    public record ContextProcessorStateInput(String operationId, String status, String phase, String trigger,
                                             ContextProcessorPort processor, String reason,
                                             List<BaseMessage> beforeMessages, List<BaseMessage> afterMessages,
                                             Double startedAt, Double endedAt, String error,
                                             List<Integer> messagesToModify, boolean force, int contextMax,
                                             String compactSummary, Object compressionUsage) {
    }

    /**
     * Active processor run result.
     *
     * <p>Mirrors Python's {@code _run_add_processors} return values in
     * {@code openjiuwen/core/context_engine/context/context.py}.</p>
     */
    private record ProcessorRunResult(boolean changed, List<BaseMessage> messagesToAdd) {
    }

    /**
     * Window-message split result.
     *
     * <p>Mirrors Python's {@code _get_window_messages} tuple in
     * {@code openjiuwen/core/context_engine/context/context.py}.</p>
     */
    private record WindowMessages(List<BaseMessage> systemMessages, List<BaseMessage> contextMessages) {
    }

    /**
     * Reload tool facade.
     *
     * <p>Mirrors Python's {@code reloader_tool} returned tool in
     * {@code openjiuwen/core/context_engine/context/context.py}.</p>
     */
    public static final class ReloaderTool implements ToolPort {
        private final ToolInfo toolInfo;
        private final OffloadMessageBuffer offloadBuffer;

        private ReloaderTool(ToolInfo toolInfo, OffloadMessageBuffer offloadBuffer) {
            this.toolInfo = Objects.requireNonNull(toolInfo, "toolInfo");
            this.offloadBuffer = Objects.requireNonNull(offloadBuffer, "offloadBuffer");
        }

        @Override
        public String name() {
            return toolInfo.getName();
        }

        public ToolInfo toolInfo() {
            return toolInfo;
        }

        public String reloadOriginalContextMessages(String offloadHandle, String offloadType) {
            List<BaseMessage> reloadedMessages = offloadBuffer.reloadBlocking(offloadHandle, offloadType);
            if (reloadedMessages.isEmpty()) {
                return "Failed to reload messages with offload_handle=" + offloadHandle
                        + " and offload_type=" + offloadType;
            }
            return formatReloadedMessages(offloadHandle, reloadedMessages);
        }
    }

    private static String formatReloadedMessages(String offloadHandle, List<BaseMessage> messages) {
        StringBuilder formattedContent = new StringBuilder("reload messages with handle=")
                .append(offloadHandle)
                .append(":\n");
        for (int index = 0; index < messages.size(); index++) {
            formattedContent.append("message ").append(index + 1).append(": ")
                    .append(writeJson(messages.get(index).modelDump()));
            if (index != messages.size() - 1) {
                formattedContent.append('\n');
            }
        }
        return formattedContent.toString();
    }
}
