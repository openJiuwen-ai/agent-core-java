/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.service;

import com.openjiuwen.extensions.context_evolver.core.context.RuntimeContext;
import com.openjiuwen.extensions.context_evolver.core.op.BaseOp;
import com.openjiuwen.extensions.context_evolver.core.schema.VectorNode;
import com.openjiuwen.extensions.context_evolver.core.vector_store.MemoryVectorStore;
import com.openjiuwen.extensions.context_evolver.schema.ReMeMemory;
import com.openjiuwen.extensions.context_evolver.schema.ReMeMemoryMetadata;
import com.openjiuwen.extensions.context_evolver.summary.task.reme.ReMeUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

class ReMeSummarizeMemoryOp extends BaseOp {

    private final boolean extractBestTraj;
    private final boolean extractWorstTraj;
    private final boolean extractComparativeTraj;
    private final boolean memoryValidation;
    private final boolean memoryDeduplication;

    ReMeSummarizeMemoryOp(
            boolean extractBestTraj,
            boolean extractWorstTraj,
            boolean extractComparativeTraj,
            boolean memoryValidation,
            boolean memoryDeduplication
    ) {
        this.extractBestTraj = extractBestTraj;
        this.extractWorstTraj = extractWorstTraj;
        this.extractComparativeTraj = extractComparativeTraj;
        this.memoryValidation = memoryValidation;
        this.memoryDeduplication = memoryDeduplication;
    }

    @Override
    protected CompletableFuture<Void> asyncExecute(RuntimeContext context) {
        List<?> rawTrajectories = context.getList("trajectories");
        if (rawTrajectories == null || rawTrajectories.isEmpty()) {
            context.set("memories", List.of());
            return CompletableFuture.completedFuture(null);
        }

        List<String> trajectories = new ArrayList<>();
        for (Object rawTrajectory : rawTrajectories) {
            String normalized = SummaryFlowSupport.normalizeTrajectory(rawTrajectory);
            if (!normalized.isBlank()) {
                trajectories.add(normalized);
            }
        }
        if (trajectories.isEmpty()) {
            context.set("memories", List.of());
            return CompletableFuture.completedFuture(null);
        }

        String userId = context.getString("user_id", "default");
        String query = context.getString("query", "");
        List<?> rawScores = context.getList("score");
        double successThreshold = threshold(context);
        List<Double> scores = new ArrayList<>();
        List<String> successTrajectories = new ArrayList<>();
        List<String> failureTrajectories = new ArrayList<>();

        for (int index = 0; index < trajectories.size(); index++) {
            String trajectory = trajectories.get(index);
            double score = SummaryFlowSupport.scoreAt(rawScores, index, inferDefaultScore(trajectory));
            scores.add(score);
            if (score >= successThreshold) {
                successTrajectories.add(trajectory);
            } else {
                failureTrajectories.add(trajectory);
            }
        }

        context.set("success_trajectories", successTrajectories);
        context.set("failure_trajectories", failureTrajectories);
        context.set("all_trajectories", trajectories);
        context.set("score", scores);

        List<ReMeMemory> successMemories = new ArrayList<>();
        List<ReMeMemory> failureMemories = new ArrayList<>();
        List<ReMeMemory> comparativeMemories = new ArrayList<>();

        if (extractBestTraj) {
            for (int index = 0; index < trajectories.size(); index++) {
                if (scores.get(index) >= successThreshold) {
                    successMemories.add(buildSuccessMemory(userId, query, trajectories.get(index), scores.get(index)));
                }
            }
        }

        if (extractWorstTraj) {
            for (int index = 0; index < trajectories.size(); index++) {
                if (scores.get(index) < successThreshold) {
                    failureMemories.add(buildFailureMemory(userId, query, trajectories.get(index), scores.get(index)));
                }
            }
        }

        if (extractComparativeTraj) {
            ReMeMemory comparative = buildComparativeMemory(userId, query, trajectories, scores);
            if (comparative != null) {
                comparativeMemories.add(comparative);
            }
        }

        List<ReMeMemory> memories = new ArrayList<>();
        memories.addAll(successMemories);
        memories.addAll(failureMemories);
        memories.addAll(comparativeMemories);
        if (memories.isEmpty()) {
            memories.add(buildSuccessMemory(userId, query, trajectories.get(0), scores.get(0)));
        }

        if (memoryValidation) {
            memories = validateMemories(memories);
        }
        if (memoryDeduplication) {
            memories = deduplicateMemories(userId, memories);
        }

        context.set("success_memories", successMemories);
        context.set("failure_memories", failureMemories);
        context.set("comparative_memories", comparativeMemories);
        context.set("validated_memories", memories);
        context.set("deduplicated_memories", memories);
        context.set("memories", memories);
        return CompletableFuture.completedFuture(null);
    }

