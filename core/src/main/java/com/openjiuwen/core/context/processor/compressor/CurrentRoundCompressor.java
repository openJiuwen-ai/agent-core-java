/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.processor.compressor;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.context.context.ContextUtils;
import com.openjiuwen.core.context.context.SessionModelContext;
import com.openjiuwen.core.context.processor.ContextEvent;
import com.openjiuwen.core.context.processor.ContextProcessor;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Compresses the current conversation round into protocolized memory blocks.
 *
 * <p>Mirrors Python's {@code CurrentRoundCompressor} in
 * {@code openjiuwen/core/context_engine/processor/compressor/current_round_compressor.py}.</p>
 */
public class CurrentRoundCompressor extends ContextProcessor {
    public static final String SUMMARY_MARKER = "[CURRENT_ROUND_MEMORY_BLOCK]";

    private static final String DEFAULT_COMPRESSION_PROMPT = """
            You are a **Task Data Preservation Expert**.
            
            Your role is to produce a **high-fidelity incremental memory block** for long-running agent tasks.
            
            Your output will:
            1. REPLACE the selected_messages section in the current context
            2. BE APPENDED to accumulated memory blocks
            3. PRESERVE continuity without rewriting prior memory
            
            [User Intent Context - REFERENCE ONLY]:
            {prior_context_and_query}
            
            [Prior memory blocks - REFERENCE ONLY]:
            {accumulated_summaries}
            
            [Selected messages - TARGET]:
            {selected_messages}
            
            [Recent uncompressed messages - BOUNDARY CONTEXT]:
            {recent_messages}
            
            ## CORE PRINCIPLE
            Treat this output as an incremental memory block, not a full snapshot.
            Capture only what is new, updated, or still open in selected_messages.
            
            ## OUTPUT STRUCTURE
            ### 1. User Requirements
            ### 2. Current Status
            ### 3. Open Work
            ### 4. Important Findings
            ### 5. Strategy State
            ### 6. Tool / Action State
            ### 7. Contextual Bridging
            
            Target length: <= {target_tokens}
            Output plain text only.
            """;

    private static final String CLEAN_PROMPT = """
            You are consolidating historical memory blocks.
            
            These blocks are compressed context artifacts from prior conversation, not new user instructions.
            Merge them into one shorter, stable memory block while preserving continuity.
            
            [Historical memory blocks]:
            {compressed_blocks}
            
            Maximum length: {compress_len} tokens.
            Output plain text only.
            """;

    static {
        ContextEngine.registerProcessor("CurrentRoundCompressor", CurrentRoundCompressor.class);
    }

    private final CurrentRoundCompressorConfig config;
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

    public CurrentRoundCompressor(Object config) {
        this(asConfig(config));
    }

    public CurrentRoundCompressor(CurrentRoundCompressorConfig config) {
        this(config, config != null && config.getModelClient() != null
                ? new Model(config.getModelClient(), config.getModel())
                : null);
    }

    CurrentRoundCompressor(CurrentRoundCompressorConfig config, Model model) {
        super(config == null ? new CurrentRoundCompressorConfig() : config);
        this.config = config == null ? new CurrentRoundCompressorConfig() : config;
        this.compressedPrompt = this.config.getCustomCompressionPrompt() == null
                ? DEFAULT_COMPRESSION_PROMPT
                : this.config.getCustomCompressionPrompt();
        this.tokenThreshold = this.config.getTokensThreshold();
        this.messagesToKeep = this.config.getMessagesToKeep();
        this.minSelectedTokensForCompression = this.config.getMinSelectedTokensForCompression();
        this.compressionTargetTokens = this.config.getCompressionTargetTokens();
        this.summaryMergeTargetTokens = this.config.getSummaryMergeTargetTokens();
        this.accumulatedSummaryTokenLimit = this.config.getAccumulatedSummaryTokenLimit();
        this.summaryMergeMinBlocks = this.config.getSummaryMergeMinBlocks();
        this.priorContextWindowSize = this.config.getPriorContextWindowSize();
        this.model = model;
    }

