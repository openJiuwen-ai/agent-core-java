/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context_engine.processor.offloader;

import com.openjiuwen.core.context_engine.ContextEngine;
import com.openjiuwen.core.context_engine.context.ContextUtils;
import com.openjiuwen.core.context_engine.context.SessionModelContext;
import com.openjiuwen.core.context_engine.processor.ContextEvent;
import com.openjiuwen.core.context_engine.schema.OffloadMessage;
import com.openjiuwen.core.context_engine.schema.OffloadMessages;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Offloads oversized tool results round-by-round until each round fits the configured budget.
 *
 * <p>Mirrors Python's {@code ToolResultBudgetProcessor} in
 * {@code openjiuwen/core/context_engine/processor/offloader/tool_result_budget_processor.py}.</p>
 */
public class ToolResultBudgetProcessor extends MessageOffloader {
    public static final String PERSISTED_OUTPUT_TAG = "<persisted-output>";
    public static final String PERSISTED_OUTPUT_CLOSING_TAG = "</persisted-output>";

    private final ToolResultBudgetProcessorConfig budgetConfig;

    static {
        ContextEngine.registerProcessor("ToolResultBudgetProcessor", ToolResultBudgetProcessor.class);
    }

    public ToolResultBudgetProcessor(Object config) {
        this(asConfig(config));
    }

    public ToolResultBudgetProcessor(ToolResultBudgetProcessorConfig config) {
        super(toOffloaderConfig(config == null ? new ToolResultBudgetProcessorConfig() : config));
        this.budgetConfig = config == null ? new ToolResultBudgetProcessorConfig() : config;
    }

    public ToolResultBudgetProcessorConfig getBudgetConfig() {
        return budgetConfig;
    }

    @Override
    public CompletionStage<Boolean> triggerAddMessages(SessionModelContext context, List<BaseMessage> messagesToAdd,
                                                       Map<String, Object> kwargs) {
        List<BaseMessage> allMessages = new ArrayList<>(context == null ? List.of() : context.getMessages());
        allMessages.addAll(messagesToAdd == null ? List.of() : messagesToAdd);
        return CompletableFuture.completedFuture(!roundBudgetExceeded(allMessages, context).isEmpty());
    }

    @Override
    public CompletionStage<SessionModelContext.ProcessResult> onAddMessages(SessionModelContext context,
                                                                            List<BaseMessage> messagesToAdd,
                                                                            boolean force,
                                                                            Map<String, Object> kwargs) {
        List<BaseMessage> updatedMessages = new ArrayList<>(context == null ? List.of() : context.getMessages());
        int contextSize = context == null ? 0 : context.length();
        List<BaseMessage> incoming = messagesToAdd == null ? List.of() : new ArrayList<>(messagesToAdd);
        updatedMessages.addAll(incoming);

        List<Integer> modifiedIndices = new ArrayList<>();
        for (RoundRange roundRange : iterRoundRanges(updatedMessages)) {
            ShrinkResult result = shrinkRoundToBudget(updatedMessages, roundRange, context, kwargs);
            if (result.changed()) {
                modifiedIndices.addAll(result.modifiedIndices());
            }
        }

        if (modifiedIndices.isEmpty()) {
            return CompletableFuture.completedFuture(new SessionModelContext.ProcessResult(null, incoming, null));
        }
        if (context != null) {
            context.setMessages(new ArrayList<>(updatedMessages.subList(0, Math.min(contextSize,
                    updatedMessages.size()))), true);
        }
        List<BaseMessage> processedIncoming = new ArrayList<>(
                updatedMessages.subList(Math.min(contextSize, updatedMessages.size()), updatedMessages.size()));
        return CompletableFuture.completedFuture(new SessionModelContext.ProcessResult(
                new ContextEvent(processorType(), sortedDistinct(modifiedIndices), "", null),
                processedIncoming,
                null));
    }

    @Override
    public void loadState(Map<String, Object> state) {
        // Python implementation is stateless.
    }

    @Override
    public Map<String, Object> saveState() {
        return new LinkedHashMap<>();
    }

    List<RoundRange> roundBudgetExceeded(List<BaseMessage> messages, SessionModelContext context) {
        List<RoundRange> exceeded = new ArrayList<>();
        for (RoundRange roundRange : iterRoundRanges(messages)) {
            int totalSize = roundToolResultSize(messages, roundRange.startIndex(), roundRange.endIndex(), context);
            if (totalSize > budgetConfig.getTokensThreshold()
                    && !collectRoundCandidates(messages, roundRange.startIndex(), roundRange.endIndex(),
                    context).isEmpty()) {
                exceeded.add(roundRange);
            }
        }
        return exceeded;
    }

    List<RoundRange> iterRoundRanges(List<BaseMessage> messages) {
        List<ContextUtils.DialogueRound> rounds = new ArrayList<>(ContextUtils.findAllDialogueRound(
                messages == null ? List.of() : messages));
        java.util.Collections.reverse(rounds);
        List<RoundRange> ranges = new ArrayList<>();
        for (ContextUtils.DialogueRound round : rounds) {
            int startIndex = round.userIndex();
            Integer assistantIndex = round.assistantIndex();
            int endIndex = assistantIndex == null ? (messages == null ? 0 : messages.size()) - 1 : assistantIndex;
            if (startIndex < 0 || endIndex < 0 || startIndex > endIndex) {
                continue;
            }
            ranges.add(new RoundRange(startIndex, endIndex));
        }
        return ranges;
    }

