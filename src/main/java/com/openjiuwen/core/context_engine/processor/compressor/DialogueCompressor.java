/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context_engine.processor.compressor;

import com.openjiuwen.core.context_engine.ContextEngine;
import com.openjiuwen.core.context_engine.context.ContextUtils;
import com.openjiuwen.core.context_engine.context.SessionModelContext;
import com.openjiuwen.core.context_engine.processor.ContextEvent;
import com.openjiuwen.core.context_engine.processor.ContextProcessor;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Compresses historical ReAct dialogue blocks into memory messages.
 *
 * <p>Mirrors Python's {@code DialogueCompressor} in
 * {@code openjiuwen/core/context_engine/processor/compressor/dialogue_compressor.py}.</p>
 */
public class DialogueCompressor extends ContextProcessor {
    public static final String DIALOGUE_MEMORY_BLOCK_MARKER = "[DIALOGUE_MEMORY_BLOCK]";

    private static final String DEFAULT_COMPRESSION_PROMPT = """
            You are a **Task Data Preservation Expert** focused on compressing historical ReAct blocks with high fidelity.
            
            Your output will replace only the explicitly listed target ReAct blocks.
            Compress ONLY the listed target blocks and return valid JSON only.
            Use this exact schema:
            {
              "blocks": [
                {
                  "block_id": "react_1",
                  "summary": "..."
                }
              ]
            }
            Target length for each block summary: <= {compression_target_tokens} tokens.
            """;

    static {
        ContextEngine.registerProcessor("DialogueCompressor", DialogueCompressor.class);
    }

    private final DialogueCompressorConfig config;
    private final String compressedPrompt;
    private final int tokenThreshold;
    private final Integer messageNumThreshold;
    private final Integer messagesToKeep;
    private final boolean keepLastRound;
    private final int compressionTargetTokens;
    private final Model model;

    public DialogueCompressor(Object config) {
        this(asConfig(config));
    }

    public DialogueCompressor(DialogueCompressorConfig config) {
        this(config, config != null && config.getModelClient() != null
                ? new Model(config.getModelClient(), config.getModel())
                : null);
    }

    DialogueCompressor(DialogueCompressorConfig config, Model model) {
        super(config == null ? new DialogueCompressorConfig() : config);
        this.config = config == null ? new DialogueCompressorConfig() : config;
        this.compressedPrompt = this.config.getCustomCompressionPrompt() == null
                ? DEFAULT_COMPRESSION_PROMPT
                : this.config.getCustomCompressionPrompt();
        this.tokenThreshold = this.config.getTokensThreshold();
        this.messageNumThreshold = this.config.getMessagesThreshold();
        this.messagesToKeep = this.config.getMessagesToKeep();
        this.keepLastRound = this.config.isKeepLastRound();
        this.compressionTargetTokens = this.config.getCompressionTargetTokens();
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
        int compressUntilIdx = getCompressIdx(contextMessages);
        if (compressUntilIdx == -1) {
            return noOp(incoming);
        }
        List<CompressTarget> targets = buildCompressTargets(contextMessages.subList(0, compressUntilIdx));
        if (targets.isEmpty()) {
            return noOp(incoming);
        }
        AssistantMessage response = invokeMultiBlockCompression(contextMessages, targets);
        if (response == null) {
            return noOp(incoming);
        }
        ReplacementBuildResult jsonResult = buildJsonReplacements(context, targets, response.getParserContent());
        if (!jsonResult.replacements().isEmpty()) {
            List<BaseMessage> updatedMessages = applyReplacements(contextMessages, jsonResult.replacements());
            ContextEvent event = new ContextEvent(
                    processorType(),
                    jsonResult.modifiedIndices(),
                    extractCompactSummaryFromReplacements(jsonResult.replacements()),
                    currentCompressionUsage());
            context.setMessages(updatedMessages, true);
            return CompletableFuture.completedFuture(new SessionModelContext.ProcessResult(event, List.of(), null));
        }
        if (!isValidBlocksPayload(response.getParserContent())) {
            Replacement fallback = buildFallbackReplacement(context, targets, response.getContentAsString());
            if (fallback != null) {
                List<BaseMessage> updatedMessages = applyReplacements(contextMessages, List.of(fallback));
                List<Integer> modifiedIndices = indexRange(fallback.startIndex(), fallback.endIndex());
                ContextEvent event = new ContextEvent(
                        processorType(),
                        modifiedIndices,
                        extractCompactSummaryFromReplacements(List.of(fallback)),
                        currentCompressionUsage());
                context.setMessages(updatedMessages, true);
                return CompletableFuture.completedFuture(new SessionModelContext.ProcessResult(event, List.of(), null));
            }
        }
        return noOp(incoming);
    }

