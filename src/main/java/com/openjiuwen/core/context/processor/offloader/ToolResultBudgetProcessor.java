/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.processor.offloader;

import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.context.context.ContextUtils;
import com.openjiuwen.core.context.processor.ContextEvent;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Offload oversized tool results round-by-round until each round fits budget.
 * 
 * @since 0.1.7
 */
public class ToolResultBudgetProcessor extends MessageOffloader {
    /**
     * PERSISTED_OUTPUT_TAG.
     * 
     * @since 0.1.7
     */
    public static final String PERSISTED_OUTPUT_TAG = "<persisted-output>";

    /**
     * PERSISTED_OUTPUT_CLOSING_TAG.
     * 
     * @since 0.1.7
     */
    public static final String PERSISTED_OUTPUT_CLOSING_TAG = "</persisted-output>";

    private final ToolResultBudgetProcessorConfig config;

    /**
     * ToolResultBudgetProcessor.
     * 
     * @param config config
     * @since 0.1.7
     */
    public ToolResultBudgetProcessor(ToolResultBudgetProcessorConfig config) {
        super(MessageOffloaderConfig.builder().messagesThreshold(config != null ? config.getMessagesThreshold() : null)
                .messagesToKeep(config != null ? config.getMessagesToKeep() : null)
                .tokensThreshold(config != null ? config.getTokensThreshold() : 50000)
                .largeMessageThreshold(config != null ? config.getLargeMessageThreshold() : 10000)
                .trimSize(config != null ? config.getTrimSize() : 3000).offloadMessageType(List.of("tool"))
                .keepLastRound(false).build());
        this.config = config != null ? config : ToolResultBudgetProcessorConfig.builder().build();
        this.config.validate();
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
        allMessages.addAll(messagesToAdd);
        return !roundsExceedingBudget(allMessages, context).isEmpty();
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
        List<BaseMessage> contextMessages = new ArrayList<>(context.getMessages());
        contextMessages.addAll(messagesToAdd);
        int contextSize = context.size();
        List<BaseMessage> updatedMessages = new ArrayList<>(contextMessages);
        List<Integer> modifiedIndices = new ArrayList<>();

        for (int[] roundRange : iterRoundRanges(updatedMessages)) {
            List<Integer> roundModified = shrinkRoundToBudget(updatedMessages, roundRange[0], roundRange[1], context);
            modifiedIndices.addAll(roundModified);
        }

        if (modifiedIndices.isEmpty()) {
            return ProcessResult.ofMessages(null, messagesToAdd);
        }

        context.setMessages(new ArrayList<>(updatedMessages.subList(0, contextSize)));
        ContextEvent event = ContextEvent.builder().eventType(processorType())
                .messagesToModify(modifiedIndices.stream().distinct().sorted().toList()).build();
        return ProcessResult.ofMessages(event,
                new ArrayList<>(updatedMessages.subList(contextSize, updatedMessages.size())));
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
     * getToolResultConfig.
     * 
     * @return the result
     * @since 0.1.7
     */
    public ToolResultBudgetProcessorConfig getToolResultConfig() {
        return config;
    }

    boolean isAlreadyOffloaded(ToolMessage message) {
        return message instanceof com.openjiuwen.core.context.schema.OffloadMessages.OffloadToolMessage;
    }

    /**
     * shouldOffloadMessage.
     * 
     * @param message message
     * @param contextMessages contextMessages
     * @param context context
     * @return the result
     * @since 0.1.7
     */
    @Override
    protected boolean shouldOffloadMessage(BaseMessage message, List<BaseMessage> contextMessages,
            ModelContext context) {
        if (!(message instanceof ToolMessage toolMessage)) {
            return false;
        }
        if (isAlreadyOffloaded(toolMessage)) {
            return false;
        }
        if (!(toolMessage.getContent() instanceof String)) {
            return false;
        }
        String toolName = ContextUtils.resolveToolNameFromMessage(message, contextMessages);
        if (toolName != null && config.getToolNameAllowlist() != null
                && config.getToolNameAllowlist().contains(toolName)) {
            return false;
        }
        return messageSize(toolMessage, context) > config.getLargeMessageThreshold();
    }

    /**
     * roundsExceedingBudget.
     * 
     * @param messages messages
     * @param context context
     * @return the result
     * @since 0.1.7
     */
    private List<int[]> roundsExceedingBudget(List<BaseMessage> messages, ModelContext context) {
        List<int[]> exceeded = new ArrayList<>();
        for (int[] range : iterRoundRanges(messages)) {
            if (roundToolResultSize(messages, range[0], range[1], context) > config.getTokensThreshold()) {
                List<int[]> candidates = collectRoundCandidates(messages, range[0], range[1], context);
                if (!candidates.isEmpty()) {
                    exceeded.add(range);
                }
            }
        }
        return exceeded;
    }

    /**
     * iterRoundRanges.
     * 
     * @param messages messages
     * @return the result
     * @since 0.1.7
     */
    private List<int[]> iterRoundRanges(List<BaseMessage> messages) {
        List<int[]> rounds = ContextUtils.findAllDialogueRound(messages);
        List<int[]> ranges = new ArrayList<>();
        for (int i = rounds.size() - 1; i >= 0; i--) {
            int[] round = rounds.get(i);
            int startIdx = round[0];
            int endIdx = round[1] >= 0 ? round[1] : messages.size() - 1;
            if (startIdx >= 0 && startIdx <= endIdx) {
                ranges.add(new int[]{startIdx, endIdx});
            }
        }
        return ranges;
    }

    /**
     * roundToolResultSize.
     * 
     * @param messages messages
     * @param startIdx startIdx
     * @param endIdx endIdx
     * @param context context
     * @return the result
     * @since 0.1.7
     */
    private int roundToolResultSize(List<BaseMessage> messages, int startIdx, int endIdx, ModelContext context) {
        int size = 0;
        for (int idx = startIdx; idx <= endIdx; idx++) {
            BaseMessage message = messages.get(idx);
            if (message instanceof ToolMessage toolMessage) {
                size += messageSize(toolMessage, context);
            }
        }
        return size;
    }

    /**
     * messageSize.
     * 
     * @param message message
     * @param context context
     * @return the result
     * @since 0.1.7
     */
    private int messageSize(ToolMessage message, ModelContext context) {
        if (context.tokenCounter() != null) {
            try {
                return context.tokenCounter().countMessages(List.of(message));
            } catch (IllegalArgumentException | IllegalStateException ignored) {
                return ContextUtils.estimateMessageTokens(message);
            }
        }
        return ContextUtils.estimateMessageTokens(message);
    }

    /**
     * shrinkRoundToBudget.
     * 
     * @param messages messages
     * @param startIdx startIdx
     * @param endIdx endIdx
     * @param context context
     * @return the result
     * @since 0.1.7
     */
    private List<Integer> shrinkRoundToBudget(List<BaseMessage> messages, int startIdx, int endIdx,
            ModelContext context) {
        List<Integer> modifiedIndices = new ArrayList<>();
        while (roundToolResultSize(messages, startIdx, endIdx, context) > config.getTokensThreshold()) {
            List<int[]> candidates = collectRoundCandidates(messages, startIdx, endIdx, context);
            if (candidates.isEmpty()) {
                break;
            }
            candidates.sort(Comparator.comparingInt((int[] item) -> item[1]).reversed());
            int targetIdx = candidates.get(0)[0];
            if (messages.get(targetIdx) instanceof ToolMessage tm) {
                BaseMessage offloaded = offloadToolMessage(tm, context);
                messages.set(targetIdx, offloaded);
                modifiedIndices.add(targetIdx);
            }
        }
        return modifiedIndices;
    }

    /**
     * collectRoundCandidates.
     * 
     * @param messages messages
     * @param startIdx startIdx
     * @param endIdx endIdx
     * @param context context
     * @return the result
     * @since 0.1.7
     */
    private List<int[]> collectRoundCandidates(List<BaseMessage> messages, int startIdx, int endIdx,
            ModelContext context) {
        List<int[]> candidates = new ArrayList<>();
        for (int idx = startIdx; idx <= endIdx; idx++) {
            BaseMessage message = messages.get(idx);
            if (shouldOffloadMessage(message, messages, context) && message instanceof ToolMessage toolMessage) {
                candidates.add(new int[]{idx, messageSize(toolMessage, context)});
            }
        }
        return candidates;
    }

    /**
     * offloadToolMessage.
     * 
     * @param message message
     * @param context context
     * @return the result
     * @since 0.1.7
     */
    private BaseMessage offloadToolMessage(ToolMessage message, ModelContext context) {
        String content = message.getContentAsString();
        String offloadHandle = UUID.randomUUID().toString().replace("-", "");
        String offloadPath = null;
        if (context.workspaceDir() != null && !context.workspaceDir().isBlank()) {
            String fileName = config.getOffloadFilePrefix() + "_" + offloadHandle + ".json";
            offloadPath = java.nio.file.Path
                    .of(context.workspaceDir(), "context", context.sessionId() + "_context", "offload", fileName)
                    .toString();
        }

        String preview = content.substring(0, Math.min(content.length(), config.getTrimSize()));
        boolean hasMore = content.length() > config.getTrimSize();
        String persistedContent = buildPersistedOutputMessage(content.length(), "pending", preview, hasMore);

        BaseMessage offloadMessage = offloadMessages("tool", persistedContent, List.of(message), context, offloadHandle,
                offloadPath != null ? "filesystem" : "in_memory", offloadPath,
                Map.of("tool_call_id", message.getToolCallId(), "name", message.getName()));
        if (offloadMessage instanceof ToolMessage toolOffloadMessage) {
            String actualHandle = offloadMessage instanceof com.openjiuwen.core.context.schema.OffloadMixin mixin
                    ? mixin.getOffloadHandle()
                    : "unknown";
            String actualType = offloadMessage instanceof com.openjiuwen.core.context.schema.OffloadMixin mixin
                    ? mixin.getOffloadType()
                    : "unknown";
            toolOffloadMessage.setContent(buildPersistedOutputMessage(content.length(),
                    "[[OFFLOAD: handle=" + actualHandle + ", type=" + actualType + ", path=" + offloadPath + "]]",
                    preview, hasMore));
            return toolOffloadMessage;
        }
        return message;
    }

    /**
     * buildPersistedOutputMessage.
     * 
     * @param originalSize originalSize
     * @param offloadHandle offloadHandle
     * @param preview preview
     * @param hasMore hasMore
     * @return the result
     * @since 0.1.7
     */
    private String buildPersistedOutputMessage(int originalSize, String offloadHandle, String preview,
            boolean hasMore) {
        String suffix = hasMore ? "\n...\n" : "\n";
        return PERSISTED_OUTPUT_TAG + "\n" + "Output too large (" + originalSize + " bytes)." + "\n" + offloadHandle
                + "\n" + "Preview (first " + preview.length() + " chars):\n" + preview + suffix
                + PERSISTED_OUTPUT_CLOSING_TAG;
    }
}
