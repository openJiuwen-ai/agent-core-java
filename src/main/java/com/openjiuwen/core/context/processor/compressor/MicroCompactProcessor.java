/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.processor.compressor;

import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.context.context.ContextUtils;
import com.openjiuwen.core.context.context.SessionMemoryManager;
import com.openjiuwen.core.context.processor.ContextEvent;
import com.openjiuwen.core.context.processor.ContextProcessor;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Clear stale tool results while keeping recent ones per tool.
 * 
 * @since 0.1.7
 */
public class MicroCompactProcessor extends ContextProcessor {
    /**
     * MicroCompactProcessor.
     * 
     * @param config config
     * @since 0.1.7
     */
    public MicroCompactProcessor(MicroCompactProcessorConfig config) {
        super(config);
        config.validate();
    }

    /**
     * triggerAddMessages.
     * 
     * @param context context
     * @param messagesToAdd messagesToAdd
     * @return the result
     * @since 0.1.7
     */
    @Override
    public boolean triggerAddMessages(ModelContext context, List<BaseMessage> messagesToAdd) {
        List<BaseMessage> allMessages = new ArrayList<>(context.getMessages());
        if (messagesToAdd != null) {
            allMessages.addAll(messagesToAdd);
        }
        if (!apiRound(allMessages)) {
            return false;
        }
        return hasAnyToolExceedThreshold(allMessages);
    }

    /**
     * onAddMessages.
     * 
     * @param context context
     * @param messagesToAdd messagesToAdd
     * @return the result
     * @since 0.1.7
     */
    @Override
    public ProcessResult onAddMessages(ModelContext context, List<BaseMessage> messagesToAdd) {
        MicroCompactProcessorConfig config = getConfig();
        List<BaseMessage> allMessages = new ArrayList<>(context.getMessages());
        if (messagesToAdd != null) {
            allMessages.addAll(messagesToAdd);
        }

        List<Integer> indicesToClear = collectFlatIndicesForCompact(allMessages, false);
        if (indicesToClear.isEmpty()) {
            return ProcessResult.ofMessages(null, messagesToAdd);
        }

        List<BaseMessage> updatedMessages = new ArrayList<>(allMessages);
        List<Integer> modifiedIndices = new ArrayList<>();
        String clearedMarker = config.getClearedMarker();
        for (Integer index : indicesToClear) {
            BaseMessage message = updatedMessages.get(index);
            if (!(message instanceof ToolMessage toolMessage)) {
                continue;
            }
            if (clearedMarker.equals(toolMessage.getContentAsString())) {
                continue;
            }
            ToolMessage cleared = new ToolMessage();
            cleared.setRole(toolMessage.getRole());
            cleared.setName(toolMessage.getName());
            cleared.setToolCallId(toolMessage.getToolCallId());
            cleared.setMetadata(toolMessage.getMetadata() == null
                    ? new LinkedHashMap<>()
                    : new LinkedHashMap<>(toolMessage.getMetadata()));
            cleared.setContent(clearedMarker);
            updatedMessages.set(index, cleared);
            modifiedIndices.add(index);
        }

        context.setMessages(updatedMessages);
        return ProcessResult.ofMessages(
                ContextEvent.builder().eventType(processorType()).messagesToModify(modifiedIndices).build(), List.of());
    }

    /**
     * loadState.
     * 
     * @param state state
     * @since 0.1.7
     */
    @Override
    public void loadState(Map<String, Object> state) {
        // stateless
    }

    /**
     * saveState.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Map<String, Object> saveState() {
        return Map.of();
    }

    /**
     * hasAnyToolExceedThreshold.
     * 
     * @param messages messages
     * @return the result
     * @since 0.1.7
     */
    private boolean hasAnyToolExceedThreshold(List<BaseMessage> messages) {
        MicroCompactProcessorConfig config = getConfig();
        Map<String, List<Integer>> grouped = collectCompactableIndicesByTool(messages);
        return grouped.values().stream()
                .anyMatch(indices -> indices.size() > config.getTriggerThreshold() + config.getKeepRecentPerTool());
    }

    /**
     * collectFlatIndicesForCompact.
     * 
     * @param messages messages
     * @param isForce isForce
     * @return the result
     * @since 0.1.7
     */
    private List<Integer> collectFlatIndicesForCompact(List<BaseMessage> messages, boolean isForce) {
        MicroCompactProcessorConfig config = getConfig();
        Map<String, List<Integer>> grouped = collectCompactableIndicesByTool(messages);
        List<Integer> result = new ArrayList<>();
        for (List<Integer> indices : grouped.values()) {
            int threshold =
                isForce ? config.getKeepRecentPerTool() : config.getTriggerThreshold() + config.getKeepRecentPerTool();
            if (indices.size() > threshold) {
                if (config.getKeepRecentPerTool() > 0) {
                    result.addAll(indices.subList(0, indices.size() - config.getKeepRecentPerTool()));
                } else {
                    result.addAll(indices);
                }
            }
        }
        return result;
    }

    /**
     * collectCompactableIndicesByTool.
     * 
     * @param messages messages
     * @return the result
     * @since 0.1.7
     */
    private Map<String, List<Integer>> collectCompactableIndicesByTool(List<BaseMessage> messages) {
        MicroCompactProcessorConfig config = getConfig();
        Map<String, List<Integer>> result = new LinkedHashMap<>();
        for (int index = 0; index < messages.size(); index++) {
            BaseMessage message = messages.get(index);
            if (!(message instanceof ToolMessage toolMessage)) {
                continue;
            }
            if (config.getClearedMarker().equals(toolMessage.getContentAsString())) {
                continue;
            }
            String toolName = ContextUtils.resolveToolNameFromMessage(toolMessage, messages);
            if (toolName != null && config.getCompactableToolNames().contains(toolName)) {
                result.computeIfAbsent(toolName, key -> new ArrayList<>()).add(index);
            }
        }
        return result;
    }

    /**
     * apiRound.
     * 
     * @param messages messages
     * @return the result
     * @since 0.1.7
     */
    private static boolean apiRound(List<BaseMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return false;
        }
        List<int[]> rounds = SessionMemoryManager.groupCompletedApiRounds(messages);
        if (rounds.isEmpty()) {
            return false;
        }
        int[] last = rounds.get(rounds.size() - 1);
        return last.length > 1 && last[1] == messages.size();
    }
}