    @Override
    public CompletionStage<Boolean> triggerAddMessages(SessionModelContext context, List<BaseMessage> messagesToAdd,
                                                       Map<String, Object> kwargs) {
        List<BaseMessage> incoming = messagesToAdd == null ? List.of() : messagesToAdd;
        int messageSize = context.length() + incoming.size();
        System.out.println("[DEBUG-DC-triggerAddMessages] context.length()=" + context.length() + ", incoming.size()=" + incoming.size() + ", messageSize=" + messageSize + ", messageNumThreshold=" + messageNumThreshold + ", messagesToKeep=" + messagesToKeep);
        if (messageNumThreshold != null && messageSize > messageNumThreshold) {
            System.out.println("[DEBUG-DC-triggerAddMessages] TRIGGERED by messageNumThreshold: " + messageSize + " > " + messageNumThreshold);
            return CompletableFuture.completedFuture(true);
        }
        if (messagesToKeep != null && messageSize < messagesToKeep) {
            return CompletableFuture.completedFuture(false);
        }
        List<BaseMessage> contextMessages = new ArrayList<>(context.getMessages());
        contextMessages.addAll(incoming);
        int tokens = countMessagesTokens(context, contextMessages);
        return CompletableFuture.completedFuture(tokens > tokenThreshold);
    }

    public int getCompressIdx(List<BaseMessage> messages) {
        List<BaseMessage> safeMessages = messages == null ? List.of() : messages;
        int keepIndex = messagesToKeep == null ? safeMessages.size() : safeMessages.size() - messagesToKeep;
        if (!keepLastRound) {
            return keepIndex;
        }
        Integer lastFinalAssistantIdx = findLastFinalAssistantIdx(safeMessages);
        if (lastFinalAssistantIdx == null) {
            return keepIndex;
        }
        return Math.min(lastFinalAssistantIdx, keepIndex);
    }

    public static List<CompressPair> getCompressPairs(List<BaseMessage> messages) {
        int currentUser = -1;
        List<CompressPair> result = new ArrayList<>();
        List<BaseMessage> safeMessages = messages == null ? List.of() : messages;
        for (int index = 0; index < safeMessages.size(); index++) {
            BaseMessage message = safeMessages.get(index);
            if (message instanceof UserMessage) {
                if (currentUser == -1) {
                    currentUser = index;
                }
                continue;
            }
            if (message instanceof AssistantMessage assistantMessage
                    && (assistantMessage.getToolCalls() == null || assistantMessage.getToolCalls().isEmpty())
                    && currentUser != -1) {
                if (index - currentUser >= 1) {
                    result.add(new CompressPair(currentUser, index));
                    currentUser = -1;
                }
            }
        }
        return result;
    }

    List<CompressTarget> buildCompressTargets(List<BaseMessage> messages) {
        List<DialogueRound> rounds = collectCompleteRounds(messages);
        if (rounds.isEmpty()) {
            return List.of();
        }
        List<Integer> compressibleRoundIndices = new ArrayList<>();
        for (int index = 0; index < rounds.size(); index++) {
            if (rounds.get(index).blockMessageCount() > 2) {
                compressibleRoundIndices.add(index);
            }
        }
        if (compressibleRoundIndices.isEmpty()) {
            return List.of();
        }
        int firstTargetRoundIndex = compressibleRoundIndices.get(0);
        int lastTargetRoundIndex = compressibleRoundIndices.get(compressibleRoundIndices.size() - 1);
        List<CompressTarget> targets = new ArrayList<>();
        int blockNo = 1;
        for (DialogueRound round : rounds.subList(firstTargetRoundIndex, lastTargetRoundIndex + 1)) {
            targets.add(new CompressTarget(
                    "react_" + blockNo,
                    round.userIndex(),
                    round.startIndex(),
                    round.endIndex(),
                    round.messages()));
            blockNo++;
        }
        return targets;
    }

