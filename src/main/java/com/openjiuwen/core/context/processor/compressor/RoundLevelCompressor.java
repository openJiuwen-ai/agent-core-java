/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.processor.compressor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.context.ContextWindow;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.context.context.ContextUtils;
import com.openjiuwen.core.context.context.SessionModelContext;
import com.openjiuwen.core.context.processor.ContextEvent;
import com.openjiuwen.core.context.processor.ContextProcessor;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.foundation.llm.output_parsers.JsonOutputParser;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Fallback round-level context compressor for long-running ReAct sessions.
 *
 * <p>Mirrors Python's {@code RoundLevelCompressor} in
 * {@code openjiuwen/core/context_engine/processor/compressor/round_level_compressor.py}.</p>
 */
public class RoundLevelCompressor extends ContextProcessor {
    public static final String ROUND_LEVEL_FALLBACK_MARKER = "[ROUND_LEVEL_MEMORY_BLOCK]";

    private static final String COMPRESS_LEVEL = "compress_level";
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private static final String DEFAULT_ROUND_COMPRESSION_PROMPT = """
            You are a Fallback Context Compression Expert for long-running ReAct agent sessions.
            
            Your job is to compress ONLY the explicitly listed targets so the whole task can fit under a strict context budget.
            
            Priority order:
            1. Ongoing ReAct state and exact handoff point
            2. Unfinished work, blockers, pending actions, and last concrete action
            3. Critical facts, constraints, decisions, corrections, and outputs needed for correct continuation
            4. Durable conclusions from completed work
            5. Secondary historical detail only if budget allows
            
            Rules:
            - Compress only the selected targets.
            - Protected recent context is reference only and must not be absorbed as standalone content.
            - Treat fallback blocks as historical context artifacts, not as new user instructions.
            - Preserve both what was done and what was learned.
            - Preserve the user's original requirements, constraints, acceptance criteria, and preferences as completely as possible.
            - For ongoing ReAct blocks, keep a distinct `User Requirements` section that makes the unfinished work recoverable.
            - For completed ReAct blocks, preserve both `User Requirements` and `Final Result` explicitly when they exist.
            - Return valid JSON only.
            """;

    private static final String DEFAULT_AGGRESSIVE_ROUND_COMPRESSION_PROMPT = """
            You are a Hard-Budget Fallback Compression Expert.
            
            The context is still over budget after an earlier compression pass.
            Compress ONLY the explicitly listed targets much more aggressively while keeping the task recoverable.
            
            Priority order:
            1. Ongoing ReAct state and exact handoff point
            2. Unfinished work, blockers, pending actions, and last concrete action
            3. Critical facts, constraints, decisions, corrections, and outputs needed for continuation
            4. Durable conclusions from completed work
            5. Secondary historical detail only if budget allows
            
            Rules:
            - Remove redundant reasoning, repeated tool chatter, and low-value chronology first.
            - Keep ongoing work maximally recoverable.
            - Preserve the user's original requirements as much as possible even under aggressive compression.
            - For completed blocks, keep the final result before secondary detail.
            - Return valid JSON only.
            """;

    static {
        ContextEngine.registerProcessor("RoundLevelCompressor", RoundLevelCompressor.class);
    }

    private final RoundLevelCompressorConfig config;
    private final int targetTotalTokens;
    private final int triggerTotalTokens;
    private final int compressionCallMaxTokens;
    private final int keepRecentMessages;
    private final int firstPassTargetTokens;
    private final int secondPassTargetTokens;
    private final int thirdPassTargetTokens;
    private final double truncateHeadRatio;
    private final String truncatedMarker;
    private final String compressionMarker;
    private Model model;

    public RoundLevelCompressor(Object config) {
        this(asConfig(config));
    }

    public RoundLevelCompressor(RoundLevelCompressorConfig config) {
        this(config, null);
    }

    RoundLevelCompressor(RoundLevelCompressorConfig config, Model model) {
        super(config == null ? new RoundLevelCompressorConfig() : config);
        this.config = config == null ? new RoundLevelCompressorConfig() : config;
        this.targetTotalTokens = this.config.getTargetTotalTokens();
        this.triggerTotalTokens = this.config.getTriggerTotalTokens();
        this.compressionCallMaxTokens = this.config.getCompressionCallMaxTokens();
        this.keepRecentMessages = this.config.getKeepRecentMessages();
        this.firstPassTargetTokens = this.config.getFirstPassTargetTokens();
        this.secondPassTargetTokens = this.config.getSecondPassTargetTokens();
        this.thirdPassTargetTokens = this.config.getThirdPassTargetTokens();
        this.truncateHeadRatio = this.config.getTruncateHeadRatio();
        this.truncatedMarker = this.config.getTruncatedMarker();
        this.compressionMarker = this.config.getCompressionMarker();
        this.model = model;
    }

    @Override
    public CompletionStage<Boolean> triggerAddMessages(SessionModelContext context, List<BaseMessage> messagesToAdd,
                                                       Map<String, Object> kwargs) {
        List<BaseMessage> contextMessages = new ArrayList<>(context.getMessages());
        contextMessages.addAll(messagesToAdd == null ? List.of() : messagesToAdd);
        int totalTokens = countContextWindowTokens(
                getMessageList(kwargs, "system_messages", "systemMessages"),
                contextMessages,
                getToolList(kwargs, "tools"),
                context);
        if (totalTokens > triggerTotalTokens) {
            Loggers.CONTEXT_ENGINE.info(
                    "[{} triggered] estimated context window tokens {} exceeds trigger_total_tokens {}",
                    processorType(),
                    totalTokens,
                    triggerTotalTokens);
            return CompletableFuture.completedFuture(true);
        }
        return CompletableFuture.completedFuture(false);
    }

