/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.summary.task.reme;

import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.extensions.context_evolver.core.context.RuntimeContext;
import com.openjiuwen.extensions.context_evolver.core.op.BaseOp;
import com.openjiuwen.extensions.context_evolver.schema.ReMeMemory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Extract ReMe memories by comparing best and worst scored trajectories.
 * <p>
 * Mirrors Python's {@code ComparativeExtractionOp} in
 * {@code openjiuwen/extensions/context_evolver/summary/task/reme/update.py}.
 * </p>
 */
public class ComparativeExtractionOp extends BaseOp {

    private static final LoggerProtocol LOGGER = Loggers.CONTEXT_ENGINE;

    private final boolean useExtraction;

    public ComparativeExtractionOp() {
        this(true);
    }

    public ComparativeExtractionOp(boolean useExtraction) {
        super(Map.of("use_extraction", useExtraction));
        this.useExtraction = useExtraction;
    }

    @Override
    public CompletableFuture<Void> asyncExecute(RuntimeContext context) {
        if (!useExtraction) {
            context.set("comparative_memories", List.of());
            return CompletableFuture.completedFuture(null);
        }
        List<Object> allTrajectories = TrajectoryPreprocessOp.objectList(context.get("all_trajectories", List.of()));
        String userId = String.valueOf(context.get("user_id", "default"));
        List<Double> scores = TrajectoryPreprocessOp.numberList(context.get("score", List.of()));
        if (allTrajectories.size() < 2) {
            LOGGER.info("Not enough trajectories for comparative extraction (need at least 2)");
            context.set("comparative_memories", List.of());
            return CompletableFuture.completedFuture(null);
        }
        if (scores.isEmpty()) {
            LOGGER.info("No scores provided for comparative extraction, skipping");
            context.set("comparative_memories", List.of());
            return CompletableFuture.completedFuture(null);
        }
        double maxScore = scores.stream().max(Comparator.naturalOrder()).orElse(0.0d);
        double minScore = scores.stream().min(Comparator.naturalOrder()).orElse(0.0d);
        if (Double.compare(maxScore, minScore) == 0) {
            LOGGER.info("Best and worst trajectory score is the same, skipping comparative extraction");
            context.set("comparative_memories", List.of());
            return CompletableFuture.completedFuture(null);
        }
        Object llmObject = getLlm();
        if (!(llmObject instanceof ReMeSummaryAsyncLlm llm)) {
            return CompletableFuture.failedFuture(new IllegalStateException("LLM not configured in ServiceContext"));
        }
        int maxIndex = scores.indexOf(maxScore);
        int minIndex = scores.indexOf(minScore);
        String prompt = ReMePrompts.COMPARATIVE_MEMORY_PROMPT
                .replace("{higher_score}", String.valueOf(maxScore))
                .replace("{lower_score}", String.valueOf(minScore))
                .replace("{higher_steps}", String.valueOf(allTrajectories.get(maxIndex)))
                .replace("{lower_steps}", String.valueOf(allTrajectories.get(minIndex)));
        LOGGER.debug("Comparing trajectory with score %s and %s", maxScore, minScore);
        List<ReMeMemory> memories = new ArrayList<>();
        return llm.asyncGenerate(prompt)
                .thenAccept(response -> SuccessExtractionOp.appendParsedMemories(response, userId, memories))
                .thenRun(() -> {
                    context.set("comparative_memories", memories);
                    LOGGER.info("Extracted %s insights from comparative analysis", memories.size());
                });
    }
}