    private ReMeMemory buildSuccessMemory(String userId, String query, String trajectory, double score) {
        List<String> actionLines = SummaryFlowSupport.actionLines(trajectory);
        List<String> tools = SummaryFlowSupport.toolNames(trajectory);
        List<String> observationKeys = SummaryFlowSupport.observationKeys(trajectory);
        String primaryTool = SummaryFlowSupport.firstToolName(tools, actionLines);
        String queryHint = fallbackQueryHint(query, trajectory);

        String whenToUse = "When solving tasks like: " + queryHint;
        if (!primaryTool.isBlank()) {
            whenToUse += " using " + primaryTool;
        }

        StringBuilder content = new StringBuilder();
        if (!actionLines.isEmpty()) {
            content.append("Start with ")
                .append(SummaryFlowSupport.limit(actionLines.get(0), 160))
                .append(" to gather the authoritative evidence.");
        }
        if (!observationKeys.isEmpty()) {
            if (content.length() > 0) {
                content.append(' ');
            }
            content.append("Use returned fields such as ")
                .append(String.join(", ", observationKeys))
                .append(" to ground the answer.");
        }
        String assistantSummary = SummaryFlowSupport.assistantSummary(trajectory);
        if (!assistantSummary.isBlank()) {
            if (content.length() > 0) {
                content.append(' ');
            }
            content.append("Keep the successful final move concise: ").append(assistantSummary).append('.');
        }
        if (content.length() == 0) {
            content.append("Preserve the most reusable successful step from the trajectory and reuse it for similar tasks.");
        }

        List<String> tags = new ArrayList<>();
        tags.add("success_pattern");
        tags.addAll(tagify(tools));
        tags.addAll(tagify(observationKeys));
        return createMemory(
            userId,
            whenToUse,
            content.toString(),
            clampConfidence(0.55d + Math.max(score, 0.0d) * 0.25d),
            primaryTool.isBlank() ? "reasoning" : "action",
            tools,
            tags
        );
    }

    private ReMeMemory buildFailureMemory(String userId, String query, String trajectory, double score) {
        List<String> actionLines = SummaryFlowSupport.actionLines(trajectory);
        List<String> tools = SummaryFlowSupport.toolNames(trajectory);
        String primaryTool = SummaryFlowSupport.firstToolName(tools, actionLines);
        String queryHint = fallbackQueryHint(query, trajectory);

        String whenToUse = "When solving tasks like: " + queryHint + " after a low-scoring attempt";
        if (!primaryTool.isBlank()) {
            whenToUse += " with " + primaryTool;
        }

        StringBuilder content = new StringBuilder();
        if (!actionLines.isEmpty()) {
            content.append("Do not keep following ")
                .append(SummaryFlowSupport.limit(actionLines.get(0), 160))
                .append(" when it fails to surface usable evidence.");
        } else {
            content.append("Do not continue a low-scoring trajectory without a verification step.");
        }
        content.append(" Validate the returned data, adjust the tool or filter, and only then answer the task.");
        String assistantSummary = SummaryFlowSupport.assistantSummary(trajectory);
        if (!assistantSummary.isBlank()) {
            content.append(" The failed run ended with: ").append(assistantSummary).append('.');
        }

        List<String> tags = new ArrayList<>();
        tags.add("error_prevention");
        tags.add("failure_analysis");
        tags.addAll(tagify(tools));
        return createMemory(
            userId,
            whenToUse,
            content.toString(),
            clampConfidence(0.65d - Math.min(Math.max(score, 0.0d), 0.5d) * 0.1d),
            primaryTool.isBlank() ? "decision" : "action",
            tools,
            tags
        );
    }