    @Override
    public CompletionStage<SessionModelContext.ProcessResult> onAddMessages(SessionModelContext context,
                                                                            List<BaseMessage> messagesToAdd,
                                                                            boolean force,
                                                                            Map<String, Object> kwargs) {
        List<BaseMessage> incoming = messagesToAdd == null ? List.of() : messagesToAdd;
        List<BaseMessage> allMessages = new ArrayList<>(context.getMessages());
        allMessages.addAll(incoming);
        resetCompressionUsage();

        List<BaseMessage> compressedMessages = compressUntilTarget(
                allMessages,
                context,
                getMessageList(kwargs, "system_messages", "systemMessages"),
                getToolList(kwargs, "tools"),
                keepRecentMessages,
                force);
        if (compressedMessages.equals(allMessages)) {
            return CompletableFuture.completedFuture(new SessionModelContext.ProcessResult(null, incoming, null));
        }

        context.setMessages(compressedMessages, true);
        ContextEvent event = new ContextEvent(
                processorType(),
                indexRange(0, allMessages.size() - 1),
                extractCompactSummary(compressedMessages),
                currentCompressionUsage());
        return CompletableFuture.completedFuture(new SessionModelContext.ProcessResult(event, List.of(), null));
    }

    @Override
    public CompletionStage<Boolean> triggerGetContextWindow(SessionModelContext context, ContextWindow window,
                                                            Map<String, Object> kwargs) {
        int totalTokens = countContextWindowTokens(
                window.getSystemMessages(),
                window.getContextMessages(),
                window.getTools(),
                context);
        return CompletableFuture.completedFuture(totalTokens > triggerTotalTokens);
    }

    @Override
    public CompletionStage<SessionModelContext.ProcessResult> onGetContextWindow(SessionModelContext context,
                                                                                 ContextWindow window,
                                                                                 Map<String, Object> kwargs) {
        resetCompressionUsage();
        int totalTokens = countContextWindowTokens(
                window.getSystemMessages(),
                window.getContextMessages(),
                window.getTools(),
                context);
        if (totalTokens <= targetTotalTokens) {
            return CompletableFuture.completedFuture(new SessionModelContext.ProcessResult(null, null, window));
        }

        List<BaseMessage> originalContextMessages = window.getContextMessages();
        List<BaseMessage> compressedMessages = compressUntilTarget(
                originalContextMessages,
                context,
                window.getSystemMessages(),
                window.getTools(),
                0,
                false);
        window.setContextMessages(compressedMessages);
        context.setMessages(compressedMessages, true);
        ContextEvent event = new ContextEvent(
                processorType(),
                indexRange(0, originalContextMessages.size() - 1),
                extractCompactSummary(compressedMessages),
                currentCompressionUsage());
        return CompletableFuture.completedFuture(new SessionModelContext.ProcessResult(event, null, window));
    }

    protected List<BaseMessage> compressUntilTarget(List<BaseMessage> contextMessages,
                                                    SessionModelContext context,
                                                    List<BaseMessage> systemMessages,
                                                    List<ToolInfo> tools,
                                                    int keepRecent,
                                                    boolean force) {
        List<BaseMessage> working = new ArrayList<>(contextMessages == null ? List.of() : contextMessages);
        if (!force && isUnderContextWindowBudget(systemMessages, working, tools, context)) {
            return working;
        }

        List<BaseMessage> recursiveUpdated = runRecursiveCompression(working, context, systemMessages, tools,
                keepRecent);
        if (recursiveUpdated != null) {
            working = recursiveUpdated;
        }
        if (isUnderContextWindowBudget(systemMessages, working, tools, context)) {
            return working;
        }

        List<BaseMessage> aggressiveKeepRecent = runAggressivePhase(
                working,
                context,
                systemMessages,
                tools,
                keepRecent,
                secondPassTargetTokens,
                "aggressive_keep_recent");
        if (aggressiveKeepRecent != null) {
            working = aggressiveKeepRecent;
        }
        if (isUnderContextWindowBudget(systemMessages, working, tools, context)) {
            return working;
        }

        List<BaseMessage> aggressiveFull = runAggressivePhase(
                working,
                context,
                systemMessages,
                tools,
                0,
                thirdPassTargetTokens,
                "aggressive_full_context");
        if (aggressiveFull != null) {
            working = aggressiveFull;
        }
        if (isUnderContextWindowBudget(systemMessages, working, tools, context)) {
            return working;
        }
        return truncateToTarget(working, context, systemMessages, tools);
    }

    private List<BaseMessage> runRecursiveCompression(List<BaseMessage> messages,
                                                      SessionModelContext context,
                                                      List<BaseMessage> systemMessages,
                                                      List<ToolInfo> tools,
                                                      int keepRecent) {
        List<BaseMessage> working = new ArrayList<>(messages == null ? List.of() : messages);
        boolean changed = false;

        int compressEnd = working.size() - keepRecent - 1;
        if (compressEnd >= 0) {
            List<CompressTarget> rawTargets = buildRawTargets(working, compressEnd);
            if (!rawTargets.isEmpty()) {
                List<BaseMessage> updated = applyLlmPhase(
                        working,
                        context,
                        systemMessages,
                        tools,
                        rawTargets,
                        firstPassTargetTokens,
                        false,
                        "l0_to_l1",
                        keepRecent);
                if (updated != null) {
                    working = updated;
                    changed = true;
                }
            }
        }

        while (!isUnderContextWindowBudget(systemMessages, working, tools, context)) {
            compressEnd = working.size() - keepRecent - 1;
            if (compressEnd < 0) {
                break;
            }
            List<CompressTarget> mergeTargets = buildRecursiveMergeTargets(working, compressEnd);
            if (mergeTargets.isEmpty()) {
                break;
            }
            List<BaseMessage> updated = applyLlmPhase(
                    working,
                    context,
                    systemMessages,
                    tools,
                    mergeTargets,
                    firstPassTargetTokens,
                    false,
                    "recursive_merge_l" + mergeTargets.get(0).currentLevel()
                            + "_to_l" + mergeTargets.get(0).nextLevel(),
                    keepRecent);
            if (updated == null || updated.equals(working)) {
                break;
            }
            working = updated;
            changed = true;
        }
        return changed ? working : null;
    }

