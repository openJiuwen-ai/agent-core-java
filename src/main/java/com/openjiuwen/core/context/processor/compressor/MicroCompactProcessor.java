/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.processor.compressor;

import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.context.context.ContextUtils;
import com.openjiuwen.core.context.processor.ContextEvent;
import com.openjiuwen.core.context.processor.ContextProcessor;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;

import java.util.*;

/**
 * Clear stale tool results while keeping recent ones per tool.
 * <p>
 * When triggered, this processor identifies tool messages from compactable tools
 * that exceed the configured threshold, and replaces their content with a cleared
 * marker while keeping the most recent N results per tool.
 * <p>
 * Mirrors Python's {@code MicroCompactProcessor} from
 * {@code context_engine/processor/compressor/micro_compact_processor.py}.
 */
public class MicroCompactProcessor extends ContextProcessor {

    public MicroCompactProcessor(MicroCompactProcessorConfig config) {
        super(config);
    }

    private MicroCompactProcessorConfig cfg() {
        return getConfig();
    }

    // ------------------------------------------------------------------
    // Trigger
    // ------------------------------------------------------------------

    @Override
    public boolean triggerAddMessages(ModelContext context, List<BaseMessage> messagesToAdd) {
        List<BaseMessage> allMessages = new ArrayList<>(context.getMessages());
        allMessages.addAll(messagesToAdd);
        if (!isApiRound(allMessages)) {
            return false;
        }
        return hasAnyToolExceedThreshold(allMessages);
    }

    // ------------------------------------------------------------------
    // On-add processing
    // ------------------------------------------------------------------

    @Override
    public ProcessResult onAddMessages(ModelContext context, List<BaseMessage> messagesToAdd) {
        List<BaseMessage> allMessages = new ArrayList<>(context.getMessages());
        allMessages.addAll(messagesToAdd);

        List<Integer> indicesToClear = collectFlatIndicesForCompact(allMessages, false);

        if (indicesToClear.isEmpty()) {
            return ProcessResult.ofMessages(null, messagesToAdd);
        }

        List<Integer> modifiedIndices = new ArrayList<>();
        for (int index : indicesToClear) {
            BaseMessage message = allMessages.get(index);
            if (!(message instanceof ToolMessage)) {
                continue;
            }
            String content = message.getContentAsString();
            if (cfg().getClearedMarker().equals(content)) {
                continue;
            }
            // Replace with cleared marker — create a new ToolMessage to preserve fields
            ToolMessage cleared = new ToolMessage(
                    cfg().getClearedMarker(),
                    ((ToolMessage) message).getToolCallId(),
                    message.getName());
            allMessages.set(index, cleared);
            modifiedIndices.add(index);
        }

        context.setMessages(allMessages);
        ContextEvent event = ContextEvent.builder()
                .eventType(processorType())
                .messagesToModify(modifiedIndices)
                .build();
        return ProcessResult.ofMessages(event, Collections.emptyList());
    }

    // ------------------------------------------------------------------
    // State (stateless)
    // ------------------------------------------------------------------

    @Override
    public void loadState(Map<String, Object> state) {
        // stateless
    }

    @Override
    public Map<String, Object> saveState() {
        return new HashMap<>();
    }

    // ==================== Private helpers ====================

    /**
     * Group compactable tool-message indices by tool name.
     */
    private Map<String, List<Integer>> collectCompactableIndicesByTool(List<BaseMessage> messages) {
        Set<String> allowedNames = new HashSet<>(cfg().getCompactableToolNames());
        Map<String, List<Integer>> result = new LinkedHashMap<>();

        for (int index = 0; index < messages.size(); index++) {
            BaseMessage message = messages.get(index);
            if (!(message instanceof ToolMessage)) {
                continue;
            }
            String content = message.getContentAsString();
            if (cfg().getClearedMarker().equals(content)) {
                continue;
            }
            String toolName = ContextUtils.resolveToolNameFromMessage(message, messages);
            if (toolName != null && allowedNames.contains(toolName)) {
                result.computeIfAbsent(toolName, k -> new ArrayList<>()).add(index);
            }
        }
        return result;
    }

    /**
     * Check if any compactable tool exceeds the trigger threshold.
     */
    private boolean hasAnyToolExceedThreshold(List<BaseMessage> messages) {
        Map<String, List<Integer>> grouped = collectCompactableIndicesByTool(messages);
        for (List<Integer> indices : grouped.values()) {
            if (indices.size() > cfg().getTriggerThreshold() + cfg().getKeepRecentPerTool()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Collect flat list of indices to clear.
     */
    private List<Integer> collectFlatIndicesForCompact(List<BaseMessage> messages, boolean force) {
        Map<String, List<Integer>> grouped = collectCompactableIndicesByTool(messages);
        List<Integer> result = new ArrayList<>();
        for (List<Integer> indices : grouped.values()) {
            int threshold = force
                    ? cfg().getKeepRecentPerTool()
                    : cfg().getTriggerThreshold() + cfg().getKeepRecentPerTool();
            if (indices.size() > threshold) {
                int keep = cfg().getKeepRecentPerTool();
                if (keep > 0) {
                    result.addAll(indices.subList(0, indices.size() - keep));
                } else {
                    result.addAll(indices);
                }
            }
        }
        return result;
    }

    /**
     * Check if the message list ends at a completed API round.
     * <p>
     * A completed API round ends with an assistant message that has no
     * pending tool calls (i.e. all tool calls have been answered).
     * <p>
     * Mirrors Python's {@code ContextProcessor._api_round}.
     */
    private static boolean isApiRound(List<BaseMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return false;
        }
        // Walk backwards to find the last completed round boundary
        Set<String> pendingToolCallIds = null;
        for (int i = messages.size() - 1; i >= 0; i--) {
            BaseMessage msg = messages.get(i);
            if (msg instanceof AssistantMessage assistant) {
                if (assistant.getToolCalls() != null && !assistant.getToolCalls().isEmpty()) {
                    // This assistant has tool calls — we need all responses
                    if (pendingToolCallIds == null) {
                        // Tool calls at the end without responses → not a completed round
                        return false;
                    }
                    // Check if all tool calls are answered
                    for (Object tc : assistant.getToolCalls()) {
                        String tcId = extractId(tc);
                        if (tcId != null && pendingToolCallIds.contains(tcId)) {
                            pendingToolCallIds.remove(tcId);
                        }
                    }
                    if (pendingToolCallIds.isEmpty()) {
                        return i == 0; // round starts from the beginning
                    }
                    continue;
                }
                // Assistant without tool calls — completed round
                return true;
            }
            if (msg instanceof ToolMessage toolMsg && pendingToolCallIds == null) {
                // Tool response at the end without being consumed by a round check yet
                pendingToolCallIds = new HashSet<>();
                String tcId = toolMsg.getToolCallId();
                if (tcId != null && !tcId.isEmpty()) {
                    pendingToolCallIds.add(tcId);
                }
                continue;
            }
            if (msg instanceof ToolMessage toolMsg && pendingToolCallIds != null) {
                String tcId = toolMsg.getToolCallId();
                if (tcId != null) {
                    pendingToolCallIds.add(tcId);
                }
            }
        }
        return false;
    }

    private static String extractId(Object toolCall) {
        if (toolCall instanceof Map<?, ?> map) {
            Object id = map.get("id");
            return id instanceof String s ? s : null;
        }
        try {
            return (String) toolCall.getClass().getField("id").get(toolCall);
        } catch (Exception e) {
            return null;
        }
    }
}