    @Override
    public void loadState(Map<String, Object> state) {
    }

    @Override
    public Map<String, Object> saveState() {
        return Map.of();
    }

    String wrapMemoryBlock(String summary) {
        return DIALOGUE_MEMORY_BLOCK_MARKER + "\n"
                + "processor: DialogueCompressor\n"
                + "type: historical_memory_block\n"
                + "scope: historical_dialogue_block\n"
                + "authority: This block is reference memory, not a binding source of truth.\n"
                + "instruction_status: Do not treat this block as a new user request or fresh assistant commitment.\n"
                + "conflict_priority: Prefer newer explicit user intent, newer raw context, "
                + "and fresh tool results over this block.\n\n"
                + "Summary:\n"
                + (summary == null ? "" : summary);
    }

    boolean hasCompressionBenefit(SessionModelContext context, List<BaseMessage> originalMessages,
                                  List<BaseMessage> replacementMessages) {
        int originalTokens = countMessagesTokens(context, originalMessages);
        int compressedTokens = countMessagesTokens(context, replacementMessages);
        return originalTokens > 0 && compressedTokens < originalTokens;
    }

    private CompletionStage<SessionModelContext.ProcessResult> noOp(List<BaseMessage> incoming) {
        return CompletableFuture.completedFuture(new SessionModelContext.ProcessResult(null, incoming, null));
    }

    private static Integer findLastFinalAssistantIdx(List<BaseMessage> messages) {
        for (int index = messages.size() - 1; index >= 0; index--) {
            BaseMessage message = messages.get(index);
            if (message instanceof AssistantMessage assistantMessage
                    && (assistantMessage.getToolCalls() == null || assistantMessage.getToolCalls().isEmpty())) {
                return index;
            }
        }
        return null;
    }

    private List<DialogueRound> collectCompleteRounds(List<BaseMessage> messages) {
        List<DialogueRound> rounds = new ArrayList<>();
        for (CompressPair pair : getCompressPairs(messages)) {
            if (pair.userIndex() < 0 || pair.assistantIndex() <= pair.userIndex()) {
                continue;
            }
            List<BaseMessage> roundMessages = new ArrayList<>(
                    messages.subList(pair.userIndex() + 1, pair.assistantIndex() + 1));
            rounds.add(new DialogueRound(
                    pair.userIndex(),
                    pair.userIndex() + 1,
                    pair.assistantIndex(),
                    roundMessages,
                    pair.assistantIndex() - pair.userIndex() + 1));
        }
        return rounds;
    }