    private List<BaseMessage> runAggressivePhase(List<BaseMessage> messages,
                                                 SessionModelContext context,
                                                 List<BaseMessage> systemMessages,
                                                 List<ToolInfo> tools,
                                                 int keepRecent,
                                                 int targetTokens,
                                                 String phaseName) {
        int compressEnd = messages.size() - keepRecent - 1;
        if (compressEnd < 0) {
            return null;
        }
        List<CompressTarget> targets = buildAggressiveTargets(messages, compressEnd);
        if (targets.isEmpty()) {
            return null;
        }
        return applyLlmPhase(messages, context, systemMessages, tools, targets, targetTokens, true, phaseName,
                keepRecent);
    }

    List<CompressTarget> buildRawTargets(List<BaseMessage> messages, int compressEnd) {
        List<CompressTarget> targets = new ArrayList<>();
        int blockNo = 1;
        int cursor = 0;
        while (cursor <= compressEnd) {
            if (isRoundLevelFallbackBlock(messages.get(cursor))) {
                cursor = findRoundLevelBlockEnd(messages, cursor, compressEnd) + 1;
                continue;
            }
            int startIndex = cursor;
            BlockEnd blockEnd = findL0BlockEnd(messages, startIndex, compressEnd);
            if (blockEnd.endIndex() < startIndex) {
                cursor += 1;
                continue;
            }
            int endIndex = protectToolCallBoundary(messages, startIndex, blockEnd.endIndex());
            if (endIndex != blockEnd.endIndex() && endIndex <= startIndex) {
                break;
            }
            targets.add(new CompressTarget(
                    "block_" + blockNo,
                    blockEnd.scope(),
                    startIndex,
                    endIndex,
                    new ArrayList<>(messages.subList(startIndex, endIndex + 1)),
                    0,
                    1,
                    1));
            blockNo++;
            cursor = endIndex + 1;
        }
        return targets;
    }

    static int protectToolCallBoundary(List<BaseMessage> messages, int startIndex, int endIndex) {
        if (endIndex < startIndex) {
            return endIndex;
        }
        int protectedEndIndex = endIndex;
        Set<String> tailToolIds = new LinkedHashSet<>();
        for (BaseMessage message : messages.subList(endIndex + 1, messages.size())) {
            if (message instanceof ToolMessage toolMessage && notBlank(toolMessage.getToolCallId())) {
                tailToolIds.add(toolMessage.getToolCallId());
            }
        }
        if (tailToolIds.isEmpty()) {
            if (hasToolCalls(messages.get(endIndex))) {
                return endIndex - 1;
            }
            return endIndex;
        }

        for (int index = startIndex; index <= endIndex; index++) {
            BaseMessage message = messages.get(index);
            if (!hasToolCalls(message)) {
                continue;
            }
            Set<String> toolCallIds = new LinkedHashSet<>();
            for (ToolCall toolCall : ((AssistantMessage) message).getToolCalls()) {
                if (notBlank(toolCall.getId())) {
                    toolCallIds.add(toolCall.getId());
                }
            }
            for (String toolCallId : toolCallIds) {
                if (tailToolIds.contains(toolCallId)) {
                    protectedEndIndex = Math.min(protectedEndIndex, index - 1);
                    break;
                }
            }
        }

        if (protectedEndIndex == endIndex && hasToolCalls(messages.get(endIndex))) {
            protectedEndIndex = endIndex - 1;
        }
        return protectedEndIndex;
    }

    private BlockEnd findL0BlockEnd(List<BaseMessage> messages, int startIndex, int compressEnd) {
        int lastNonRoundLevelIndex = startIndex - 1;
        for (int index = startIndex; index <= compressEnd; index++) {
            if (isRoundLevelFallbackBlock(messages.get(index))) {
                break;
            }
            lastNonRoundLevelIndex = index;
            if (messages.get(index) instanceof AssistantMessage && !hasToolCalls(messages.get(index))) {
                return new BlockEnd(index, "completed_react");
            }
        }
        return new BlockEnd(lastNonRoundLevelIndex, "ongoing_react");
    }

    private List<CompressTarget> buildAggressiveTargets(List<BaseMessage> messages, int compressEnd) {
        List<CompressTarget> rawTargets = buildRawTargets(messages, compressEnd);
        if (!rawTargets.isEmpty()) {
            return rawTargets;
        }
        return collectRoundLevelMemoryTargets(messages, compressEnd);
    }

    private List<CompressTarget> collectRoundLevelMemoryTargets(List<BaseMessage> messages, int compressEnd) {
        List<CompressTarget> targets = new ArrayList<>();
        int blockNo = 1;
        int index = 0;
        while (index <= compressEnd) {
            if (!isRoundLevelFallbackBlock(messages.get(index))) {
                index++;
                continue;
            }
            int endIndex = findRoundLevelBlockEnd(messages, index, compressEnd);
            int level = 1;
            for (BaseMessage message : messages.subList(index, endIndex + 1)) {
                level = Math.max(level, getCompressLevel(message));
            }
            targets.add(new CompressTarget(
                    "memory_" + blockNo,
                    "existing_round_level_block",
                    index,
                    endIndex,
                    new ArrayList<>(messages.subList(index, endIndex + 1)),
                    level,
                    level + 1,
                    1));
            blockNo++;
            index = endIndex + 1;
        }
        return targets;
    }