    int roundToolResultSize(List<BaseMessage> messages, int startIndex, int endIndex, SessionModelContext context) {
        int size = 0;
        List<BaseMessage> safeMessages = messages == null ? List.of() : messages;
        for (int index = startIndex; index <= endIndex && index < safeMessages.size(); index++) {
            BaseMessage message = safeMessages.get(index);
            if (message instanceof ToolMessage toolMessage) {
                size += messageSize(toolMessage, context);
            }
        }
        return size;
    }

    int messageSize(ToolMessage message, SessionModelContext context) {
        if (context != null && context.tokenCounter() != null) {
            try {
                return context.tokenCounter().countTokens(List.of(message));
            } catch (RuntimeException ignored) {
                return ContextUtils.estimateTokens(message == null ? "" : message.getContent());
            }
        }
        return ContextUtils.estimateTokens(message == null ? "" : message.getContent());
    }

    ShrinkResult shrinkRoundToBudget(List<BaseMessage> messages, RoundRange roundRange, SessionModelContext context,
                                     Map<String, Object> kwargs) {
        boolean changed = false;
        List<Integer> modifiedIndices = new ArrayList<>();
        while (roundToolResultSize(messages, roundRange.startIndex(), roundRange.endIndex(), context)
                > budgetConfig.getTokensThreshold()) {
            List<Candidate> candidates = collectRoundCandidates(messages, roundRange.startIndex(),
                    roundRange.endIndex(), context);
            if (candidates.isEmpty()) {
                break;
            }
            candidates.sort(Comparator.comparingInt(Candidate::size).reversed());
            int targetIndex = candidates.get(0).index();
            BaseMessage offloaded = offloadToolMessage((ToolMessage) messages.get(targetIndex), context, kwargs)
                    .toCompletableFuture().join();
            messages.set(targetIndex, offloaded);
            modifiedIndices.add(targetIndex);
            changed = true;
        }
        return new ShrinkResult(changed, modifiedIndices);
    }

    List<Candidate> collectRoundCandidates(List<BaseMessage> messages, int startIndex, int endIndex,
                                           SessionModelContext context) {
        List<Candidate> candidates = new ArrayList<>();
        List<BaseMessage> safeMessages = messages == null ? List.of() : messages;
        for (int index = startIndex; index <= endIndex && index < safeMessages.size(); index++) {
            BaseMessage message = safeMessages.get(index);
            if (shouldOffloadMessage(message, safeMessages, context) && message instanceof ToolMessage toolMessage) {
                candidates.add(new Candidate(index, messageSize(toolMessage, context)));
            }
        }
        return candidates;
    }

    @Override
    boolean shouldOffloadMessage(BaseMessage message, List<BaseMessage> contextMessages, SessionModelContext context) {
        if (!(message instanceof ToolMessage toolMessage)) {
            return false;
        }
        if (message instanceof OffloadMessage) {
            return false;
        }
        if (!(message.getContent() instanceof String)) {
            return false;
        }
        if (isAllowlistedToolMessage(message, contextMessages)) {
            return false;
        }
        return messageSize(toolMessage, context) > budgetConfig.getLargeMessageThreshold();
    }

    boolean isAllowlistedToolMessage(BaseMessage message, List<BaseMessage> contextMessages) {
        Set<String> allowlist = new LinkedHashSet<>(budgetConfig.getToolNameAllowlist() == null
                ? List.of()
                : budgetConfig.getToolNameAllowlist());
        if (allowlist.isEmpty()) {
            return false;
        }
        Optional<String> toolName = resolveToolNameFromMessage(message, contextMessages);
        return toolName.filter(allowlist::contains).isPresent();
    }

    CompletionStage<BaseMessage> offloadToolMessage(ToolMessage message, SessionModelContext context,
                                                    Map<String, Object> kwargs) {
        Object content = message.getContent();
        if (!(content instanceof String text)) {
            return CompletableFuture.completedFuture(message);
        }

        OffloadTarget target = newBudgetOffloadHandleAndPath(context);
        String preview = text.substring(0, Math.min(budgetConfig.getTrimSize(), text.length()));
        boolean hasMore = text.length() > budgetConfig.getTrimSize();
        String pendingContent = buildPersistedOutputMessage(text.length(), "pending", preview, hasMore);

        Map<String, Object> extraFields = new LinkedHashMap<>(message.modelDump());
        extraFields.remove("role");
        extraFields.remove("content");
        extraFields.remove("offload_type");
        extraFields.remove("offload_handle");
        if (message.getMetadata() != null) {
            extraFields.put("metadata", new LinkedHashMap<>(message.getMetadata()));
        }
        if (kwargs != null) {
            extraFields.putAll(kwargs);
        }

        return offloadMessages(
                message.getRole(),
                pendingContent,
                List.of(message),
                context,
                target.offloadHandle(),
                "filesystem",
                target.offloadPath(),
                extraFields
        ).thenApply(rawMessage -> wrapBudgetOffloadMessage(message, rawMessage, target, text.length(), preview,
                hasMore, extraFields));
    }