    private AssistantMessage invokeMultiBlockCompression(List<BaseMessage> contextMessages,
                                                         List<CompressTarget> targets) {
        String systemPrompt = compressedPrompt.replace("{compression_target_tokens}",
                String.valueOf(compressionTargetTokens));
        List<BaseMessage> modelMessages = List.of(
                new SystemMessage(systemPrompt),
                new UserMessage(buildSplitContextPayload(contextMessages, targets)),
                new UserMessage(buildTargetsPayload(targets)));
        try {
            if (model == null) {
                return null;
            }
            AssistantMessage response = model.invoke(modelMessages).toCompletableFuture().join();
            recordCompressionUsage(response);
            return response;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String buildSplitContextPayload(List<BaseMessage> contextMessages, List<CompressTarget> targets) {
        int firstTargetStart = targets.stream().mapToInt(CompressTarget::startIndex).min().orElse(0);
        int lastTargetEnd = targets.stream().mapToInt(CompressTarget::endIndex).max().orElse(0);
        String beforeTargets = serializeMessages(contextMessages.subList(0, firstTargetStart), 0);
        if (beforeTargets.isEmpty()) {
            beforeTargets = "(none)";
        }
        List<String> targetBlocks = new ArrayList<>();
        targetBlocks.add("[Compression Targets]");
        for (CompressTarget target : targets) {
            targetBlocks.add("[Block: " + target.blockId() + "]");
            String serializedTarget = serializeMessages(target.messages(), target.startIndex());
            targetBlocks.add(serializedTarget.isEmpty() ? "(empty)" : serializedTarget);
            targetBlocks.add("");
        }
        String afterTargets = serializeMessages(
                contextMessages.subList(lastTargetEnd + 1, contextMessages.size()),
                lastTargetEnd + 1);
        if (afterTargets.isEmpty()) {
            afterTargets = "(none)";
        }
        List<String> lines = new ArrayList<>();
        lines.add("[Context Before Targets]");
        lines.add(beforeTargets);
        lines.add("");
        lines.addAll(targetBlocks);
        lines.add("[Context After Targets]");
        lines.add(afterTargets);
        return String.join("\n", lines);
    }

    private String buildTargetsPayload(List<CompressTarget> targets) {
        List<String> blocks = new ArrayList<>();
        blocks.add("[Target Mapping]");
        blocks.add("You must only compress the following ReAct blocks.");
        blocks.add("");
        for (CompressTarget target : targets) {
            blocks.add("[Block: " + target.blockId() + "]");
            blocks.add("- anchor_user_index: " + target.userIndex());
            blocks.add("- replace_range: [" + target.startIndex() + ", " + target.endIndex() + "]");
            blocks.add("");
        }
        blocks.add("[Output Requirements]");
        blocks.add("- Read the full context to understand the entire task.");
        blocks.add("- Compress only the listed blocks.");
        blocks.add("- Produce one summary for each block_id.");
        blocks.add("- Keep the most task-useful content first.");
        blocks.add("- Preserve both action continuity and task-critical information.");
        blocks.add("- Do not rewrite non-target messages.");
        blocks.add("- Return valid JSON only.");
        return String.join("\n", blocks);
    }

    private String serializeMessages(List<BaseMessage> messages, int startIndex) {
        List<String> lines = new ArrayList<>();
        for (int offset = 0; offset < messages.size(); offset++) {
            lines.add(serializeMessage(startIndex + offset, messages.get(offset)));
        }
        return String.join("\n", lines);
    }

    private String serializeMessage(int index, BaseMessage message) {
        List<String> parts = new ArrayList<>();
        parts.add("[" + index + "] role=" + message.getRole());
        if (message instanceof AssistantMessage assistantMessage
                && assistantMessage.getToolCalls() != null
                && !assistantMessage.getToolCalls().isEmpty()) {
            String names = String.join(", ", assistantMessage.getToolCalls().stream()
                    .map(call -> call.getName() == null ? "" : call.getName())
                    .toList());
            parts.add("tool_calls=" + names);
        }
        if (message instanceof ToolMessage toolMessage) {
            parts.add("tool_call_id=" + toolMessage.getToolCallId());
        }
        parts.add("content=" + CompressorUtils.messageToText(message));
        return String.join(" | ", parts);
    }

    private ReplacementBuildResult buildJsonReplacements(SessionModelContext context, List<CompressTarget> targets,
                                                         Object parserContent) {
        if (!isValidBlocksPayload(parserContent)) {
            return new ReplacementBuildResult(List.of(), List.of());
        }
        Map<String, String> blockMap = new LinkedHashMap<>();
        Object blocks = ((Map<?, ?>) parserContent).get("blocks");
        for (Object item : (List<?>) blocks) {
            if (!(item instanceof Map<?, ?> rawMap)) {
                continue;
            }
            Object blockId = rawMap.get("block_id");
            Object summary = rawMap.get("summary");
            if (!(blockId instanceof String blockIdText) || blockIdText.isBlank()
                    || !(summary instanceof String summaryText) || summaryText.strip().isEmpty()) {
                continue;
            }
            blockMap.put(blockIdText, summaryText.strip());
        }
        List<Replacement> replacements = new ArrayList<>();
        List<Integer> modifiedIndices = new ArrayList<>();
        for (CompressTarget target : targets) {
            String summary = blockMap.get(target.blockId());
            if (summary == null || summary.isBlank()) {
                continue;
            }
            BaseMessage replacementMessage = buildMemoryMessage(summary);
            List<BaseMessage> replacementMessages = List.of(replacementMessage);
            if (!hasCompressionBenefit(context, target.messages(), replacementMessages)) {
                continue;
            }
            replacements.add(new Replacement(target.startIndex(), target.endIndex(), replacementMessages));
            modifiedIndices.addAll(indexRange(target.startIndex(), target.endIndex()));
        }
        return new ReplacementBuildResult(replacements, modifiedIndices);
    }

    private static boolean isValidBlocksPayload(Object parserContent) {
        return parserContent instanceof Map<?, ?> map && map.get("blocks") instanceof List<?>;
    }

    private Replacement buildFallbackReplacement(SessionModelContext context, List<CompressTarget> targets,
                                                 String summary) {
        String cleanSummary = summary == null ? "" : summary.strip();
        if (cleanSummary.isEmpty()) {
            return null;
        }
        int startIndex = targets.stream().mapToInt(CompressTarget::startIndex).min().orElse(0);
        int endIndex = targets.stream().mapToInt(CompressTarget::endIndex).max().orElse(0);
        List<BaseMessage> originalMessages = new ArrayList<>();
        for (CompressTarget target : targets) {
            originalMessages.addAll(target.messages());
        }
        List<BaseMessage> replacementMessages = List.of(buildMemoryMessage(cleanSummary));
        if (!hasCompressionBenefit(context, originalMessages, replacementMessages)) {
            return null;
        }
        return new Replacement(startIndex, endIndex, replacementMessages);
    }

    private BaseMessage buildMemoryMessage(String summary) {
        return new UserMessage(wrapMemoryBlock(summary.strip()));
    }

    private static String extractCompactSummaryFromReplacements(List<Replacement> replacements) {
        List<String> parts = new ArrayList<>();
        for (Replacement replacement : replacements) {
            for (BaseMessage message : replacement.replacementMessages()) {
                String text = CompressorUtils.messageToText(message);
                if (text.startsWith(DIALOGUE_MEMORY_BLOCK_MARKER)) {
                    parts.add(text);
                }
            }
        }
        return String.join("\n\n", parts);
    }

    private int countMessagesTokens(SessionModelContext context, List<BaseMessage> messages) {
        return CompressorUtils.countMessagesTokens(messages, context == null ? null : context.tokenCounter(),
                processorType());
    }

    private static List<BaseMessage> applyReplacements(List<BaseMessage> messages, List<Replacement> replacements) {
        List<BaseMessage> updatedMessages = new ArrayList<>(messages);
        List<Replacement> sorted = new ArrayList<>(replacements);
        sorted.sort(Comparator.comparingInt(Replacement::startIndex).reversed());
        for (Replacement replacement : sorted) {
            updatedMessages = ContextUtils.replaceMessages(
                    updatedMessages,
                    replacement.replacementMessages(),
                    replacement.startIndex(),
                    replacement.endIndex());
        }
        return updatedMessages;
    }

    private static List<Integer> indexRange(int startIndex, int endIndex) {
        List<Integer> values = new ArrayList<>();
        for (int index = startIndex; index <= endIndex; index++) {
            values.add(index);
        }
        return values;
    }

    private static DialogueCompressorConfig asConfig(Object config) {
        if (config == null) {
            return new DialogueCompressorConfig();
        }
        if (config instanceof DialogueCompressorConfig dialogueConfig) {
            return dialogueConfig;
        }
        throw new IllegalArgumentException("DialogueCompressor requires DialogueCompressorConfig");
    }

    /**
     * Compressible user/final-assistant pair.
     *
     * <p>Mirrors Python's tuple returned by {@code get_compress_pairs} in
     * {@code openjiuwen/core/context_engine/processor/compressor/dialogue_compressor.py}.</p>
     */
    public record CompressPair(int userIndex, int assistantIndex) {
    }

    /**
     * Target block for model-side dialogue compression.
     *
     * <p>Mirrors Python's {@code _CompressTarget} in
     * {@code openjiuwen/core/context_engine/processor/compressor/dialogue_compressor.py}.</p>
     */
    record CompressTarget(String blockId, int userIndex, int startIndex, int endIndex,
                          List<BaseMessage> messages) {
    }

    /**
     * Completed dialogue round metadata.
     *
     * <p>Mirrors Python's {@code _DialogueRound} in
     * {@code openjiuwen/core/context_engine/processor/compressor/dialogue_compressor.py}.</p>
     */
    record DialogueRound(int userIndex, int startIndex, int endIndex, List<BaseMessage> messages,
                         int blockMessageCount) {
    }

    private record Replacement(int startIndex, int endIndex, List<BaseMessage> replacementMessages) {
    }

    private record ReplacementBuildResult(List<Replacement> replacements, List<Integer> modifiedIndices) {
    }
}