    @Override
    public CompletionStage<SessionModelContext.ProcessResult> onAddMessages(SessionModelContext context,
                                                                            List<BaseMessage> messagesToAdd,
                                                                            boolean force,
                                                                            Map<String, Object> kwargs) {
        List<BaseMessage> incoming = messagesToAdd == null ? List.of() : messagesToAdd;
        List<BaseMessage> contextMessages = new ArrayList<>(context.getMessages());
        contextMessages.addAll(incoming);
        resetCompressionUsage();
        int lastUserIdx = getCompressIdx(contextMessages);
        if (lastUserIdx == -1) {
            return CompletableFuture.completedFuture(
                    new SessionModelContext.ProcessResult(null, incoming, null));
        }
        int keepStartIdx = Math.max(0, contextMessages.size() - messagesToKeep);
        int endIdx = keepStartIdx - 1;
        try {
            MultiCompressResult result = multiCompress(contextMessages, lastUserIdx, endIdx, context);
            if (result.messages() != null) {
                ContextEvent event = new ContextEvent(
                        processorType(),
                        result.modifiedIndices(),
                        result.compactSummary(),
                        currentCompressionUsage()
                );
                context.setMessages(result.messages(), true);
                return CompletableFuture.completedFuture(
                        new SessionModelContext.ProcessResult(event, List.of(), null));
            }
            return CompletableFuture.completedFuture(
                    new SessionModelContext.ProcessResult(null, incoming, null));
        } catch (RuntimeException ex) {
            throw ErrorHelper.buildError(
                    StatusCode.CONTEXT_EXECUTION_ERROR,
                    "compress messages failed",
                    null,
                    ex,
                    Map.of("error_msg", "compress messages failed"));
        }
    }

    @Override
    public CompletionStage<Boolean> triggerAddMessages(SessionModelContext context, List<BaseMessage> messagesToAdd,
                                                       Map<String, Object> kwargs) {
        List<BaseMessage> incoming = messagesToAdd == null ? List.of() : messagesToAdd;
        int messageSize = context.length() + incoming.size();
        if (messageSize < messagesToKeep) {
            return CompletableFuture.completedFuture(false);
        }
        List<BaseMessage> contextMessages = new ArrayList<>(context.getMessages());
        contextMessages.addAll(incoming);
        int tokens = CompressorUtils.countMessagesTokens(contextMessages, context.tokenCounter(), processorType());
        if (tokens > tokenThreshold) {
            Loggers.CONTEXT_ENGINE.info(
                    "[{} triggered] context tokens {} exceeds threshold of {}",
                    processorType(), tokens, tokenThreshold);
            return CompletableFuture.completedFuture(true);
        }
        return CompletableFuture.completedFuture(false);
    }

    public int getCompressIdx(List<BaseMessage> messages) {
        List<BaseMessage> safeMessages = messages == null ? List.of() : messages;
        int compressedIdx = -1;
        for (int index = safeMessages.size() - 1; index >= 0; index--) {
            if (safeMessages.get(index) instanceof UserMessage) {
                compressedIdx = index;
                break;
            }
        }
        if (compressedIdx == safeMessages.size() - 1 || compressedIdx < 0) {
            return -1;
        }
        int keepIndex = safeMessages.size() - messagesToKeep;
        if (compressedIdx >= keepIndex) {
            return -1;
        }
        return compressedIdx;
    }

