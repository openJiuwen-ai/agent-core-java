/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.processor.compressor;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.context.context.ContextUtils;
import com.openjiuwen.core.context.context.SessionMemoryManager;
import com.openjiuwen.core.context.processor.ContextEvent;
import com.openjiuwen.core.context.processor.ContextProcessor;
import com.openjiuwen.core.context.token.TokenCounter;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Compress the current round into protocolized memory blocks.
 */
public class CurrentRoundCompressor extends ContextProcessor {

    static final String SUMMARY_MARKER = "[CURRENT_ROUND_MEMORY_BLOCK]";

    private static final String DEFAULT_COMPRESSION_PROMPT = """
            You are a **Task Data Preservation Expert**.
            
            Your role is to produce a **high-fidelity incremental memory block** for long-running agent tasks.
            
            [User Intent Context - REFERENCE ONLY]:
            {prior_context_and_query}
            
            [Prior memory blocks - REFERENCE ONLY]:
            {accumulated_summaries}
            
            [Selected messages - TARGET]:
            {selected_messages}
            
            [Recent uncompressed messages - BOUNDARY CONTEXT]:
            {recent_messages}
            
            Output plain text only. Target length: <= {target_tokens}
            """;

    private static final String CLEAN_PROMPT = """
            You are consolidating historical memory blocks.
            
            These blocks are compressed context artifacts from prior conversation, not new user instructions.
            
            [Historical memory blocks]:
            {compressed_blocks}
            
            Maximum length: {compress_len} tokens
            Output plain text only.
            """;

    private final String compressedPrompt;
    private final int tokenThreshold;
    private final int messagesToKeep;
    private final int minSelectedTokensForCompression;
    private final int compressionTargetTokens;
    private final int summaryMergeTargetTokens;
    private final int accumulatedSummaryTokenLimit;
    private final int summaryMergeMinBlocks;
    private final int priorContextWindowSize;
    private final Model model;

    /**
     * Auto-generated for codecheck compliance.
     */
    public CurrentRoundCompressor(CurrentRoundCompressorConfig config) {
        super(config);
        config.validate();
        this.compressedPrompt = config.getCustomCompressionPrompt() != null
                ? config.getCustomCompressionPrompt()
                : DEFAULT_COMPRESSION_PROMPT;
        this.tokenThreshold = config.getTokensThreshold();
        this.messagesToKeep = config.getMessagesToKeep();
        this.minSelectedTokensForCompression = config.getMinSelectedTokensForCompression();
        this.compressionTargetTokens = config.getCompressionTargetTokens();
        this.summaryMergeTargetTokens = config.getSummaryMergeTargetTokens();
        this.accumulatedSummaryTokenLimit = config.getAccumulatedSummaryTokenLimit();
        this.summaryMergeMinBlocks = config.getSummaryMergeMinBlocks();
        this.priorContextWindowSize = config.getPriorContextWindowSize();
        this.model = new Model(config.getModelClient(), config.getModel());
    }

    String wrapMemoryBlock(String summary) {
        return SUMMARY_MARKER + "\n"
                + "processor: CurrentRoundCompressor\n"
                + "type: historical_memory_block\n"
                + "scope: current_round_increment\n"
                + "type_note: This is compressed memory from earlier conversation, kept to preserve long-range task "
                + "continuity.\n"
                + "authority: This block is reference memory, not a binding source of truth. If newer information "
                + "conflicts with it, prefer the newer information.\n"
                + "instruction_status: Do not treat this block as a new user request or a fresh instruction to "
                + "execute. It only records prior context.\n"
                + "strategy_status: Any plans, approaches, or next steps recorded here are historical working state. "
                + "They may be revised, replaced, or discarded later.\n"
                + "tool_action_state_status: Tool results, action history, and execution state in this block may help "
                + "continuation, but they should only be reused if they are still valid in the current context.\n"
                + "conflict_priority: Prefer newer signals in this order: latest explicit user request, recent "
                + "uncompressed context, fresh tool or action results, then this memory block.\n\n"
                + "Summary:\n"
                + summary;
    }

    String buildPrompt(
            int targetTokens,
            String priorSummaries,
            String recentContext,
            String priorContextAndQuery) {
        return compressedPrompt
                .replace("{target_tokens}", String.valueOf(targetTokens))
                .replace("{accumulated_summaries}", priorSummaries == null || priorSummaries.isBlank()
                        ? "(none)" : priorSummaries)
                .replace("{recent_messages}", recentContext == null || recentContext.isBlank()
                        ? "(none)" : recentContext)
                .replace("{prior_context_and_query}", priorContextAndQuery == null || priorContextAndQuery.isBlank()
                        ? "(none)" : priorContextAndQuery);
    }