    private List<CompressTarget> buildRecursiveMergeTargets(List<BaseMessage> messages, int compressEnd) {
        List<CompressTarget> memoryTargets = collectRoundLevelMemoryTargets(messages, compressEnd);
        if (memoryTargets.size() < 2) {
            return List.of();
        }
        Map<String, CompressTarget> targetById = new LinkedHashMap<>();
        for (CompressTarget target : memoryTargets) {
            targetById.put(target.blockId(), target);
        }
        EffectiveMergeLevels resolved = resolveEffectiveMergeLevels(memoryTargets);
        if (resolved.candidateLevel() == null) {
            return List.of();
        }
        List<CompressTarget> selectedTargets = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : resolved.effectiveLevels().entrySet()) {
            if (Objects.equals(entry.getValue(), resolved.candidateLevel())) {
                selectedTargets.add(targetById.get(entry.getKey()));
            }
        }
        selectedTargets.sort(Comparator.comparingInt(CompressTarget::startIndex));

        List<CompressTarget> mergedTargets = new ArrayList<>();
        List<CompressTarget> group = new ArrayList<>();
        for (CompressTarget target : selectedTargets) {
            if (group.isEmpty()) {
                group.add(target);
                continue;
            }
            if (target.startIndex() == group.get(group.size() - 1).endIndex() + 1) {
                group.add(target);
                continue;
            }
            if (group.size() >= 2) {
                mergedTargets.add(buildMergeTarget(group, messages, resolved.candidateLevel(),
                        mergedTargets.size() + 1));
            }
            group = new ArrayList<>(List.of(target));
        }
        if (group.size() >= 2) {
            mergedTargets.add(buildMergeTarget(group, messages, resolved.candidateLevel(),
                    mergedTargets.size() + 1));
        }
        return mergedTargets;
    }

    private CompressTarget buildMergeTarget(List<CompressTarget> group,
                                            List<BaseMessage> messages,
                                            int candidateLevel,
                                            int groupNo) {
        int startIndex = group.get(0).startIndex();
        int endIndex = group.get(group.size() - 1).endIndex();
        return new CompressTarget(
                "merge_" + candidateLevel + "_" + groupNo,
                "recursive_merge",
                startIndex,
                endIndex,
                new ArrayList<>(messages.subList(startIndex, endIndex + 1)),
                candidateLevel,
                candidateLevel + 1,
                group.size());
    }

    private EffectiveMergeLevels resolveEffectiveMergeLevels(List<CompressTarget> memoryTargets) {
        Map<String, Integer> effectiveLevels = new LinkedHashMap<>();
        for (CompressTarget target : memoryTargets) {
            effectiveLevels.put(target.blockId(), Math.max(target.currentLevel(), 1));
        }
        while (true) {
            Map<Integer, Integer> levelCounts = new TreeMap<>();
            for (Integer level : effectiveLevels.values()) {
                levelCounts.put(level, levelCounts.getOrDefault(level, 0) + 1);
            }
            if (levelCounts.isEmpty()) {
                return new EffectiveMergeLevels(effectiveLevels, null);
            }
            int highestLevel = levelCounts.keySet().stream().mapToInt(Integer::intValue).max().orElse(1);
            boolean changed = false;
            for (Integer level : new ArrayList<>(levelCounts.keySet())) {
                if (level == highestLevel || levelCounts.get(level) != 1) {
                    continue;
                }
                Integer nextHigherLevel = levelCounts.keySet().stream()
                        .filter(candidate -> candidate > level)
                        .findFirst()
                        .orElse(null);
                if (nextHigherLevel == null) {
                    continue;
                }
                for (Map.Entry<String, Integer> entry : effectiveLevels.entrySet()) {
                    if (Objects.equals(entry.getValue(), level)) {
                        effectiveLevels.put(entry.getKey(), nextHigherLevel);
                        changed = true;
                        break;
                    }
                }
                break;
            }
            if (changed) {
                continue;
            }
            Integer candidateLevel = levelCounts.entrySet().stream()
                    .filter(entry -> entry.getValue() >= 2)
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(null);
            return new EffectiveMergeLevels(effectiveLevels, candidateLevel);
        }
    }

    private List<BaseMessage> applyLlmPhase(List<BaseMessage> messages,
                                            SessionModelContext context,
                                            List<BaseMessage> systemMessages,
                                            List<ToolInfo> tools,
                                            List<CompressTarget> targets,
                                            int targetTokens,
                                            boolean aggressive,
                                            String phaseName,
                                            int keepRecent) {
        List<BaseMessage> modelMessages = prepareRoundCompressionMessages(
                messages,
                targets,
                context,
                phaseName,
                targetTokens,
                aggressive,
                keepRecent,
                systemMessages,
                tools);
        if (modelMessages == null) {
            Loggers.CONTEXT_ENGINE.warning(
                    "[RoundLevelCompressor] phase={} skipped because compression call budget is impossible",
                    phaseName);
            return null;
        }

        AssistantMessage response;
        try {
            response = getModel().invoke(
                    modelMessages,
                    ModelInvokeOptions.builder().outputParser(new JsonOutputParser()).build())
                    .toCompletableFuture()
                    .join();
            recordCompressionUsage(response);
        } catch (RuntimeException ex) {
            throw ErrorHelper.buildError(
                    StatusCode.MODEL_CALL_FAILED,
                    processorType() + " failed to invoke compression model during phase=" + phaseName,
                    null,
                    ex,
                    Map.of("error_msg", processorType()
                            + " failed to invoke compression model during phase=" + phaseName));
        }

        Object parserContent = response == null ? null : response.getParserContent();
        if (parserContent == null && response != null && !response.getContentAsString().isBlank()) {
            parserContent = new JsonOutputParser().parse(response).join();
        }
        List<Replacement> replacements = buildJsonReplacements(context, targets, parserContent);
        if (replacements.isEmpty() && response != null && !response.getContentAsString().strip().isEmpty()) {
            Replacement fallback = buildRawFallbackReplacement(context, targets, response.getContentAsString().strip());
            if (fallback != null) {
                replacements = List.of(fallback);
            }
        }
        if (replacements.isEmpty()) {
            Loggers.CONTEXT_ENGINE.warning("[RoundLevelCompressor] phase={} produced no valid replacements",
                    phaseName);
            return null;
        }

        List<BaseMessage> updatedMessages = applyReplacements(messages, replacements);
        Loggers.CONTEXT_ENGINE.info(
                "[RoundLevelCompressor] phase={} context_window_tokens {} -> {}",
                phaseName,
                countContextWindowTokens(systemMessages, messages, tools, context),
                countContextWindowTokens(systemMessages, updatedMessages, tools, context));
        return updatedMessages;
    }

    private List<BaseMessage> prepareRoundCompressionMessages(List<BaseMessage> contextMessages,
                                                              List<CompressTarget> targets,
                                                              SessionModelContext context,
                                                              String phaseName,
                                                              int targetTokens,
                                                              boolean aggressive,
                                                              int keepRecent,
                                                              List<BaseMessage> systemMessages,
                                                              List<ToolInfo> tools) {
        String systemPrompt = aggressive ? DEFAULT_AGGRESSIVE_ROUND_COMPRESSION_PROMPT
                : DEFAULT_ROUND_COMPRESSION_PROMPT;
        String promptText = buildCompressionUserPrompt(
                contextMessages,
                targets,
                context,
                phaseName,
                targetTokens,
                keepRecent,
                systemMessages,
                tools);
        if (isUnderCompressionCallBudget(systemPrompt, promptText, context)) {
            return List.of(new SystemMessage(systemPrompt), new UserMessage(promptText));
        }
        String compactPrompt = truncatePromptToBudget(systemPrompt, promptText, context);
        if (compactPrompt == null) {
            return null;
        }
        return List.of(new SystemMessage(systemPrompt), new UserMessage(compactPrompt));
    }

    String buildCompressionUserPrompt(List<BaseMessage> contextMessages,
                                      List<CompressTarget> targets,
                                      SessionModelContext context,
                                      String phaseName,
                                      int targetTokens,
                                      int keepRecent,
                                      List<BaseMessage> systemMessages,
                                      List<ToolInfo> tools) {
        Set<Integer> targetIndices = new LinkedHashSet<>();
        int firstTargetIndex = Integer.MAX_VALUE;
        int lastTargetIndex = Integer.MIN_VALUE;
        for (CompressTarget target : targets) {
            firstTargetIndex = Math.min(firstTargetIndex, target.startIndex());
            lastTargetIndex = Math.max(lastTargetIndex, target.endIndex());
            for (int index = target.startIndex(); index <= target.endIndex(); index++) {
                targetIndices.add(index);
            }
        }
        int protectedRecentStart = Math.max(contextMessages.size() - keepRecent, lastTargetIndex + 1);

        List<String> referenceLines = new ArrayList<>();
        for (int index = 0; index < contextMessages.size(); index++) {
            if (!targetIndices.contains(index) && index < protectedRecentStart) {
                referenceLines.add(serializeMessage(index, contextMessages.get(index)));
            }
        }

        List<String> targetLines = new ArrayList<>();
        for (CompressTarget target : targets) {
            targetLines.add("[Block: " + target.blockId() + "]");
            targetLines.add("- scope: " + target.scope());
            targetLines.add("- replace_range: [" + target.startIndex() + ", " + target.endIndex() + "]");
            targetLines.add("- current_level: l" + target.currentLevel());
            targetLines.add("- next_level: l" + target.nextLevel());
            targetLines.add("- source_block_count: " + target.sourceBlockCount());
            for (int offset = 0; offset < target.messages().size(); offset++) {
                targetLines.add(serializeMessage(target.startIndex() + offset, target.messages().get(offset)));
            }
            targetLines.add("");
        }

        List<String> recentLines = new ArrayList<>();
        for (int index = protectedRecentStart; index < contextMessages.size(); index++) {
            recentLines.add(serializeMessage(index, contextMessages.get(index)));
        }
        int currentWindowTokens = countContextWindowTokens(systemMessages, contextMessages, tools, context);

        return String.join("\n", List.of(
                "[Compression Task]",
                "- phase: " + phaseName,
                "- target_summary_tokens: " + targetTokens,
                "- keep_recent_messages: " + keepRecent,
                "- selected_blocks: " + targets.size(),
                "- current_context_window_tokens: " + currentWindowTokens,
                "- compression_call_budget_limit: " + compressionCallMaxTokens,
                "- selected_range: [" + firstTargetIndex + ", " + lastTargetIndex + "]",
                "",
                "[Reference Context]",
                referenceLines.isEmpty() ? "(none)" : String.join("\n", referenceLines),
                "",
                "[Selected Targets]",
                rstrip(targetLines.isEmpty() ? "(none)" : String.join("\n", targetLines)),
                "",
                "[Protected Recent Context]",
                recentLines.isEmpty() ? "(none)" : String.join("\n", recentLines),
                "",
                "[Output Contract]",
                "- Return valid JSON only.",
                "- Use schema: {\"blocks\": [{\"block_id\": \"...\", \"summary\": \"...\"}]}",
                "- Emit exactly one summary for each selected block_id.",
                "- Do not emit undeclared block_ids.",
                "- Target content must appear only in [Selected Targets], not elsewhere.",
                "- Preserve the user's original requirements, constraints, "
                        + "acceptance criteria, and preferences as completely as possible.",
                "- Do not weaken or over-compress the user's original request unless absolutely necessary.",
                "- If a selected block is ongoing_react, include a distinct "
                        + "`User Requirements` section tied to the unfinished work.",
                "- If a selected block is completed_react, explicitly preserve "
                        + "both `User Requirements` and `Final Result` when they exist."));
    }

    private String truncatePromptToBudget(String systemPrompt, String promptText, SessionModelContext context) {
        String minimumPrompt = "[Compression Task]\n...[TRUNCATED]...\n[Output Contract]\nReturn valid JSON only.";
        if (!isUnderCompressionCallBudget(systemPrompt, minimumPrompt, context)) {
            return null;
        }
        int low = 0;
        int high = promptText.length();
        String best = minimumPrompt;
        while (low <= high) {
            int middle = (low + high) / 2;
            String candidate = buildHeadTailTruncatedText(promptText, middle);
            if (isUnderCompressionCallBudget(systemPrompt, candidate, context)) {
                best = candidate;
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }
        return best;
    }

    private List<Replacement> buildJsonReplacements(SessionModelContext context,
                                                    List<CompressTarget> targets,
                                                    Object parserContent) {
        if (!isValidBlocksPayload(parserContent)) {
            return List.of();
        }
        Map<String, String> blockMap = new LinkedHashMap<>();
        Object rawBlocks = ((Map<?, ?>) parserContent).get("blocks");
        for (Object item : (List<?>) rawBlocks) {
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
        for (CompressTarget target : targets) {
            String summary = blockMap.get(target.blockId());
            if (summary == null || summary.isBlank()) {
                continue;
            }
            BaseMessage replacementMessage = buildMemoryMessage(summary, target);
            List<BaseMessage> replacementMessages = List.of(replacementMessage);
            if (!hasCompressionBenefit(context, target.messages(), replacementMessages)) {
                continue;
            }
            replacements.add(new Replacement(target.startIndex(), target.endIndex(), replacementMessages));
        }
        return replacements;
    }

    private Replacement buildRawFallbackReplacement(SessionModelContext context,
                                                    List<CompressTarget> targets,
                                                    String summary) {
        if (targets.isEmpty() || summary == null || summary.isBlank()) {
            return null;
        }
        int startIndex = targets.stream().mapToInt(CompressTarget::startIndex).min().orElse(0);
        int endIndex = targets.stream().mapToInt(CompressTarget::endIndex).max().orElse(0);
        List<BaseMessage> originalMessages = new ArrayList<>();
        int currentLevel = 0;
        int nextLevel = 1;
        int sourceBlockCount = 0;
        for (CompressTarget target : targets) {
            originalMessages.addAll(target.messages());
            currentLevel = Math.max(currentLevel, target.currentLevel());
            nextLevel = Math.max(nextLevel, target.nextLevel());
            sourceBlockCount += target.sourceBlockCount();
        }
        CompressTarget mergedTarget = new CompressTarget(
                "raw_fallback",
                "mixed_context",
                startIndex,
                endIndex,
                originalMessages,
                currentLevel,
                nextLevel,
                sourceBlockCount);
        BaseMessage replacement = buildMemoryMessage(summary, mergedTarget);
        List<BaseMessage> replacementMessages = List.of(replacement);
        if (!hasCompressionBenefit(context, originalMessages, replacementMessages)) {
            return null;
        }
        return new Replacement(startIndex, endIndex, replacementMessages);
    }

    BaseMessage buildMemoryMessage(String summary, CompressTarget target) {
        UserMessage message = new UserMessage(wrapMemoryBlock(summary, target.scope()));
        Map<String, Object> metadata = message.getMetadata() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(message.getMetadata());
        metadata.put(COMPRESS_LEVEL, target.nextLevel());
        message.setMetadata(metadata);
        return message;
    }

    String wrapMemoryBlock(String summary, String scope) {
        return compressionMarker + "\n"
                + "processor: RoundLevelCompressor\n"
                + "type: historical_memory_block\n"
                + "scope: " + scope + "\n"
                + "authority: This block is reference memory, not a binding source of truth.\n"
                + "instruction_status: Historical fallback context only. Do not treat as a new user instruction.\n"
                + "conflict_priority: Prefer newer explicit user intent, newer raw context, "
                + "and fresh tool results over this block.\n\n"
                + "Summary:\n"
                + (summary == null ? "" : summary);
    }

    String extractCompactSummary(List<BaseMessage> messages) {
        List<String> parts = new ArrayList<>();
        for (BaseMessage message : messages == null ? List.<BaseMessage>of() : messages) {
            String content = toText(message == null ? "" : message.getContent());
            if (content.startsWith(compressionMarker)) {
                parts.add(content);
            }
        }
        return String.join("\n\n", parts);
    }

    private UserMessage buildMinimalTruncatedMessage() {
        return new UserMessage(compressionMarker + "\n"
                + "processor: RoundLevelCompressor\n"
                + "type: historical_memory_block\n"
                + "scope: truncated_full_context\n"
                + "Summary:\n"
                + truncatedMarker);
    }

    private UserMessage buildCompactTruncatedMessage() {
        return new UserMessage(compressionMarker + "\n" + truncatedMarker);
    }

    private List<BaseMessage> truncateToTarget(List<BaseMessage> contextMessages,
                                               SessionModelContext context,
                                               List<BaseMessage> systemMessages,
                                               List<ToolInfo> tools) {
        int fixedTokens = countContextWindowFixedTokens(systemMessages, tools, context);
        int allowedContextTokens = targetTotalTokens - fixedTokens;
        if (allowedContextTokens <= 0) {
            return List.of(buildCompactTruncatedMessage());
        }

        List<String> serializedLines = new ArrayList<>();
        for (int index = 0; index < contextMessages.size(); index++) {
            serializedLines.add(serializeMessage(index, contextMessages.get(index)));
        }
        String serialized = String.join("\n", serializedLines);
        if (serialized.isEmpty()) {
            return contextMessages;
        }

        int low = 0;
        int high = serialized.length();
        List<BaseMessage> bestMessages = List.of();
        while (low <= high) {
            int middle = (low + high) / 2;
            String candidateContent = wrapMemoryBlock(
                    buildHeadTailTruncatedText(serialized, middle),
                    "truncated_full_context");
            List<BaseMessage> candidateMessages = List.of(new UserMessage(candidateContent));
            int candidateTokens = countContextWindowTokens(systemMessages, candidateMessages, tools, context);
            if (candidateTokens <= targetTotalTokens) {
                bestMessages = candidateMessages;
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }
        if (!bestMessages.isEmpty()) {
            return bestMessages;
        }
        UserMessage minimalMessage = buildMinimalTruncatedMessage();
        int minimalTokens = countContextWindowTokens(systemMessages, List.of(minimalMessage), tools, context);
        if (minimalTokens <= targetTotalTokens) {
            return List.of(minimalMessage);
        }
        return List.of(buildCompactTruncatedMessage());
    }

    String buildHeadTailTruncatedText(String text, int keptChars) {
        if (keptChars <= 0) {
            return truncatedMarker;
        }
        int headChars = Math.max((int) (keptChars * truncateHeadRatio), 0);
        int tailChars = Math.max(keptChars - headChars, 0);
        String head = text.substring(0, Math.min(headChars, text.length()));
        String tail = tailChars > 0 ? text.substring(Math.max(text.length() - tailChars, 0)) : "";
        if (!head.isEmpty() && !tail.isEmpty()) {
            return head + "\n" + truncatedMarker + "\n" + tail;
        }
        if (!head.isEmpty()) {
            return head;
        }
        return tail.isEmpty() ? truncatedMarker : tail;
    }

    int countContextWindowTokens(List<BaseMessage> systemMessages,
                                 List<BaseMessage> contextMessages,
                                 List<ToolInfo> tools,
                                 SessionModelContext context) {
        ModelContext.TokenCounterPort tokenCounter = context == null ? null : context.tokenCounter();
        List<BaseMessage> allMessages = new ArrayList<>();
        allMessages.addAll(systemMessages == null ? List.of() : systemMessages);
        allMessages.addAll(contextMessages == null ? List.of() : contextMessages);
        if (tokenCounter != null) {
            try {
                int total = tokenCounter.countTokens(allMessages);
                total += countToolsWithCounterOrEstimate(tools, tokenCounter);
                return total;
            } catch (RuntimeException ex) {
                Loggers.CONTEXT_ENGINE.warning("[{}] token_counter failed, fallback to estimate: {}",
                        processorType(),
                        ex.toString());
            }
        }
        int total = 0;
        for (BaseMessage message : allMessages) {
            total += estimateContentTokens(message == null ? "" : message.getContent());
        }
        for (ToolInfo tool : tools == null ? List.<ToolInfo>of() : tools) {
            total += estimateContentTokens(serializeTool(tool));
        }
        return total;
    }

    private int countContextWindowFixedTokens(List<BaseMessage> systemMessages,
                                              List<ToolInfo> tools,
                                              SessionModelContext context) {
        return countContextWindowTokens(systemMessages, List.of(), tools, context);
    }

    private int countCompressionCallTokens(String systemPrompt, String promptText, SessionModelContext context) {
        ModelContext.TokenCounterPort tokenCounter = context == null ? null : context.tokenCounter();
        List<BaseMessage> messages = List.of(new SystemMessage(systemPrompt), new UserMessage(promptText));
        if (tokenCounter != null) {
            try {
                return tokenCounter.countTokens(messages);
            } catch (RuntimeException ex) {
                Loggers.CONTEXT_ENGINE.warning("[{}] compression token counting fallback: {}",
                        processorType(),
                        ex.toString());
            }
        }
        int total = 0;
        for (BaseMessage message : messages) {
            total += estimateContentTokens(message.getContent());
        }
        return total;
    }

    private boolean isUnderContextWindowBudget(List<BaseMessage> systemMessages,
                                               List<BaseMessage> contextMessages,
                                               List<ToolInfo> tools,
                                               SessionModelContext context) {
        return countContextWindowTokens(systemMessages, contextMessages, tools, context) <= targetTotalTokens;
    }

    private boolean isUnderCompressionCallBudget(String systemPrompt, String promptText,
                                                 SessionModelContext context) {
        return countCompressionCallTokens(systemPrompt, promptText, context) <= compressionCallMaxTokens;
    }

    private boolean hasCompressionBenefit(SessionModelContext context,
                                          List<BaseMessage> originalMessages,
                                          List<BaseMessage> replacementMessages) {
        int originalTokens = countMessageTokens(originalMessages, context);
        int replacementTokens = countMessageTokens(replacementMessages, context);
        return originalTokens > replacementTokens;
    }

    private int countMessageTokens(List<BaseMessage> messages, SessionModelContext context) {
        ModelContext.TokenCounterPort tokenCounter = context == null ? null : context.tokenCounter();
        List<BaseMessage> safeMessages = messages == null ? List.of() : messages;
        if (tokenCounter != null) {
            try {
                return tokenCounter.countTokens(safeMessages);
            } catch (RuntimeException ex) {
                Loggers.CONTEXT_ENGINE.warning("[{}] token_counter failed, fallback to estimate: {}",
                        processorType(),
                        ex.toString());
            }
        }
        int total = 0;
        for (BaseMessage message : safeMessages) {
            total += estimateContentTokens(message == null ? "" : message.getContent());
        }
        return total;
    }

    String serializeMessage(int index, BaseMessage message) {
        List<String> parts = new ArrayList<>();
        parts.add("[" + index + "] role=" + message.getRole());
        if (hasToolCalls(message)) {
            parts.add("tool_calls=" + String.join(", ", ((AssistantMessage) message).getToolCalls().stream()
                    .map(call -> call.getName() == null ? "" : call.getName())
                    .toList()));
        }
        if (message instanceof ToolMessage toolMessage) {
            parts.add("tool_call_id=" + toolMessage.getToolCallId());
        }
        int level = getCompressLevel(message);
        if (level > 0) {
            parts.add("compress_level=l" + level);
        }
        parts.add("content=" + toText(message.getContent()));
        return String.join(" | ", parts);
    }

    private String serializeTool(ToolInfo tool) {
        try {
            return JSON_MAPPER.writeValueAsString(tool);
        } catch (JsonProcessingException ex) {
            return String.valueOf(tool);
        }
    }

    private static int estimateContentTokens(Object content) {
        if (content instanceof String text) {
            return text.length() / 3;
        }
        try {
            return JSON_MAPPER.writeValueAsString(content).length() / 3;
        } catch (JsonProcessingException ex) {
            return String.valueOf(content).length() / 3;
        }
    }

    private static String toText(Object content) {
        return content instanceof String text ? text : String.valueOf(content);
    }

    boolean isRoundLevelFallbackBlock(BaseMessage message) {
        return message instanceof UserMessage && toText(message.getContent()).startsWith(compressionMarker);
    }

    private int findRoundLevelBlockEnd(List<BaseMessage> messages, int start, int compressEnd) {
        int endIndex = start;
        while (endIndex + 1 <= compressEnd
                && messages.get(endIndex + 1) instanceof AssistantMessage
                && !hasToolCalls(messages.get(endIndex + 1))
                && looksLikeAck(messages.get(endIndex + 1))) {
            endIndex++;
        }
        return endIndex;
    }

    private static boolean looksLikeAck(BaseMessage message) {
        return message instanceof AssistantMessage
                && "Understood. I have recorded this compressed context."
                .equals(toText(message.getContent()).strip());
    }

    private static boolean isValidBlocksPayload(Object parserContent) {
        return parserContent instanceof Map<?, ?> map && map.get("blocks") instanceof List<?>;
    }

    private List<BaseMessage> applyReplacements(List<BaseMessage> messages, List<Replacement> replacements) {
        List<BaseMessage> updated = new ArrayList<>(messages);
        List<Replacement> sorted = new ArrayList<>(replacements);
        sorted.sort(Comparator.comparingInt(Replacement::startIndex).reversed());
        for (Replacement replacement : sorted) {
            updated = ContextUtils.replaceMessages(
                    updated,
                    replacement.replacementMessages(),
                    replacement.startIndex(),
                    replacement.endIndex());
        }
        return updated;
    }

    int getCompressLevel(BaseMessage message) {
        Map<String, Object> metadata = message.getMetadata();
        if (metadata != null) {
            Object level = metadata.get(COMPRESS_LEVEL);
            if (level instanceof Number number) {
                return number.intValue();
            }
            if (level instanceof String text && !text.isBlank()) {
                try {
                    return Integer.parseInt(text);
                } catch (NumberFormatException ignored) {
                    return 0;
                }
            }
        }
        if (isRoundLevelFallbackBlock(message)) {
            return 1;
        }
        return 0;
    }

    private Model getModel() {
        if (model == null) {
            model = new Model(config.getModelClient(), config.getModel());
        }
        return model;
    }

    @Override
    public void loadState(Map<String, Object> state) {
    }

    @Override
    public Map<String, Object> saveState() {
        return Map.of();
    }

    private static int countToolsWithCounterOrEstimate(List<ToolInfo> tools,
                                                       ModelContext.TokenCounterPort tokenCounter) {
        List<ToolInfo> safeTools = tools == null ? List.of() : tools;
        if (safeTools.isEmpty()) {
            return 0;
        }
        if (tokenCounter instanceof SessionModelContext.ToolTokenCounterPort toolCounter) {
            return toolCounter.countTools(safeTools);
        }
        int total = 0;
        for (ToolInfo tool : safeTools) {
            String text = String.valueOf(tool.getName()) + " " + String.valueOf(tool.getDescription())
                    + " " + String.valueOf(tool.getParameters());
            total += estimateContentTokens(text);
        }
        return total;
    }

    @SuppressWarnings("unchecked")
    private static List<BaseMessage> getMessageList(Map<String, Object> kwargs, String... keys) {
        if (kwargs == null) {
            return List.of();
        }
        for (String key : keys) {
            Object value = kwargs.get(key);
            if (value instanceof List<?> list && list.stream().allMatch(BaseMessage.class::isInstance)) {
                return (List<BaseMessage>) list;
            }
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private static List<ToolInfo> getToolList(Map<String, Object> kwargs, String key) {
        if (kwargs == null) {
            return List.of();
        }
        Object value = kwargs.get(key);
        if (value instanceof List<?> list && list.stream().allMatch(ToolInfo.class::isInstance)) {
            return (List<ToolInfo>) list;
        }
        return List.of();
    }

    private static boolean hasToolCalls(BaseMessage message) {
        return message instanceof AssistantMessage assistantMessage
                && assistantMessage.getToolCalls() != null
                && !assistantMessage.getToolCalls().isEmpty();
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static List<Integer> indexRange(int startIndex, int endIndex) {
        List<Integer> values = new ArrayList<>();
        for (int index = startIndex; index <= endIndex; index++) {
            values.add(index);
        }
        return values;
    }

    private static String rstrip(String value) {
        int end = value.length();
        while (end > 0 && Character.isWhitespace(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(0, end);
    }

    private static RoundLevelCompressorConfig asConfig(Object config) {
        if (config == null) {
            return new RoundLevelCompressorConfig();
        }
        if (config instanceof RoundLevelCompressorConfig roundLevelConfig) {
            return roundLevelConfig;
        }
        throw new IllegalArgumentException("RoundLevelCompressor requires RoundLevelCompressorConfig");
    }

    /**
     * Compression target metadata.
     *
     * <p>Mirrors Python's {@code _CompressTarget} in
     * {@code openjiuwen/core/context_engine/processor/compressor/round_level_compressor.py}.</p>
     */
    record CompressTarget(String blockId, String scope, int startIndex, int endIndex,
                          List<BaseMessage> messages, int currentLevel, int nextLevel,
                          int sourceBlockCount) {
    }

    private record BlockEnd(int endIndex, String scope) {
    }

    private record EffectiveMergeLevels(Map<String, Integer> effectiveLevels, Integer candidateLevel) {
    }

    private record Replacement(int startIndex, int endIndex, List<BaseMessage> replacementMessages) {
    }
}
