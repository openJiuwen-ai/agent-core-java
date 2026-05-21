/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.processor.offloader;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.context.context.ContextUtils;
import com.openjiuwen.core.context.processor.ContextEvent;
import com.openjiuwen.core.context.processor.ContextProcessor;
import com.openjiuwen.core.context.schema.OffloadMixin;
import com.openjiuwen.core.context.token.TokenCounter;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;

import java.util.*;

/**
 * Offload oversized tool results round-by-round until each round fits budget.
 * <p>
 * Unlike the base {@link MessageOffloader}, this processor does not use
 * message-count thresholds. Instead, it iterates over each dialogue round,
 * accumulates the token cost of tool-result messages, and offloads the largest
 * tool results until the round fits within the configured
 * {@link ToolResultBudgetProcessorConfig#getTokensThreshold()}.
 * <p>
 * Mirrors Python's {@code ToolResultBudgetProcessor} from
 * {@code context_engine/processor/offloader/tool_result_budget_processor.py}.
 */
public class ToolResultBudgetProcessor extends ContextProcessor {

    private static final String PERSISTED_OUTPUT_TAG = "<persisted-output>";
    private static final String PERSISTED_OUTPUT_CLOSING_TAG = "</persisted-output>";

    public ToolResultBudgetProcessor(ToolResultBudgetProcessorConfig config) {
        super(config);
    }

    /**
     * Typed config accessor.
     */
    private ToolResultBudgetProcessorConfig cfg() {
        return getConfig();
    }

    // ------------------------------------------------------------------
    // Trigger
    // ------------------------------------------------------------------

    @Override
    public boolean triggerAddMessages(ModelContext context, List<BaseMessage> messagesToAdd) {
        List<BaseMessage> allMessages = new ArrayList<>(context.getMessages());
        allMessages.addAll(messagesToAdd);
        return !roundBudgetExceeded(allMessages, context).isEmpty();
    }

    // ------------------------------------------------------------------
    // On-add processing
    // ------------------------------------------------------------------