    private ReMeMemory buildComparativeMemory(
            String userId,
            String query,
            List<String> trajectories,
            List<Double> scores
    ) {
        if (trajectories.size() < 2) {
            return null;
        }

        int bestIndex = 0;
        int worstIndex = 0;
        for (int index = 1; index < scores.size(); index++) {
            if (scores.get(index) > scores.get(bestIndex)) {
                bestIndex = index;
            }
            if (scores.get(index) < scores.get(worstIndex)) {
                worstIndex = index;
            }
        }

        if (Double.compare(scores.get(bestIndex), scores.get(worstIndex)) == 0) {
            return null;
        }

        String bestTrajectory = trajectories.get(bestIndex);
        String worstTrajectory = trajectories.get(worstIndex);
        String bestTool = SummaryFlowSupport.firstToolName(
            SummaryFlowSupport.toolNames(bestTrajectory),
            SummaryFlowSupport.actionLines(bestTrajectory)
        );
        String worstTool = SummaryFlowSupport.firstToolName(
            SummaryFlowSupport.toolNames(worstTrajectory),
            SummaryFlowSupport.actionLines(worstTrajectory)
        );
        List<String> bestKeys = SummaryFlowSupport.observationKeys(bestTrajectory);

        String whenToUse = "When comparing multiple trajectories for: " + fallbackQueryHint(query, bestTrajectory);
        StringBuilder content = new StringBuilder("Prefer the higher-scoring trajectory because it exposed directly usable evidence earlier.");
        if (!bestTool.isBlank() || !worstTool.isBlank()) {
            content.append(" The better run relied on ")
                .append(bestTool.isBlank() ? "a stronger action path" : bestTool)
                .append(" while the weaker run stalled around ")
                .append(worstTool.isBlank() ? "a weaker action path" : worstTool)
                .append('.');
        }
        if (!bestKeys.isEmpty()) {
            content.append(" The stronger run grounded the answer in fields such as ")
                .append(String.join(", ", bestKeys))
                .append('.');
        }

        List<String> tags = new ArrayList<>();
        tags.add("comparative_analysis");
        tags.add("success_factors");
        tags.addAll(tagify(List.of(bestTool, worstTool)));
        return createMemory(
            userId,
            whenToUse,
            content.toString(),
            0.8d,
            "decision",
            List.of(bestTool, worstTool),
            tags
        );
    }

    private List<ReMeMemory> validateMemories(List<ReMeMemory> memories) {
        List<ReMeMemory> validated = new ArrayList<>();
        for (ReMeMemory memory : memories) {
            String whenToUse = SummaryFlowSupport.compact(memory.getWhenToUse());
            String content = SummaryFlowSupport.compact(memory.getContent());
            Double confidence = memory.getMetadata().getConfidence();
            if (whenToUse.isBlank() || content.isBlank() || content.length() < 20) {
                continue;
            }
            if (confidence == null || confidence < 0.3d) {
                continue;
            }
            memory.setWhenToUse(SummaryFlowSupport.limit(whenToUse, 220));
            memory.setContent(SummaryFlowSupport.limit(content, 600));
            memory.setScore(Math.max(memory.getScore(), confidence));
            validated.add(memory);
        }
        return validated;
    }

    private List<ReMeMemory> deduplicateMemories(String userId, List<ReMeMemory> memories) {
        Set<String> existingKeys = new LinkedHashSet<>();
        List<List<Double>> existingEmbeddings = new ArrayList<>();
        Object vectorStore = getVectorStore();
        if (vectorStore instanceof MemoryVectorStore store) {
            Map<String, Object> filter = new LinkedHashMap<>();
            filter.put("workspace_id", userId);
            filter.put("type", "reme_memory");
            for (VectorNode node : store.getAll(filter)) {
                ReMeMemory existing = ReMeMemory.fromVectorNode(node);
                existingKeys.add(memoryKey(existing));
                if (node.getEmbedding() != null) {
                    existingEmbeddings.add(node.getEmbedding());
                }
            }
        }

        Set<String> seen = new LinkedHashSet<>();
        List<List<Double>> batchEmbeddings = new ArrayList<>();
        List<ReMeMemory> unique = new ArrayList<>();
        for (ReMeMemory memory : memories) {
            String key = memoryKey(memory);
            if (!seen.add(key) || existingKeys.contains(key)) {
                continue;
            }
            List<Double> embedding = TaskMemoryService.defaultEmbeddingFor(memory.getWhenToUse() + " " + memory.getContent());
            if (isSimilar(embedding, existingEmbeddings) || isSimilar(embedding, batchEmbeddings)) {
                continue;
            }
            unique.add(memory);
            batchEmbeddings.add(embedding);
        }
        return unique;
    }