    private BaseMessage wrapBudgetOffloadMessage(ToolMessage source, BaseMessage rawMessage, OffloadTarget target,
                                                 int originalSize, String preview, boolean hasMore,
                                                 Map<String, Object> extraFields) {
        if (rawMessage == null) {
            return source;
        }
        Map<String, Object> metadata = rawMessage.getMetadata() == null ? Map.of() : rawMessage.getMetadata();
        String handle = stringValue(metadata.get("offload_handle"), target.offloadHandle());
        String type = stringValue(metadata.get("offload_type"), "in_memory");
        String marker = "[[OFFLOAD: handle=" + handle + ", type=" + type + ", path="
                + pathText(target.offloadPath()) + "]]";
        String persistedContent = buildPersistedOutputMessage(originalSize, marker, preview, hasMore);
        Map<String, Object> safeFields = new LinkedHashMap<>(extraFields == null ? Map.of() : extraFields);
        safeFields.putIfAbsent("tool_call_id", source.getToolCallId());
        return OffloadMessages.createOffloadMessage(source.getRole(), persistedContent, handle, type, safeFields);
    }

    String buildPersistedOutputMessage(int originalSize, String offloadHandle, String preview, boolean hasMore) {
        String suffix = hasMore ? "\n...\n" : "\n";
        return PERSISTED_OUTPUT_TAG + "\n"
                + "Output too large (" + originalSize + " bytes)."
                + "\n" + offloadHandle + "\n"
                + "Preview (first " + preview.length() + " chars):\n"
                + preview + suffix
                + PERSISTED_OUTPUT_CLOSING_TAG;
    }

    private OffloadTarget newBudgetOffloadHandleAndPath(SessionModelContext context) {
        OffloadTarget target = newOffloadHandleAndPath(context);
        if (context == null || context.workspaceDir() == null || context.workspaceDir().isBlank()) {
            return target;
        }
        String prefix = budgetConfig.getOffloadFilePrefix();
        if (prefix == null || prefix.isBlank() || prefix.equals(processorType())) {
            return target;
        }
        String fileName = prefix + "_" + target.offloadHandle() + ".json";
        return new OffloadTarget(target.offloadHandle(),
                java.nio.file.Path.of(context.workspaceDir(), "context", context.sessionId() + "_context",
                        "offload", fileName).toString());
    }

    private static List<Integer> sortedDistinct(List<Integer> values) {
        return values.stream().distinct().sorted().toList();
    }

    private static String pathText(String path) {
        return path == null ? "None" : path;
    }

    private static String stringValue(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private static ToolResultBudgetProcessorConfig asConfig(Object config) {
        if (config == null) {
            return new ToolResultBudgetProcessorConfig();
        }
        if (config instanceof ToolResultBudgetProcessorConfig budgetProcessorConfig) {
            return budgetProcessorConfig;
        }
        if (config instanceof com.openjiuwen.core.context.processor.offloader.ToolResultBudgetProcessorConfig legacyConfig) {
            ToolResultBudgetProcessorConfig newConfig = new ToolResultBudgetProcessorConfig();
            newConfig.setTokensThreshold(legacyConfig.getTokensThreshold());
            newConfig.setLargeMessageThreshold(legacyConfig.getLargeMessageThreshold());
            newConfig.setTrimSize(legacyConfig.getTrimSize());
            newConfig.setOffloadFilePrefix(legacyConfig.getOffloadFilePrefix());
            newConfig.setToolNameAllowlist(legacyConfig.getToolNameAllowlist());
            return newConfig;
        }
        throw new IllegalArgumentException("ToolResultBudgetProcessor requires ToolResultBudgetProcessorConfig");
    }

    private static MessageOffloaderConfig toOffloaderConfig(ToolResultBudgetProcessorConfig config) {
        ToolResultBudgetProcessorConfig safeConfig = config == null ? new ToolResultBudgetProcessorConfig() : config;
        MessageOffloaderConfig offloaderConfig = new MessageOffloaderConfig();
        offloaderConfig.setTokensThreshold(safeConfig.getTokensThreshold());
        offloaderConfig.setLargeMessageThreshold(Math.max(2, safeConfig.getLargeMessageThreshold()));
        offloaderConfig.setTrimSize(1);
        offloaderConfig.setOffloadMessageType(safeConfig.getOffloadMessageType());
        offloaderConfig.setKeepLastRound(false);
        return offloaderConfig;
    }

    record RoundRange(int startIndex, int endIndex) {
    }

    record Candidate(int index, int size) {
    }

    record ShrinkResult(boolean changed, List<Integer> modifiedIndices) {
        ShrinkResult {
            modifiedIndices = modifiedIndices == null ? List.of() : new ArrayList<>(modifiedIndices);
        }
    }
}