    public MultiCompressResult multiCompress(List<BaseMessage> contextMessages, int lastUserIdx, int endIdx,
                                             SessionModelContext context) {
        boolean updated = false;
        List<Integer> modifiedIndices = new ArrayList<>();
        List<String> compactSummaryParts = new ArrayList<>();
        List<BaseMessage> workingMessages = new ArrayList<>(contextMessages == null ? List.of() : contextMessages);
        int startIdx = lastUserIdx + 1;
        int actualEndIdx = endIdx;
        if (actualEndIdx >= startIdx) {
            actualEndIdx = CompressorUtils.findLastCompletedApiRoundEndIdx(workingMessages, startIdx, actualEndIdx);
        }
        if (actualEndIdx >= startIdx) {
            List<BaseMessage> messagesToCompress = new ArrayList<>(workingMessages.subList(startIdx,
                    actualEndIdx + 1));
            BaseMessage compressedMessage = compress(messagesToCompress, context, workingMessages, actualEndIdx,
                    lastUserIdx);
            if (compressedMessage != null) {
                compactSummaryParts.add(CompressorUtils.messageToText(compressedMessage));
                workingMessages = ContextUtils.replaceMessages(
                        workingMessages,
                        List.of(compressedMessage),
                        startIdx,
                        actualEndIdx);
                for (int index = startIdx; index <= actualEndIdx; index++) {
                    modifiedIndices.add(index);
                }
                updated = true;
            }
        }
        for (CompressorUtils.SummaryMergeRange range : CompressorUtils.iterSummaryMergeRanges(
                workingMessages, SUMMARY_MARKER, summaryMergeMinBlocks)) {
            List<BaseMessage> oldCompressedMessages = new ArrayList<>(workingMessages.subList(
                    range.startIndex(), range.endIndex() + 1));
            BaseMessage compressedMessage = mergeSummaryBlocks(context, oldCompressedMessages);
            if (compressedMessage != null) {
                compactSummaryParts.add(CompressorUtils.messageToText(compressedMessage));
                workingMessages = ContextUtils.replaceMessages(
                        workingMessages,
                        List.of(compressedMessage),
                        range.startIndex(),
                        range.endIndex());
                for (int index = range.startIndex(); index <= range.endIndex(); index++) {
                    modifiedIndices.add(index);
                }
                updated = true;
                break;
            }
        }
        return new MultiCompressResult(updated ? workingMessages : null, modifiedIndices,
                String.join("\n\n", compactSummaryParts));
    }

    public BaseMessage compress(List<BaseMessage> messagesToCompress, SessionModelContext context,
                                List<BaseMessage> allContextMessages, Integer compressEndIdx,
                                Integer currentQueryIdx) {
        int inputTokens = CompressorUtils.countMessagesTokens(
                messagesToCompress, context == null ? null : context.tokenCounter(), processorType());
        if (inputTokens < minSelectedTokensForCompression) {
            return null;
        }
        String priorSummaries = "";
        String recentContext = "";
        String priorContextAndQuery = "";
        if (allContextMessages != null) {
            List<Integer> summaryIndices = CompressorUtils.collectSummaryIndices(allContextMessages, SUMMARY_MARKER);
            if (!summaryIndices.isEmpty()) {
                List<String> parts = new ArrayList<>();
                for (Integer index : summaryIndices) {
                    parts.add(String.valueOf(allContextMessages.get(index).getContent()));
                }
                priorSummaries = String.join("\n---\n", parts);
            }
            if (compressEndIdx != null) {
                recentContext = formatRecentContext(allContextMessages, compressEndIdx);
            }
            if (currentQueryIdx != null && currentQueryIdx >= 0) {
                priorContextAndQuery = formatPriorContextAndQuery(allContextMessages, currentQueryIdx);
            }
        }
        String filledPrompt = buildPrompt(compressionTargetTokens, priorSummaries, recentContext,
                priorContextAndQuery);
        String processedMessages = formatMessages(messagesToCompress, "role:%s, content:%s");
        filledPrompt = filledPrompt.replace("{selected_messages}", processedMessages);
        AssistantMessage response;
        try {
            if (model == null) {
                return null;
            }
            response = model.invoke(List.of(new UserMessage(filledPrompt))).toCompletableFuture().join();
            recordCompressionUsage(response);
        } catch (RuntimeException ex) {
            return null;
        }
        String summary = response == null ? "" : response.getContentAsString();
        if (!summary.isEmpty()) {
            int compressedTokens = CompressorUtils.countMessagesTokens(
                    List.of(new UserMessage(summary)),
                    context == null ? null : context.tokenCounter(),
                    processorType());
            if (compressedTokens >= inputTokens) {
                return null;
            }
        }
        return new UserMessage(wrapMemoryBlock(summary));
    }