    @Override
    public ProcessResult onAddMessages(ModelContext context, List<BaseMessage> messagesToAdd) {
        List<BaseMessage> contextMessages = new ArrayList<>(context.getMessages());
        contextMessages.addAll(messagesToAdd);
        int contextSize = context.size();
        List<BaseMessage> updatedMessages = new ArrayList<>(contextMessages);
        List<Integer> modifiedIndices = new ArrayList<>();

        for (int[] roundRange : iterRoundRanges(updatedMessages)) {
            int startIdx = roundRange[0];
            int endIdx = roundRange[1];
            int[] result = shrinkRoundToBudget(updatedMessages, startIdx, endIdx, context);
            if (result.length > 0) {
                for (int idx : result) {
                    modifiedIndices.add(idx);
                }
            }
        }

        if (modifiedIndices.isEmpty()) {
            return ProcessResult.ofMessages(null, messagesToAdd);
        }

        List<BaseMessage> newContextMessages = new ArrayList<>(updatedMessages.subList(0, contextSize));
        List<BaseMessage> newMessagesToAdd = new ArrayList<>(updatedMessages.subList(contextSize, updatedMessages.size()));
        context.setMessages(newContextMessages);

        Set<Integer> uniqueIndices = new TreeSet<>(modifiedIndices);
        ContextEvent event = ContextEvent.builder()
                .eventType(processorType())
                .messagesToModify(new ArrayList<>(uniqueIndices))
                .build();
        return ProcessResult.ofMessages(event, newMessagesToAdd);
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
     * Identify dialogue rounds whose accumulated tool-result tokens exceed the budget.
     */
    private List<int[]> roundBudgetExceeded(List<BaseMessage> messages, ModelContext context) {
        List<int[]> exceeded = new ArrayList<>();
        for (int[] range : iterRoundRanges(messages)) {
            int totalSize = roundToolResultSize(messages, range[0], range[1], context);
            if (totalSize > cfg().getTokensThreshold()) {
                List<int[]> candidates = collectRoundCandidates(messages, range[0], range[1], context);
                if (!candidates.isEmpty()) {
                    exceeded.add(range);
                }
            }
        }
        return exceeded;
    }

    /**
     * Iterate over all dialogue round ranges (user_idx, assistant_idx).
     * Rounds are iterated from oldest to newest.
     */
    private List<int[]> iterRoundRanges(List<BaseMessage> messages) {
        List<int[]> rounds = ContextUtils.findAllDialogueRound(messages);
        if (rounds.isEmpty()) {
            return Collections.emptyList();
        }
        // rounds come in reverse order from findAllDialogueRound; reverse to forward
        Collections.reverse(rounds);
        List<int[]> ranges = new ArrayList<>();
        for (int[] round : rounds) {
            int startIdx = round[0];
            int endIdx = round[1] == -1 ? messages.size() - 1 : round[1];
            if (startIdx < 0 || endIdx < 0 || startIdx > endIdx) {
                continue;
            }
            ranges.add(new int[]{startIdx, endIdx});
        }
        return ranges;
    }

    /**
     * Sum token sizes of all tool messages within [startIdx, endIdx].
     */
    private int roundToolResultSize(List<BaseMessage> messages, int startIdx, int endIdx, ModelContext context) {
        int size = 0;
        for (int idx = startIdx; idx <= endIdx; idx++) {
            BaseMessage msg = messages.get(idx);
            if (msg instanceof ToolMessage) {
                size += messageSize(msg, context);
            }
        }
        return size;
    }

    /**
     * Measure a message's token cost using the token counter, or fall back to
     * char-based estimation.
     */
    private int messageSize(BaseMessage message, ModelContext context) {
        TokenCounter tokenCounter = context.tokenCounter();
        if (tokenCounter != null) {
            try {
                return tokenCounter.countMessages(List.of(message));
            } catch (Exception e) {
                return estimateSize(message.getContentAsString());
            }
        }
        return estimateSize(message.getContentAsString());
    }

    private static int estimateSize(String content) {
        if (content == null) {
            return 0;
        }
        return ContextUtils.estimateTokens(content);
    }

    /**
     * Offload the largest tool results in a round until it fits the budget.
     *
     * @return array of modified indices, or empty array if no change
     */
    private int[] shrinkRoundToBudget(List<BaseMessage> messages, int startIdx, int endIdx, ModelContext context) {
        List<Integer> modifiedIndices = new ArrayList<>();
        boolean changed = false;

        while (roundToolResultSize(messages, startIdx, endIdx, context) > cfg().getTokensThreshold()) {
            List<int[]> candidates = collectRoundCandidates(messages, startIdx, endIdx, context);
            if (candidates.isEmpty()) {
                break;
            }
            // Sort descending by size
            candidates.sort((a, b) -> Integer.compare(b[1], a[1]));
            int targetIdx = candidates.get(0)[0];
            BaseMessage offloaded = offloadToolMessage(messages.get(targetIdx), context);
            messages.set(targetIdx, offloaded);
            modifiedIndices.add(targetIdx);
            changed = true;
        }

        if (!changed) {
            return new int[0];
        }
        return modifiedIndices.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * Collect (index, size) pairs for messages eligible for offload in the round.
     */
    private List<int[]> collectRoundCandidates(List<BaseMessage> messages, int startIdx, int endIdx, ModelContext context) {
        List<int[]> candidates = new ArrayList<>();
        for (int idx = startIdx; idx <= endIdx; idx++) {
            BaseMessage msg = messages.get(idx);
            if (shouldOffloadMessage(msg, messages, context)) {
                candidates.add(new int[]{idx, messageSize(msg, context)});
            }
        }
        return candidates;
    }

    /**
     * Determine whether a message is eligible for offloading.
     */
    private boolean shouldOffloadMessage(BaseMessage message, List<BaseMessage> contextMessages, ModelContext context) {
        if (!(message instanceof ToolMessage)) {
            return false;
        }
        if (message instanceof OffloadMixin) {
            return false;
        }
        String content = message.getContentAsString();
        if (content == null) {
            return false;
        }
        if (isAllowlistedToolMessage(message, contextMessages)) {
            return false;
        }
        return messageSize(message, context) > cfg().getLargeMessageThreshold();
    }

    /**
     * Check if the tool name is in the allowlist.
     */
    private boolean isAllowlistedToolMessage(BaseMessage message, List<BaseMessage> contextMessages) {
        List<String> allowlist = cfg().getToolNameAllowlist();
        if (allowlist == null || allowlist.isEmpty()) {
            return false;
        }
        Set<String> allowSet = new HashSet<>(allowlist);
        String toolName = ContextUtils.resolveToolNameFromMessage(message, contextMessages);
        return toolName != null && allowSet.contains(toolName);
    }

    /**
     * Offload a single tool message by trimming its content and storing the
     * original in the offload buffer.
     */
    private BaseMessage offloadToolMessage(BaseMessage message, ModelContext context) {
        String content = message.getContentAsString();
        if (content == null) {
            return message;
        }

        String offloadHandle = UUID.randomUUID().toString().replace("-", "");

        ToolResultBudgetProcessorConfig config = cfg();
        String preview = content.substring(0, Math.min(content.length(), config.getTrimSize()));
        boolean hasMore = content.length() > config.getTrimSize();

        String persistedContent = buildPersistedOutputMessage(
                content.length(), "pending", preview, hasMore);

        // Collect extra fields
        Map<String, Object> extraFields = new HashMap<>();
        if (message instanceof ToolMessage toolMsg) {
            if (toolMsg.getToolCallId() != null) {
                extraFields.put("tool_call_id", toolMsg.getToolCallId());
            }
            if (toolMsg.getName() != null) {
                extraFields.put("name", toolMsg.getName());
            }
        }

        BaseMessage offloadMsg = offloadMessages(
                "tool", persistedContent, List.of(message), context,
                offloadHandle, "in_memory", extraFields);

        if (offloadMsg != null) {
            String actualType = "in_memory";
            String finalContent = buildPersistedOutputMessage(
                    content.length(),
                    "[[OFFLOAD: handle=" + offloadHandle + ", type=" + actualType + "]]",
                    preview,
                    hasMore);
            offloadMsg.setContent(finalContent);
            return offloadMsg;
        }
        return message;
    }

    /**
     * Build the persisted-output placeholder string.
     */
    private static String buildPersistedOutputMessage(int originalSize, String offloadHandle,
                                                      String preview, boolean hasMore) {
        String suffix = hasMore ? "\n...\n" : "\n";
        return PERSISTED_OUTPUT_TAG + "\n"
                + "Output too large (" + originalSize + " bytes)."
                + "\n" + offloadHandle + "\n"
                + "Preview (first " + preview.length() + " chars):\n"
                + preview + suffix
                + PERSISTED_OUTPUT_CLOSING_TAG;
    }
}