    private boolean isSimilar(List<Double> embedding, List<List<Double>> candidates) {
        for (List<Double> candidate : candidates) {
            if (candidate == null || candidate.isEmpty()) {
                continue;
            }
            if (ReMeUtils.calculateCosineSimilarity(embedding, candidate) > 0.96d) {
                return true;
            }
        }
        return false;
    }

    private ReMeMemory createMemory(
            String userId,
            String whenToUse,
            String content,
            double confidence,
            String stepType,
            List<String> toolsUsed,
            List<String> tags
    ) {
        Instant now = Instant.now();
        ReMeMemoryMetadata metadata = new ReMeMemoryMetadata();
        metadata.setTags(uniqueStrings(tagify(tags), 6));
        metadata.setStepType(stepType);
        metadata.setToolsUsed(uniqueStrings(tagify(toolsUsed), 4));
        metadata.setConfidence(confidence);
        metadata.setFreq(0);
        metadata.setUtility(0.0d);

        ReMeMemory memory = new ReMeMemory();
        memory.setWorkspaceId(userId);
        memory.setWhenToUse(SummaryFlowSupport.limit(whenToUse, 220));
        memory.setContent(SummaryFlowSupport.limit(content, 600));
        memory.setScore(confidence);
        memory.setCreatedAt(now);
        memory.setUpdatedAt(now);
        memory.setMetadata(metadata);
        return memory;
    }

    private List<String> tagify(List<String> values) {
        List<String> normalized = new ArrayList<>();
        if (values == null) {
            return normalized;
        }
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            String token = SummaryFlowSupport.normalizeForMatch(value).replace(' ', '_');
            if (!token.isBlank()) {
                normalized.add(token);
            }
        }
        return normalized;
    }

    private List<String> uniqueStrings(List<String> values, int limit) {
        List<String> unique = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank() || !seen.add(value)) {
                continue;
            }
            unique.add(value);
            if (unique.size() >= limit) {
                break;
            }
        }
        return unique;
    }

    private double clampConfidence(double value) {
        return Math.max(0.35d, Math.min(0.95d, value));
    }

    private double inferDefaultScore(String trajectory) {
        Double feedbackScore = SummaryFlowSupport.feedbackScore(trajectory);
        if (feedbackScore != null) {
            return feedbackScore;
        }
        String normalized = " " + SummaryFlowSupport.normalizeForMatch(trajectory) + " ";
        if (normalized.contains(" failed ")
            || normalized.contains(" error ")
            || normalized.contains(" unable ")
            || normalized.contains(" cannot ")
            || normalized.contains(" could not ")) {
            return 0.0d;
        }
        return 1.0d;
    }

    private double threshold(RuntimeContext context) {
        Object rawThreshold = context.get("threshold");
        if (rawThreshold instanceof Number number) {
            return number.doubleValue();
        }
        return 1.0d;
    }

    private String fallbackQueryHint(String query, String trajectory) {
        String hint = SummaryFlowSupport.queryHint(query);
        if (!hint.isBlank()) {
            return hint;
        }
        String summary = SummaryFlowSupport.assistantSummary(trajectory);
        return summary.isBlank() ? "similar tasks" : summary;
    }

    private String memoryKey(ReMeMemory memory) {
        return SummaryFlowSupport.normalizeForMatch(memory.getWhenToUse())
            + "|"
            + SummaryFlowSupport.normalizeForMatch(memory.getContent());
    }
}