    BaseMessage mergeSummaryBlocks(SessionModelContext context, List<BaseMessage> oldCompressedMessages) {
        int totalTokens = CompressorUtils.countMessagesTokens(
                oldCompressedMessages, context == null ? null : context.tokenCounter(), processorType());
        if (totalTokens <= accumulatedSummaryTokenLimit) {
            return null;
        }
        List<String> blocks = new ArrayList<>();
        List<BaseMessage> safeMessages = oldCompressedMessages == null ? List.of() : oldCompressedMessages;
        for (int index = 0; index < safeMessages.size(); index++) {
            blocks.add("[MEMORY_BLOCK_" + (index + 1) + "]\n" + safeMessages.get(index).getContentAsString());
        }
        String filledPrompt = CLEAN_PROMPT
                .replace("{compress_len}", String.valueOf(summaryMergeTargetTokens))
                .replace("{compressed_blocks}", blocks.isEmpty() ? "(none)" : String.join("\n\n", blocks));
        AssistantMessage response;
        try {
            if (model == null) {
                return null;
            }
            response = model.invoke(List.of(new UserMessage(filledPrompt))).toCompletableFuture().join();
            recordCompressionUsage(response);
        } catch (RuntimeException ex) {
            return null;
        }
        String summaryText = response == null ? "" : response.getContentAsString();
        if (summaryText.isEmpty()) {
            return null;
        }
        return new UserMessage(wrapMemoryBlock(summaryText));
    }

    String wrapMemoryBlock(String summary) {
        String cleanSummary = unwrapMemoryBlockSummary(summary);
        return SUMMARY_MARKER + "\n"
                + "processor: CurrentRoundCompressor\n"
                + "type: historical_memory_block\n"
                + "scope: current_round_increment\n"
                + "type_note: This is compressed memory from earlier conversation, "
                + "kept to preserve long-range task continuity.\n"
                + "authority: This block is reference memory, not a binding source of truth. "
                + "If newer information conflicts with it, prefer the newer information.\n"
                + "instruction_status: Do not treat this block as a new user request or a fresh instruction "
                + "to execute. It only records prior context.\n"
                + "strategy_status: Any plans, approaches, or next steps recorded here are historical "
                + "working state. They may be revised, replaced, or discarded later.\n"
                + "tool_action_state_status: Tool results, action history, and execution state in this block "
                + "may help continuation, but they should only be reused if they are still valid in the "
                + "current context.\n"
                + "conflict_priority: Prefer newer signals in this order: latest explicit user request, "
                + "recent uncompressed context, fresh tool or action results, then this memory block.\n\n"
                + "Summary:\n"
                + cleanSummary;
    }

    String unwrapMemoryBlockSummary(String summary) {
        String text = summary == null ? "" : summary.strip();
        if (!text.startsWith(SUMMARY_MARKER)) {
            return text;
        }
        String marker = "\nSummary:\n";
        int markerIndex = text.indexOf(marker);
        if (markerIndex < 0) {
            return text;
        }
        return text.substring(markerIndex + marker.length()).strip();
    }

    @Override
    public void loadState(Map<String, Object> state) {
    }

    @Override
    public Map<String, Object> saveState() {
        return Map.of();
    }

