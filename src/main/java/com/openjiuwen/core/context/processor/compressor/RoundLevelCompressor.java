/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.processor.compressor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.context.ContextWindow;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.context.context.ContextUtils;
import com.openjiuwen.core.context.processor.ContextEvent;
import com.openjiuwen.core.context.processor.ContextProcessor;
import com.openjiuwen.core.context.token.TokenCounter;
import com.openjiuwen.core.foundation.llm.Model;
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
import java.util.List;
import java.util.Map;

/**
 * Token-budget driven round-level fallback compressor.
 */
public class RoundLevelCompressor extends ContextProcessor {

    static final String COMPRESS_LEVEL = "compress_level";
    /**
     * Auto-generated for codecheck compliance.
     */
    public static final String ROUND_LEVEL_FALLBACK_MARKER = "[ROUND_LEVEL_MEMORY_BLOCK]";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String DEFAULT_ROUND_COMPRESSION_PROMPT = """
            You are a Fallback Context Compression Expert for long-running ReAct agent sessions.

            Your job is to compress ONLY the explicitly listed targets so the whole task can fit under a strict
            context budget.

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
            - Preserve the user's original requirements, constraints, acceptance criteria, and preferences as completely
              as possible.
            - For ongoing ReAct blocks, keep a distinct `User Requirements` section that makes the unfinished work
              recoverable.
            - For completed ReAct blocks, preserve both `User Requirements` and `Final Result` explicitly when they
              exist.
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

    /**
     * Auto-generated for codecheck compliance.
     */
    public RoundLevelCompressor(RoundLevelCompressorConfig config) {
        super(config);
        config.validate();
        this.targetTotalTokens = config.getTargetTotalTokens();
        this.triggerTotalTokens = config.getTriggerTotalTokens();
        this.compressionCallMaxTokens = config.getCompressionCallMaxTokens();
        this.keepRecentMessages = config.getKeepRecentMessages();
        this.firstPassTargetTokens = config.getFirstPassTargetTokens();
        this.secondPassTargetTokens = config.getSecondPassTargetTokens();
        this.thirdPassTargetTokens = config.getThirdPassTargetTokens();
        this.truncateHeadRatio = config.getTruncateHeadRatio();
        this.truncatedMarker = config.getTruncatedMarker();
        this.compressionMarker = config.getCompressionMarker();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean triggerAddMessages(ModelContext context, List<BaseMessage> messagesToAdd) {
        List<BaseMessage> allMessages = new ArrayList<>(context.getMessages());
        if (messagesToAdd != null) {
            allMessages.addAll(messagesToAdd);
        }
        int totalTokens = countContextWindowTokens(null, allMessages, null, context);
        if (totalTokens > triggerTotalTokens) {
            Loggers.CONTEXT_ENGINE.info("[" + processorType()
                    + " triggered] estimated context window tokens " + totalTokens
                    + " exceeds trigger_total_tokens " + triggerTotalTokens);
            return true;
        }
        return false;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public ProcessResult onAddMessages(ModelContext context, List<BaseMessage> messagesToAdd) {
        List<BaseMessage> allMessages = new ArrayList<>(context.getMessages());
        if (messagesToAdd != null) {
            allMessages.addAll(messagesToAdd);
        }
        List<BaseMessage> compressedMessages = compressUntilTarget(
                allMessages,
                context,
                null,
                null,
                keepRecentMessages,
                false);
        if (compressedMessages.equals(allMessages)) {
            return ProcessResult.ofMessages(null, messagesToAdd);
        }
        context.setMessages(compressedMessages);
        ContextEvent event = ContextEvent.builder()
                .eventType(processorType())
                .messagesToModify(range(0, allMessages.size() - 1))
                .build();
        return ProcessResult.ofMessages(event, List.of());
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean triggerGetContextWindow(ModelContext context, ContextWindow contextWindow) {
        return countContextWindowTokens(
                contextWindow.getSystemMessages(),
                contextWindow.getContextMessages(),
                contextWindow.getTools(),
                context) > triggerTotalTokens;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public ProcessResult onGetContextWindow(ModelContext context, ContextWindow contextWindow) {
        int totalTokens = countContextWindowTokens(
                contextWindow.getSystemMessages(),
                contextWindow.getContextMessages(),
                contextWindow.getTools(),
                context);
        if (totalTokens <= targetTotalTokens) {
            return ProcessResult.ofContextWindow(null, contextWindow);
        }

        List<BaseMessage> compressedMessages = compressUntilTarget(
                contextWindow.getContextMessages(),
                context,
                contextWindow.getSystemMessages(),
                contextWindow.getTools(),
                0,
                false);
        int originalContextLen = contextWindow.getContextMessages().size();
        contextWindow.setContextMessages(compressedMessages);
        context.setMessages(compressedMessages);
        ContextEvent event = ContextEvent.builder()
                .eventType(processorType())
                .messagesToModify(range(0, originalContextLen - 1))
                .build();
        return ProcessResult.ofContextWindow(event, contextWindow);
    }

    List<BaseMessage> compressUntilTarget(
            List<BaseMessage> contextMessages,
            ModelContext context,
            List<BaseMessage> systemMessages,
            List<ToolInfo> tools,
            int keepRecent,
            boolean isForce) {
        List<BaseMessage> working = new ArrayList<>(contextMessages);
        if (!isForce && isUnderContextWindowBudget(systemMessages, working, tools, context)) {
            return working;
        }

        List<BaseMessage> recursiveUpdated = runRecursiveCompression(
                working,
                context,
                systemMessages,
                tools,
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

    List<BaseMessage> runRecursiveCompression(
            List<BaseMessage> messages,
            ModelContext context,
            List<BaseMessage> systemMessages,
            List<ToolInfo> tools,
            int keepRecent) {
        List<BaseMessage> working = new ArrayList<>(messages);
        boolean isChanged = false;

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
                    isChanged = true;
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
            isChanged = true;
        }
        return isChanged ? working : null;
    }

    List<BaseMessage> runAggressivePhase(
            List<BaseMessage> messages,
            ModelContext context,
            List<BaseMessage> systemMessages,
            List<ToolInfo> tools,
            int keepRecent,
            int targetTokens,
            String phaseName) {
        int compressEnd = messages.size() - keepRecent - 1;
        if (compressEnd < 0) {
            return nullValue();
        }
        List<CompressTarget> targets = buildAggressiveTargets(messages, compressEnd);
        if (targets.isEmpty()) {
            return nullValue();
        }
        return applyLlmPhase(
                messages,
                context,
                systemMessages,
                tools,
                targets,
                targetTokens,
                true,
                phaseName,
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
            int startIdx = cursor;
            L0BlockEnd l0BlockEnd = findL0BlockEnd(messages, startIdx, compressEnd);
            int endIdx = l0BlockEnd.endIdx();
            if (endIdx < startIdx) {
                cursor++;
                continue;
            }
            int protectedEndIdx = protectToolCallBoundary(messages, startIdx, endIdx);
            if (protectedEndIdx != endIdx && protectedEndIdx <= startIdx) {
                break;
            }
            endIdx = protectedEndIdx;
            targets.add(new CompressTarget(
                    "block_" + blockNo,
                    l0BlockEnd.scope(),
                    startIdx,
                    endIdx,
                    new ArrayList<>(messages.subList(startIdx, endIdx + 1)),
                    0,
                    1,
                    1));
            blockNo++;
            cursor = endIdx + 1;
        }
        return targets;
    }

    static int protectToolCallBoundary(List<BaseMessage> messages, int startIdx, int endIdx) {
        if (endIdx < startIdx) {
            return endIdx;
        }
        int protectedEndIdx = endIdx;
        java.util.Set<String> tailToolIds = new java.util.LinkedHashSet<>();
        for (int index = endIdx + 1; index < messages.size(); index++) {
            BaseMessage message = messages.get(index);
            if (message instanceof ToolMessage toolMessage && toolMessage.getToolCallId() != null) {
                tailToolIds.add(toolMessage.getToolCallId());
            }
        }
        if (tailToolIds.isEmpty()) {
            if (messages.get(endIdx) instanceof AssistantMessage assistant
                    && assistant.getToolCalls() != null
                    && !assistant.getToolCalls().isEmpty()) {
                return endIdx - 1;
            }
            return endIdx;
        }
        for (int index = startIdx; index <= endIdx; index++) {
            BaseMessage message = messages.get(index);
            if (!(message instanceof AssistantMessage assistant)
                    || assistant.getToolCalls() == null
                    || assistant.getToolCalls().isEmpty()) {
                continue;
            }
            for (ToolCall toolCall : assistant.getToolCalls()) {
                if (toolCall.getId() != null && tailToolIds.contains(toolCall.getId())) {
                    protectedEndIdx = Math.min(protectedEndIdx, index - 1);
                }
            }
        }
        if (protectedEndIdx == endIdx
                && messages.get(endIdx) instanceof AssistantMessage assistant
                && assistant.getToolCalls() != null
                && !assistant.getToolCalls().isEmpty()) {
            protectedEndIdx = endIdx - 1;
        }
        return protectedEndIdx;
    }

    L0BlockEnd findL0BlockEnd(List<BaseMessage> messages, int startIdx, int compressEnd) {
        int lastNonRoundLevelIdx = startIdx - 1;
        for (int index = startIdx; index <= compressEnd; index++) {
            if (isRoundLevelFallbackBlock(messages.get(index))) {
                break;
            }
            lastNonRoundLevelIdx = index;
            if (messages.get(index) instanceof AssistantMessage assistant
                    && (assistant.getToolCalls() == null || assistant.getToolCalls().isEmpty())) {
                return new L0BlockEnd(index, "completed_react");
            }
        }
        return new L0BlockEnd(lastNonRoundLevelIdx, "ongoing_react");
    }

    List<CompressTarget> buildAggressiveTargets(List<BaseMessage> messages, int compressEnd) {
        List<CompressTarget> rawTargets = buildRawTargets(messages, compressEnd);
        if (!rawTargets.isEmpty()) {
            return rawTargets;
        }
        return collectRoundLevelMemoryTargets(messages, compressEnd);
    }

    List<CompressTarget> collectRoundLevelMemoryTargets(List<BaseMessage> messages, int compressEnd) {
        List<CompressTarget> targets = new ArrayList<>();
        int blockNo = 1;
        int index = 0;
        while (index <= compressEnd) {
            if (!isRoundLevelFallbackBlock(messages.get(index))) {
                index++;
                continue;
            }
            int endIdx = findRoundLevelBlockEnd(messages, index, compressEnd);
            int level = 1;
            for (int cursor = index; cursor <= endIdx; cursor++) {
                level = Math.max(level, getCompressLevel(messages.get(cursor)));
            }
            targets.add(new CompressTarget(
                    "memory_" + blockNo,
                    "existing_round_level_block",
                    index,
                    endIdx,
                    new ArrayList<>(messages.subList(index, endIdx + 1)),
                    level,
                    level + 1,
                    1));
            blockNo++;
            index = endIdx + 1;
        }
        return targets;
    }

    List<CompressTarget> buildRecursiveMergeTargets(List<BaseMessage> messages, int compressEnd) {
        List<CompressTarget> memoryTargets = collectRoundLevelMemoryTargets(messages, compressEnd);
        if (memoryTargets.size() < 2) {
            return List.of();
        }

        EffectiveMergeLevels isResolved = resolveEffectiveMergeLevels(memoryTargets);
        if (isResolved.candidateLevel() == null) {
            return List.of();
        }
        Map<String, CompressTarget> targetById = new LinkedHashMap<>();
        for (CompressTarget target : memoryTargets) {
            targetById.put(target.blockId(), target);
        }
        List<CompressTarget> selectedTargets = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : isResolved.effectiveLevels().entrySet()) {
            if (entry.getValue().equals(isResolved.candidateLevel())) {
                selectedTargets.add(targetById.get(entry.getKey()));
            }
        }
        selectedTargets.sort(Comparator.comparingInt(CompressTarget::startIdx));

        List<CompressTarget> mergedTargets = new ArrayList<>();
        List<CompressTarget> group = new ArrayList<>();
        for (CompressTarget target : selectedTargets) {
            if (group.isEmpty()) {
                group.add(target);
                continue;
            }
            if (target.startIdx() == group.get(group.size() - 1).endIdx() + 1) {
                group.add(target);
                continue;
            }
            if (group.size() >= 2) {
                mergedTargets.add(buildMergeTarget(
                        group,
                        messages,
                        isResolved.candidateLevel(),
                        mergedTargets.size() + 1));
            }
            group = new ArrayList<>();
            group.add(target);
        }
        if (group.size() >= 2) {
            mergedTargets.add(buildMergeTarget(
                    group,
                    messages,
                    isResolved.candidateLevel(),
                    mergedTargets.size() + 1));
        }
        return mergedTargets;
    }

    CompressTarget buildMergeTarget(
            List<CompressTarget> group,
            List<BaseMessage> messages,
            int candidateLevel,
            int groupNo) {
        CompressTarget first = group.get(0);
        CompressTarget last = group.get(group.size() - 1);
        return new CompressTarget(
                "merge_" + candidateLevel + "_" + groupNo,
                "recursive_merge",
                first.startIdx(),
                last.endIdx(),
                new ArrayList<>(messages.subList(first.startIdx(), last.endIdx() + 1)),
                candidateLevel,
                candidateLevel + 1,
                group.size());
    }

    EffectiveMergeLevels resolveEffectiveMergeLevels(List<CompressTarget> memoryTargets) {
        Map<String, Integer> effectiveLevels = new LinkedHashMap<>();
        for (CompressTarget target : memoryTargets) {
            effectiveLevels.put(target.blockId(), Math.max(target.currentLevel(), 1));
        }
        while (true) {
            Map<Integer, Integer> levelCounts = new LinkedHashMap<>();
            for (Integer level : effectiveLevels.values()) {
                levelCounts.put(level, levelCounts.getOrDefault(level, 0) + 1);
            }
            List<Integer> orderedLevels = new ArrayList<>(levelCounts.keySet());
            orderedLevels.sort(Integer::compareTo);
            if (orderedLevels.isEmpty()) {
                return new EffectiveMergeLevels(effectiveLevels, null);
            }
            int highestLevel = orderedLevels.get(orderedLevels.size() - 1);
            boolean isChanged = false;
            for (Integer level : orderedLevels) {
                if (level == highestLevel || levelCounts.get(level) != 1) {
                    continue;
                }
                Integer nextHigherLevel = orderedLevels.stream()
                        .filter(candidate -> candidate > level)
                        .findFirst()
                        .orElse(null);
                String blockId = effectiveLevels.entrySet().stream()
                        .filter(entry -> entry.getValue().equals(level))
                        .map(Map.Entry::getKey)
                        .findFirst()
                        .orElse(null);
                if (nextHigherLevel != null && blockId != null) {
                    effectiveLevels.put(blockId, nextHigherLevel);
                    isChanged = true;
                    break;
                }
            }
            if (isChanged) {
                continue;
            }
            Integer candidateLevel = orderedLevels.stream()
                    .filter(level -> levelCounts.get(level) >= 2)
                    .findFirst()
                    .orElse(null);
            return new EffectiveMergeLevels(effectiveLevels, candidateLevel);
        }
    }

    List<BaseMessage> applyLlmPhase(
            List<BaseMessage> messages,
            ModelContext context,
            List<BaseMessage> systemMessages,
            List<ToolInfo> tools,
            List<CompressTarget> targets,
            int targetTokens,
            boolean isAggressive,
            String phaseName,
            int keepRecentMessages) {
        List<BaseMessage> modelMessages = prepareRoundCompressionMessages(
                messages,
                targets,
                context,
                phaseName,
                targetTokens,
                isAggressive,
                keepRecentMessages,
                systemMessages,
                tools);
        if (modelMessages == null) {
            Loggers.CONTEXT_ENGINE.warning("[RoundLevelCompressor] phase=" + phaseName
                    + " skipped because compression call budget is impossible");
            return nullValue();
        }
        AssistantMessage response;
        try {
            response = invokeCompressionModel(modelMessages);
        } catch (RuntimeException exception) {
            throw ErrorHelper.buildError(
                    StatusCode.MODEL_CALL_FAILED,
                    "error_msg",
                    processorType() + " failed to invoke compression model during phase=" + phaseName);
        }

        List<Replacement> replacements = buildJsonReplacements(
                context,
                targets,
                response != null ? response.getParserContent() : null);
        if (replacements.isEmpty()
                && response != null
                && response.getContentAsString() != null
                && !response.getContentAsString().strip().isEmpty()) {
            Replacement fallback = buildRawFallbackReplacement(context, targets, response.getContentAsString().strip());
            if (fallback != null) {
                replacements = List.of(fallback);
            }
        }
        if (replacements.isEmpty()) {
            Loggers.CONTEXT_ENGINE.warning("[RoundLevelCompressor] phase=" + phaseName
                    + " produced no valid replacements");
            return nullValue();
        }
        List<BaseMessage> updatedMessages = applyReplacements(messages, replacements);
        Loggers.CONTEXT_ENGINE.info("[RoundLevelCompressor] phase=" + phaseName + " context_window_tokens "
                + countContextWindowTokens(systemMessages, messages, tools, context)
                + " -> " + countContextWindowTokens(systemMessages, updatedMessages, tools, context));
        return updatedMessages;
    }

    private AssistantMessage invokeCompressionModel(List<BaseMessage> modelMessages) {
        try {
            return getModel().invoke(
                    modelMessages,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    new JsonOutputParser(),
                    null,
                    null);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    List<BaseMessage> prepareRoundCompressionMessages(
            List<BaseMessage> contextMessages,
            List<CompressTarget> targets,
            ModelContext context,
            String phaseName,
            int targetTokens,
            boolean isAggressive,
            int keepRecentMessages,
            List<BaseMessage> systemMessages,
            List<ToolInfo> tools) {
        String systemPrompt = isAggressive
                ? DEFAULT_AGGRESSIVE_ROUND_COMPRESSION_PROMPT
                : DEFAULT_ROUND_COMPRESSION_PROMPT;
        String promptText = buildCompressionUserPrompt(
                contextMessages,
                targets,
                context,
                phaseName,
                targetTokens,
                keepRecentMessages,
                systemMessages,
                tools);
        if (isUnderCompressionCallBudget(systemPrompt, promptText, context)) {
            return List.of(new SystemMessage(systemPrompt), new UserMessage(promptText));
        }
        String compactPrompt = truncatePromptToBudget(systemPrompt, promptText, context);
        if (compactPrompt == null) {
            return nullValue();
        }
        return List.of(new SystemMessage(systemPrompt), new UserMessage(compactPrompt));
    }

    String buildCompressionUserPrompt(
            List<BaseMessage> contextMessages,
            List<CompressTarget> targets,
            ModelContext context,
            String phaseName,
            int targetTokens,
            int keepRecentMessages,
            List<BaseMessage> systemMessages,
            List<ToolInfo> tools) {
        java.util.Set<Integer> targetIndices = new java.util.LinkedHashSet<>();
        for (CompressTarget target : targets) {
            for (int index = target.startIdx(); index <= target.endIdx(); index++) {
                targetIndices.add(index);
            }
        }
        int firstTargetIdx = targets.stream().mapToInt(CompressTarget::startIdx).min().orElse(0);
        int lastTargetIdx = targets.stream().mapToInt(CompressTarget::endIdx).max().orElse(0);
        int protectedRecentStart = Math.max(contextMessages.size() - keepRecentMessages, lastTargetIdx + 1);

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
            targetLines.add("- replace_range: [" + target.startIdx() + ", " + target.endIdx() + "]");
            targetLines.add("- current_level: l" + target.currentLevel());
            targetLines.add("- next_level: l" + target.nextLevel());
            targetLines.add("- source_block_count: " + target.sourceBlockCount());
            for (int offset = 0; offset < target.messages().size(); offset++) {
                targetLines.add(serializeMessage(target.startIdx() + offset, target.messages().get(offset)));
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
                "- keep_recent_messages: " + keepRecentMessages,
                "- selected_blocks: " + targets.size(),
                "- current_context_window_tokens: " + currentWindowTokens,
                "- compression_call_budget_limit: " + compressionCallMaxTokens,
                "- selected_range: [" + firstTargetIdx + ", " + lastTargetIdx + "]",
                "",
                "[Reference Context]",
                referenceLines.isEmpty() ? "(none)" : String.join("\n", referenceLines),
                "",
                "[Selected Targets]",
                targetLines.isEmpty() ? "(none)" : String.join("\n", targetLines).stripTrailing(),
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
                "- Preserve the user's original requirements, constraints, acceptance criteria, and preferences "
                        + "as completely as possible.",
                "- Do not weaken or over-compress the user's original request unless absolutely necessary.",
                "- If a selected block is ongoing_react, include a distinct `User Requirements` section tied "
                        + "to the unfinished work.",
                "- If a selected block is completed_react, explicitly preserve both `User Requirements` and "
                        + "`Final Result` when they exist."));
    }

    String truncatePromptToBudget(String systemPrompt, String promptText, ModelContext context) {
        String minimumPrompt = "[Compression Task]\n...[TRUNCATED]...\n[Output Contract]\nReturn valid JSON only.";
        if (!isUnderCompressionCallBudget(systemPrompt, minimumPrompt, context)) {
            return nullValue();
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

    List<Replacement> buildJsonReplacements(
            ModelContext context,
            List<CompressTarget> targets,
            Object parserContent) {
        if (!isValidBlocksPayload(parserContent)) {
            return List.of();
        }
        Map<String, String> blockMap = new LinkedHashMap<>();
        @SuppressWarnings("unchecked")
        List<Object> blocks = (List<Object>) ((Map<?, ?>) parserContent).get("blocks");
        for (Object item : blocks) {
            if (!(item instanceof Map<?, ?> itemMap)) {
                continue;
            }
            Object blockIdObj = itemMap.get("block_id");
            Object summaryObj = itemMap.get("summary");
            if (!(blockIdObj instanceof String blockId) || blockId.isBlank()) {
                continue;
            }
            if (!(summaryObj instanceof String summary)) {
                continue;
            }
            summary = summary.strip();
            if (summary.isEmpty()) {
                continue;
            }
            blockMap.put(blockId, summary);
        }

        List<Replacement> replacements = new ArrayList<>();
        for (CompressTarget target : targets) {
            String summary = blockMap.get(target.blockId());
            if (summary == null || summary.isBlank()) {
                continue;
            }
            BaseMessage replacementMessage = buildMemoryMessage(summary, target, context);
            if (replacementMessage == null) {
                continue;
            }
            if (!hasCompressionBenefit(context, target.messages(), List.of(replacementMessage))) {
                continue;
            }
            replacements.add(new Replacement(target.startIdx(), target.endIdx(), List.of(replacementMessage)));
        }
        return replacements;
    }

    Replacement buildRawFallbackReplacement(
            ModelContext context,
            List<CompressTarget> targets,
            String summary) {
        if (targets.isEmpty() || summary == null || summary.isBlank()) {
            return nullValue();
        }
        int startIdx = targets.stream().mapToInt(CompressTarget::startIdx).min().orElse(0);
        int endIdx = targets.stream().mapToInt(CompressTarget::endIdx).max().orElse(-1);
        List<BaseMessage> mergedMessages = new ArrayList<>();
        for (CompressTarget target : targets) {
            mergedMessages.addAll(target.messages());
        }
        CompressTarget mergedTarget = new CompressTarget(
                "raw_fallback",
                "mixed_context",
                startIdx,
                endIdx,
                mergedMessages,
                targets.stream().mapToInt(CompressTarget::currentLevel).max().orElse(0),
                targets.stream().mapToInt(CompressTarget::nextLevel).max().orElse(1),
                targets.stream().mapToInt(CompressTarget::sourceBlockCount).sum());
        BaseMessage replacement = buildMemoryMessage(summary, mergedTarget, context);
        if (replacement == null || !hasCompressionBenefit(context, mergedMessages, List.of(replacement))) {
            return nullValue();
        }
        return new Replacement(startIdx, endIdx, List.of(replacement));
    }

    BaseMessage buildMemoryMessage(String summary, CompressTarget target, ModelContext context) {
        UserMessage message = new UserMessage(wrapMemoryBlock(summary, target.scope()));
        if (message.getMetadata() == null) {
            message.setMetadata(new LinkedHashMap<>());
        }
        message.getMetadata().put(COMPRESS_LEVEL, target.nextLevel());
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
                + summary;
    }

    UserMessage buildMinimalTruncatedMessage() {
        return new UserMessage(compressionMarker + "\n"
                + "processor: RoundLevelCompressor\n"
                + "type: historical_memory_block\n"
                + "scope: truncated_full_context\n"
                + "Summary:\n"
                + truncatedMarker);
    }

    UserMessage buildCompactTruncatedMessage() {
        return new UserMessage(compressionMarker + "\n" + truncatedMarker);
    }

    List<BaseMessage> truncateToTarget(
            List<BaseMessage> contextMessages,
            ModelContext context,
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
        if (!tail.isEmpty()) {
            return tail;
        }
        return truncatedMarker;
    }

    int countContextWindowTokens(
            List<BaseMessage> systemMessages,
            List<BaseMessage> contextMessages,
            List<ToolInfo> tools,
            ModelContext context) {
        TokenCounter tokenCounter = context.tokenCounter();
        List<BaseMessage> allMessages = new ArrayList<>();
        if (systemMessages != null) {
            allMessages.addAll(systemMessages);
        }
        if (contextMessages != null) {
            allMessages.addAll(contextMessages);
        }
        if (tokenCounter != null) {
            try {
                return tokenCounter.countMessages(allMessages)
                        + tokenCounter.countTools(tools != null ? tools : List.of());
            } catch (RuntimeException exception) {
                Loggers.CONTEXT_ENGINE.warning("[" + processorType()
                        + "] token_counter failed, fallback to estimate: " + exception.getMessage());
            }
        }
        int total = allMessages.stream().mapToInt(ContextUtils::estimateMessageTokens).sum();
        if (tools != null) {
            total += tools.stream().mapToInt(tool -> ContextUtils.estimateTokens(serializeTool(tool))).sum();
        }
        return total;
    }

    int countContextWindowFixedTokens(List<BaseMessage> systemMessages, List<ToolInfo> tools, ModelContext context) {
        return countContextWindowTokens(systemMessages, List.of(), tools, context);
    }

    int countCompressionCallTokens(String systemPrompt, String promptText, ModelContext context) {
        TokenCounter tokenCounter = context.tokenCounter();
        List<BaseMessage> messages = List.of(new SystemMessage(systemPrompt), new UserMessage(promptText));
        if (tokenCounter != null) {
            try {
                return tokenCounter.countMessages(messages);
            } catch (RuntimeException exception) {
                Loggers.CONTEXT_ENGINE.warning("[" + processorType()
                        + "] compression token counting fallback: " + exception.getMessage());
            }
        }
        return messages.stream().mapToInt(ContextUtils::estimateMessageTokens).sum();
    }

    boolean isUnderContextWindowBudget(
            List<BaseMessage> systemMessages,
            List<BaseMessage> contextMessages,
            List<ToolInfo> tools,
            ModelContext context) {
        return countContextWindowTokens(systemMessages, contextMessages, tools, context) <= targetTotalTokens;
    }

    boolean isUnderCompressionCallBudget(String systemPrompt, String promptText, ModelContext context) {
        return countCompressionCallTokens(systemPrompt, promptText, context) <= compressionCallMaxTokens;
    }

    boolean hasCompressionBenefit(
            ModelContext context,
            List<BaseMessage> originalMessages,
            List<BaseMessage> replacementMessages) {
        int originalTokens = countMessageTokens(originalMessages, context);
        int replacementTokens = countMessageTokens(replacementMessages, context);
        return originalTokens > replacementTokens;
    }

    int countMessageTokens(List<BaseMessage> messages, ModelContext context) {
        TokenCounter tokenCounter = context.tokenCounter();
        if (tokenCounter != null) {
            try {
                return tokenCounter.countMessages(messages);
            } catch (RuntimeException exception) {
                Loggers.CONTEXT_ENGINE.warning("[" + processorType()
                        + "] token_counter failed, fallback to estimate: " + exception.getMessage());
            }
        }
        return messages.stream().mapToInt(ContextUtils::estimateMessageTokens).sum();
    }

    String serializeMessage(int index, BaseMessage message) {
        List<String> parts = new ArrayList<>();
        parts.add("[" + index + "] role=" + message.getRole());
        if (message instanceof AssistantMessage assistant && assistant.getToolCalls() != null
                && !assistant.getToolCalls().isEmpty()) {
            parts.add("tool_calls=" + String.join(
                    ", ",
                    assistant.getToolCalls().stream().map(ToolCall::getName).toList()));
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

    static String serializeTool(ToolInfo tool) {
        try {
            return MAPPER.writeValueAsString(tool);
        } catch (JsonProcessingException exception) {
            return String.valueOf(tool);
        }
    }

    static String toText(Object content) {
        return content instanceof String text ? text : String.valueOf(content);
    }

    boolean isRoundLevelFallbackBlock(BaseMessage message) {
        return message instanceof UserMessage && toText(message.getContent()).startsWith(compressionMarker);
    }

    int findRoundLevelBlockEnd(List<BaseMessage> messages, int start, int compressEnd) {
        int endIdx = start;
        while (endIdx + 1 <= compressEnd
                && messages.get(endIdx + 1) instanceof AssistantMessage assistant
                && (assistant.getToolCalls() == null || assistant.getToolCalls().isEmpty())
                && looksLikeAck(assistant)) {
            endIdx++;
        }
        return endIdx;
    }

    static boolean looksLikeAck(BaseMessage message) {
        return message instanceof AssistantMessage
                && "Understood. I have recorded this compressed context."
                        .equals(toText(message.getContent()).strip());
    }

    static boolean isValidBlocksPayload(Object parserContent) {
        return parserContent instanceof Map<?, ?> map && map.get("blocks") instanceof List<?>;
    }

    static List<BaseMessage> applyReplacements(List<BaseMessage> messages, List<Replacement> replacements) {
        List<BaseMessage> updated = new ArrayList<>(messages);
        List<Replacement> ordered = new ArrayList<>(replacements);
        ordered.sort(Comparator.comparingInt(Replacement::startIdx).reversed());
        for (Replacement replacement : ordered) {
            updated = ContextUtils.replaceMessages(
                    updated,
                    replacement.replacementMessages(),
                    replacement.startIdx(),
                    replacement.endIdx());
        }
        return updated;
    }

    int getCompressLevel(BaseMessage message) {
        if (message.getMetadata() != null && message.getMetadata().get(COMPRESS_LEVEL) != null) {
            Object level = message.getMetadata().get(COMPRESS_LEVEL);
            if (level instanceof Number number) {
                return number.intValue();
            }
            try {
                return Integer.parseInt(String.valueOf(level));
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        if (isRoundLevelFallbackBlock(message)) {
            return 1;
        }
        return 0;
    }

    Model getModel() {
        if (model == null) {
            RoundLevelCompressorConfig config = getConfig();
            model = new Model(config.getModelClient(), config.getModel());
        }
        return model;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public void loadState(Map<String, Object> state) {
        // stateless
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> saveState() {
        return Map.of();
    }

    static List<Integer> range(int start, int end) {
        List<Integer> values = new ArrayList<>();
        for (int index = start; index <= end; index++) {
            values.add(index);
        }
        return values;
    }

    record CompressTarget(
            String blockId,
            String scope,
            int startIdx,
            int endIdx,
            List<BaseMessage> messages,
            int currentLevel,
            int nextLevel,
            int sourceBlockCount) {
    }

    record L0BlockEnd(int endIdx, String scope) {
    }

    record EffectiveMergeLevels(Map<String, Integer> effectiveLevels, Integer candidateLevel) {
    }

    record Replacement(int startIdx, int endIdx, List<BaseMessage> replacementMessages) {
    }
    private static <T> T nullValue() {
        return null;
    }

}