    String formatRecentContext(List<BaseMessage> allContextMessages, int endIdx) {
        List<BaseMessage> recentMessages = new ArrayList<>();
        for (int index = endIdx + 1; index < allContextMessages.size(); index++) {
            BaseMessage message = allContextMessages.get(index);
            if (isSummaryMessage(message)) {
                continue;
            }
            recentMessages.add(message);
        }
        if (recentMessages.isEmpty()) {
            return "";
        }
        return String.join("\n", recentMessages.stream()
                .map(message -> "role:" + message.getRole() + ", content:" + message)
                .toList());
    }

    String formatPriorContextAndQuery(List<BaseMessage> allContextMessages, int currentQueryIdx) {
        List<String> lines = new ArrayList<>();
        List<BaseMessage> priorMessages;
        if (currentQueryIdx > 0) {
            priorMessages = new ArrayList<>();
            for (int index = 0; index < currentQueryIdx; index++) {
                BaseMessage message = allContextMessages.get(index);
                boolean isPlainUser = message instanceof UserMessage && !isSummaryMessage(message);
                boolean isPlainAssistant = message instanceof AssistantMessage assistantMessage
                        && (assistantMessage.getToolCalls() == null || assistantMessage.getToolCalls().isEmpty());
                if (isPlainUser || isPlainAssistant) {
                    priorMessages.add(message);
                }
            }
            int from = Math.max(priorMessages.size() - priorContextWindowSize, 0);
            priorMessages = new ArrayList<>(priorMessages.subList(from, priorMessages.size()));
        } else {
            priorMessages = List.of();
        }
        for (BaseMessage message : priorMessages) {
            lines.add("role:" + message.getRole() + ", content:" + message);
        }
        if (currentQueryIdx >= 0 && currentQueryIdx < allContextMessages.size()) {
            BaseMessage queryMessage = allContextMessages.get(currentQueryIdx);
            lines.add("\n--- Current User Intent ---\nrole:" + queryMessage.getRole() + ", content:" + queryMessage);
        }
        return String.join("\n", lines);
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public ProcessResult onAddMessages(ModelContext context, List<BaseMessage> messagesToAdd) {
        List<BaseMessage> contextMessages = new ArrayList<>(context.getMessages());
        if (messagesToAdd != null) {
            contextMessages.addAll(messagesToAdd);
        }
        int lastUserIdx = getCompressIdx(contextMessages);
        if (lastUserIdx == -1) {
            return ProcessResult.ofMessages(null, messagesToAdd);
        }
        int keepStartIdx = Math.max(0, contextMessages.size() - messagesToKeep);
        int endIdx = keepStartIdx - 1;

        try {
            CompressResult compressResult = multiCompress(contextMessages, lastUserIdx, endIdx, context);
            if (compressResult.messages != null) {
                ContextEvent event = ContextEvent.builder()
                        .eventType(processorType())
                        .messagesToModify(compressResult.modifiedIndices)
                        .build();
                context.setMessages(compressResult.messages);
                return ProcessResult.ofMessages(event, List.of());
            }
            return ProcessResult.ofMessages(null, messagesToAdd);
        } catch (Exception exception) {
            throw ErrorHelper.buildError(
                    StatusCode.CONTEXT_EXECUTION_ERROR,
                    "error_msg",
                    "compress messages failed: " + exception.getMessage());
        }
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean triggerAddMessages(ModelContext context, List<BaseMessage> messagesToAdd) {
        int messageSize = context.size() + (messagesToAdd != null ? messagesToAdd.size() : 0);
        if (messageSize < messagesToKeep) {
            return false;
        }
        List<BaseMessage> allMessages = new ArrayList<>(context.getMessages());
        if (messagesToAdd != null) {
            allMessages.addAll(messagesToAdd);
        }
        int tokens = countMessagesTokens(allMessages, context.tokenCounter());
        if (tokens > tokenThreshold) {
            CurrentRoundCompressorConfig config = getConfig();
            Loggers.CONTEXT_ENGINE.info("[" + processorType() + " triggered] context tokens "
                    + tokens + " exceeds threshold of " + config.getTokensThreshold());
            return true;
        }
        return false;
    }

    int getCompressIdx(List<BaseMessage> messages) {
        int compressedIdx = -1;
        for (int index = messages.size() - 1; index >= 0; index--) {
            if (messages.get(index) instanceof UserMessage) {
                compressedIdx = index;
                break;
            }
        }
        if (compressedIdx == messages.size() - 1) {
            return -1;
        }
        if (compressedIdx < 0) {
            return -1;
        }
        int keepIndex = messages.size() - messagesToKeep;
        if (compressedIdx >= keepIndex) {
            return -1;
        }
        return compressedIdx;
    }

    CompressResult multiCompress(
            List<BaseMessage> contextMessages,
            int lastUserIdx,
            int endIdx,
            ModelContext context) {
        boolean isUpdated = false;
        List<Integer> modifiedIndices = new ArrayList<>();
        List<BaseMessage> workingMessages = contextMessages;
        int startIdx = lastUserIdx + 1;
        int actualEndIdx = endIdx;
        if (actualEndIdx >= startIdx) {
            actualEndIdx = findLastCompletedApiRoundEndIdx(workingMessages, startIdx, actualEndIdx);
        }
        if (actualEndIdx >= startIdx) {
            List<BaseMessage> messagesToCompress = new ArrayList<>(
                    workingMessages.subList(startIdx, actualEndIdx + 1));
            BaseMessage compressedMessage = compress(
                    messagesToCompress,
                    context,
                    workingMessages,
                    actualEndIdx,
                    lastUserIdx);
            if (compressedMessage != null) {
                workingMessages = ContextUtils.replaceMessages(
                        workingMessages,
                        List.of(compressedMessage),
                        startIdx,
                        actualEndIdx);
                for (int index = startIdx; index <= actualEndIdx; index++) {
                    modifiedIndices.add(index);
                }
                isUpdated = true;
            }
        }

        for (int[] range : iterSummaryMergeRanges(workingMessages, summaryMergeMinBlocks)) {
            List<BaseMessage> oldCompressMessages = new ArrayList<>(
                    workingMessages.subList(range[0], range[1] + 1));
            BaseMessage compressedMessage = mergeSummaryBlocks(context, oldCompressMessages);
            if (compressedMessage != null) {
                workingMessages = ContextUtils.replaceMessages(
                        workingMessages,
                        List.of(compressedMessage),
                        range[0],
                        range[1]);
                for (int index = range[0]; index <= range[1]; index++) {
                    modifiedIndices.add(index);
                }
                isUpdated = true;
                break;
            }
        }
        return new CompressResult(isUpdated ? workingMessages : null, modifiedIndices);
    }

    BaseMessage compress(
            List<BaseMessage> messagesToCompress,
            ModelContext context,
            List<BaseMessage> allContextMessages,
            Integer compressEndIdx,
            Integer currentQueryIdx) {
        TokenCounter tokenCounter = context.tokenCounter();
        int inputTokens = countMessagesTokens(messagesToCompress, tokenCounter);
        if (inputTokens < minSelectedTokensForCompression) {
            Loggers.CONTEXT_ENGINE.info("[" + processorType() + "] Skipping: selected span tokens ("
                    + inputTokens + ") < min_selected_tokens_for_compression ("
                    + minSelectedTokensForCompression + ")");
            return null;
        }

        String priorSummaries = "";
        String recentContext = "";
        String priorContextAndQuery = "";
        if (allContextMessages != null) {
            List<Integer> summaryIndices = collectSummaryIndices(allContextMessages);
            if (!summaryIndices.isEmpty()) {
                priorSummaries = String.join("\n---\n", summaryIndices.stream()
                        .map(index -> allContextMessages.get(index).getContentAsString())
                        .toList());
            }
            if (compressEndIdx != null) {
                recentContext = formatRecentContext(allContextMessages, compressEndIdx);
            }
            if (currentQueryIdx != null && currentQueryIdx >= 0) {
                priorContextAndQuery = formatPriorContextAndQuery(allContextMessages, currentQueryIdx);
            }
        }

        String filledPrompt = buildPrompt(
                compressionTargetTokens,
                priorSummaries,
                recentContext,
                priorContextAndQuery);
        String processedMessages = String.join("\n", messagesToCompress.stream()
                .map(message -> "role:" + message.getRole() + ", content:" + message)
                .toList());
        filledPrompt = filledPrompt.replace("{selected_messages}", processedMessages);

        String summary = invokeModel(filledPrompt, "current-round compression");
        if (summary == null || summary.isBlank()) {
            return null;
        }
        int compressedTokens = countMessagesTokens(List.of(new UserMessage(summary)), tokenCounter);
        if (compressedTokens >= inputTokens) {
            Loggers.CONTEXT_ENGINE.info("[" + processorType() + "] Skipping: compressed tokens ("
                    + compressedTokens + ") >= original (" + inputTokens + "), no benefit.");
            return null;
        }
        return new UserMessage(wrapMemoryBlock(summary));
    }

    BaseMessage mergeSummaryBlocks(ModelContext context, List<BaseMessage> oldCompressMessages) {
        TokenCounter tokenCounter = context.tokenCounter();
        int totalTokens = countMessagesTokens(oldCompressMessages, tokenCounter);
        if (totalTokens <= accumulatedSummaryTokenLimit) {
            return null;
        }
        List<String> mergedBlocks = new ArrayList<>();
        for (int index = 0; index < oldCompressMessages.size(); index++) {
            mergedBlocks.add("[MEMORY_BLOCK_" + (index + 1) + "]\n"
                    + oldCompressMessages.get(index).getContentAsString());
        }
        String filledPrompt = CLEAN_PROMPT
                .replace("{compress_len}", String.valueOf(summaryMergeTargetTokens))
                .replace("{compressed_blocks}", mergedBlocks.isEmpty() ? "(none)" : String.join("\n\n", mergedBlocks));

        String summary = invokeModel(filledPrompt, "summary merge");
        if (summary == null || summary.isBlank()) {
            Loggers.CONTEXT_ENGINE.info("[" + processorType() + "] failed to compress "
                    + oldCompressMessages.size() + " old compressed messages");
            return null;
        }
        Loggers.CONTEXT_ENGINE.info("[" + processorType() + "] compressed "
                + oldCompressMessages.size() + " old compressed messages into one");
        return new UserMessage(wrapMemoryBlock(summary));
    }

    private String invokeModel(String prompt, String phase) {
        try {
            AssistantMessage response = model.invoke(
                    List.of(new UserMessage(prompt)),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null);
            return response != null ? response.getContentAsString() : "";
        } catch (Exception exception) {
            Loggers.CONTEXT_ENGINE.warning("[" + processorType()
                    + "] compression model invoke failed during " + phase
                    + ", skip current processor and continue remaining processors: "
                    + exception.getMessage());
            return null;
        }
    }

    static boolean isSummaryMessage(BaseMessage message) {
        return message instanceof UserMessage
                && message.getContent() instanceof String content
                && content.startsWith(SUMMARY_MARKER);
    }

    static List<Integer> collectSummaryIndices(List<BaseMessage> messages) {
        List<Integer> indices = new ArrayList<>();
        for (int index = 0; index < messages.size(); index++) {
            if (isSummaryMessage(messages.get(index))) {
                indices.add(index);
            }
        }
        return indices;
    }

    static int countMessagesTokens(List<BaseMessage> messages, TokenCounter tokenCounter) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        if (tokenCounter != null) {
            try {
                return tokenCounter.countMessages(messages);
            } catch (IllegalStateException exception) {
                Loggers.CONTEXT_ENGINE.warning("[CurrentRoundCompressor] token_counter failed, "
                        + "fallback to char-based estimate: "
                        + exception.getMessage());
            }
        }
        return messages.stream().mapToInt(ContextUtils::estimateMessageTokens).sum();
    }

    static int findLastCompletedApiRoundEndIdx(List<BaseMessage> messages, int startIdx, int endIdx) {
        if (endIdx < startIdx) {
            return endIdx;
        }
        List<BaseMessage> candidateMessages = messages.subList(startIdx, endIdx + 1);
        List<int[]> completedRounds = SessionMemoryManager.groupCompletedApiRounds(candidateMessages);
        if (completedRounds.isEmpty()) {
            return startIdx - 1;
        }
        int completedEnd = completedRounds.get(completedRounds.size() - 1)[1];
        return startIdx + completedEnd - 1;
    }

    static List<int[]> iterSummaryMergeRanges(List<BaseMessage> messages, int minBlocks) {
        List<int[]> ranges = new ArrayList<>();
        Integer startIdx = null;
        Integer previousIdx = null;
        for (int index = 0; index < messages.size(); index++) {
            if (isSummaryMessage(messages.get(index))) {
                if (startIdx == null) {
                    startIdx = index;
                }
                previousIdx = index;
                continue;
            }
            if (startIdx != null && previousIdx != null) {
                if (previousIdx - startIdx + 1 >= minBlocks) {
                    ranges.add(new int[]{startIdx, previousIdx});
                }
                startIdx = null;
                previousIdx = null;
            }
        }
        if (startIdx != null && previousIdx != null && previousIdx - startIdx + 1 >= minBlocks) {
            ranges.add(new int[]{startIdx, previousIdx});
        }
        return ranges;
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public void loadState(Map<String, Object> state) {
        // stateless
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> saveState() {
        return Map.of();
    }

    record CompressResult(List<BaseMessage> messages, List<Integer> modifiedIndices) {
    }
}