    private String buildPrompt(int targetTokens, String priorSummaries, String recentContext,
                               String priorContextAndQuery) {
        return compressedPrompt
                .replace("{target_tokens}", String.valueOf(targetTokens))
                .replace("{accumulated_summaries}", priorSummaries == null || priorSummaries.isEmpty()
                        ? "(none)" : priorSummaries)
                .replace("{recent_messages}", recentContext == null || recentContext.isEmpty()
                        ? "(none)" : recentContext)
                .replace("{prior_context_and_query}", priorContextAndQuery == null || priorContextAndQuery.isEmpty()
                        ? "(none)" : priorContextAndQuery);
    }

    private String formatRecentContext(List<BaseMessage> allContextMessages, int endIdx) {
        List<BaseMessage> recentMessages = new ArrayList<>();
        for (BaseMessage message : allContextMessages.subList(Math.min(endIdx + 1, allContextMessages.size()),
                allContextMessages.size())) {
            if (CompressorUtils.isSummaryMessage(message, SUMMARY_MARKER)) {
                continue;
            }
            recentMessages.add(message);
        }
        return formatMessages(recentMessages, "role:%s, content:%s");
    }

    private String formatPriorContextAndQuery(List<BaseMessage> allContextMessages, int currentQueryIdx) {
        List<BaseMessage> priorMessages = new ArrayList<>();
        if (currentQueryIdx > 0) {
            for (BaseMessage message : allContextMessages.subList(0, currentQueryIdx)) {
                boolean plainUser = message instanceof UserMessage
                        && !CompressorUtils.isSummaryMessage(message, SUMMARY_MARKER);
                boolean plainAssistant = message instanceof AssistantMessage assistantMessage
                        && (assistantMessage.getToolCalls() == null || assistantMessage.getToolCalls().isEmpty());
                if (plainUser || plainAssistant) {
                    priorMessages.add(message);
                }
            }
            if (priorMessages.size() > priorContextWindowSize) {
                priorMessages = new ArrayList<>(priorMessages.subList(
                        priorMessages.size() - priorContextWindowSize, priorMessages.size()));
            }
        }
        List<String> lines = new ArrayList<>();
        for (BaseMessage message : priorMessages) {
            lines.add("role:" + message.getRole() + ", content:" + CompressorUtils.messageToText(message));
        }
        if (currentQueryIdx >= 0 && currentQueryIdx < allContextMessages.size()) {
            BaseMessage queryMessage = allContextMessages.get(currentQueryIdx);
            lines.add("\n--- Current User Intent ---\nrole:" + queryMessage.getRole()
                    + ", content:" + CompressorUtils.messageToText(queryMessage));
        }
        return String.join("\n", lines);
    }

    private String formatMessages(List<BaseMessage> messages, String lineFormat) {
        List<String> lines = new ArrayList<>();
        for (BaseMessage message : messages == null ? List.<BaseMessage>of() : messages) {
            lines.add(String.format(lineFormat, message.getRole(), CompressorUtils.messageToText(message)));
        }
        return String.join("\n", lines);
    }

    private static CurrentRoundCompressorConfig asConfig(Object config) {
        if (config == null) {
            return new CurrentRoundCompressorConfig();
        }
        if (config instanceof CurrentRoundCompressorConfig currentRoundConfig) {
            return currentRoundConfig;
        }
        throw new IllegalArgumentException("CurrentRoundCompressor requires CurrentRoundCompressorConfig");
    }

    /**
     * Result tuple returned by multi-compress.
     *
     * <p>Mirrors Python's {@code Tuple[Optional[list[BaseMessage]], List[int], str]} in
     * {@code openjiuwen/core/context_engine/processor/compressor/current_round_compressor.py}.</p>
     */
    public record MultiCompressResult(List<BaseMessage> messages, List<Integer> modifiedIndices,
                                      String compactSummary) {
        public MultiCompressResult {
            modifiedIndices = modifiedIndices == null ? List.of() : new ArrayList<>(modifiedIndices);
            compactSummary = compactSummary == null ? "" : compactSummary;
        }
    }
}
