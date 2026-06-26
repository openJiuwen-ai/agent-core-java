/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context_engine.processor.compressor;

import com.openjiuwen.core.context_engine.ContextEngine;
import com.openjiuwen.core.context_engine.context.ContextUtils;
import com.openjiuwen.core.context_engine.context.SessionModelContext;
import com.openjiuwen.core.context_engine.processor.ContextEvent;
import com.openjiuwen.core.context_engine.processor.ContextProcessor;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Clears stale compactable tool results while keeping each tool's recent tail.
 *
 * <p>Mirrors Python's {@code MicroCompactProcessor} in
 * {@code openjiuwen/core/context_engine/processor/compressor/micro_compact_processor.py}.</p>
 */
public class MicroCompactProcessor extends ContextProcessor {
    public static final String MICRO_COMPACT_CLEARED_MARKER =
            MicroCompactProcessorConfig.DEFAULT_CLEARED_MARKER;

    static {
        ContextEngine.registerProcessor("MicroCompactProcessor", MicroCompactProcessor.class);
    }

    private final MicroCompactProcessorConfig config;

    public MicroCompactProcessor(Object config) {
        this(asConfig(config));
    }

    public MicroCompactProcessor(MicroCompactProcessorConfig config) {
        super(config == null ? new MicroCompactProcessorConfig() : config);
        this.config = config == null ? new MicroCompactProcessorConfig() : config;
    }

    public MicroCompactProcessorConfig getConfig() {
        return config;
    }

    @Override
    public CompletionStage<Boolean> triggerAddMessages(SessionModelContext context, List<BaseMessage> messages,
                                                       Map<String, Object> kwargs) {
        List<BaseMessage> allMessages = concatenate(context == null ? List.of() : context.getMessages(), messages);
        if (!apiRound(allMessages)) {
            return CompletableFuture.completedFuture(false);
        }
        return CompletableFuture.completedFuture(hasAnyToolExceedThreshold(allMessages));
    }

    @Override
    public CompletionStage<SessionModelContext.ProcessResult> onAddMessages(SessionModelContext context,
                                                                            List<BaseMessage> messages,
                                                                            boolean force,
                                                                            Map<String, Object> kwargs) {
        List<BaseMessage> incoming = messages == null ? List.of() : messages;
        List<BaseMessage> allMessages = concatenate(context == null ? List.of() : context.getMessages(), incoming);
        List<Integer> indicesToClear = collectFlatIndicesForCompact(allMessages, force || kwargsForce(kwargs));
        if (indicesToClear.isEmpty()) {
            return CompletableFuture.completedFuture(
                    new SessionModelContext.ProcessResult(null, incoming, null));
        }

        List<Integer> modifiedIndices = new ArrayList<>();
        for (Integer index : indicesToClear) {
            if (index == null || index < 0 || index >= allMessages.size()) {
                continue;
            }
            BaseMessage message = allMessages.get(index);
            if (!(message instanceof ToolMessage toolMessage)) {
                continue;
            }
            if (config.getClearedMarker().equals(message.getContentAsString())) {
                continue;
            }
            allMessages.set(index, copyClearedToolMessage(toolMessage));
            modifiedIndices.add(index);
        }

        if (modifiedIndices.isEmpty()) {
            return CompletableFuture.completedFuture(
                    new SessionModelContext.ProcessResult(null, incoming, null));
        }
        if (context != null) {
            context.setMessages(allMessages, true);
        }
        ContextEvent event = new ContextEvent(processorType(), modifiedIndices, "", null);
        return CompletableFuture.completedFuture(new SessionModelContext.ProcessResult(event, List.of(), null));
    }

    @Override
    public void loadState(Map<String, Object> state) {
        // Python implementation is stateless.
    }

    @Override
    public Map<String, Object> saveState() {
        return new LinkedHashMap<>();
    }

    Map<String, List<Integer>> collectCompactableIndicesByTool(List<BaseMessage> messages) {
        Set<String> allowedNames = new HashSet<>(config.getCompactableToolNames());
        Map<String, List<Integer>> result = new LinkedHashMap<>();
        List<BaseMessage> safeMessages = messages == null ? List.of() : messages;
        for (int index = 0; index < safeMessages.size(); index++) {
            BaseMessage message = safeMessages.get(index);
            if (!(message instanceof ToolMessage)) {
                continue;
            }
            if (config.getClearedMarker().equals(message.getContentAsString())) {
                continue;
            }
            String toolName = ContextUtils.resolveToolNameFromMessage(message, safeMessages).orElse(null);
            if (toolName != null && allowedNames.contains(toolName)) {
                result.computeIfAbsent(toolName, ignored -> new ArrayList<>()).add(index);
            }
        }
        return result;
    }

    boolean hasAnyToolExceedThreshold(List<BaseMessage> messages) {
        int threshold = config.getTriggerThreshold() + config.getKeepRecentPerTool();
        for (List<Integer> indices : collectCompactableIndicesByTool(messages).values()) {
            if (indices.size() > threshold) {
                return true;
            }
        }
        return false;
    }

    List<Integer> collectFlatIndicesForCompact(List<BaseMessage> messages, boolean force) {
        Map<String, List<Integer>> grouped = collectCompactableIndicesByTool(messages);
        List<Integer> result = new ArrayList<>();
        for (List<Integer> indices : grouped.values()) {
            int threshold = force
                    ? config.getKeepRecentPerTool()
                    : config.getTriggerThreshold() + config.getKeepRecentPerTool();
            if (indices.size() <= threshold) {
                continue;
            }
            int keep = config.getKeepRecentPerTool();
            if (keep > 0) {
                result.addAll(indices.subList(0, indices.size() - keep));
            } else {
                result.addAll(indices);
            }
        }
        return result;
    }

    private ToolMessage copyClearedToolMessage(ToolMessage source) {
        ToolMessage cleared = new ToolMessage(config.getClearedMarker(), source.getToolCallId(), source.getName());
        cleared.setMetadata(source.getMetadata() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(source.getMetadata()));
        return cleared;
    }

    private static List<BaseMessage> concatenate(List<BaseMessage> left, List<BaseMessage> right) {
        List<BaseMessage> result = new ArrayList<>(left == null ? List.of() : left);
        result.addAll(right == null ? List.of() : right);
        return result;
    }

    private static boolean kwargsForce(Map<String, Object> kwargs) {
        Object value = kwargs == null ? null : kwargs.get("force");
        return Boolean.TRUE.equals(value);
    }

    private static MicroCompactProcessorConfig asConfig(Object config) {
        if (config == null) {
            return new MicroCompactProcessorConfig();
        }
        if (config instanceof MicroCompactProcessorConfig microCompactConfig) {
            return microCompactConfig;
        }
        throw new IllegalArgumentException("MicroCompactProcessor requires MicroCompactProcessorConfig");
    }
}
