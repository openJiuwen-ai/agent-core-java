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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Extract ReMe memories by comparing all trajectories.
 * <p>
 * Mirrors Python's {@code ComparativeAllExtractionOp} in
 * {@code openjiuwen/extensions/context_evolver/summary/task/reme/update.py}.
 * </p>
 */
public class ComparativeAllExtractionOp extends BaseOp {

    private static final LoggerProtocol LOGGER = Loggers.CONTEXT_ENGINE;

    private final boolean useExtraction;

    public ComparativeAllExtractionOp() {
        this(true);
    }

    public ComparativeAllExtractionOp(boolean useExtraction) {
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
        if (allTrajectories.size() < 2) {
            LOGGER.info("Not enough trajectories for comparative extraction (need at least 2)");
            context.set("comparative_memories", List.of());
            return CompletableFuture.completedFuture(null);
        }
        Object llmObject = getLlm();
        if (!(llmObject instanceof ReMeSummaryAsyncLlm llm)) {
            return CompletableFuture.failedFuture(new IllegalStateException("LLM not configured in ServiceContext"));
        }
        String trajectoriesText = IntStream.range(0, allTrajectories.size())
                .mapToObj(index -> "# Trajectory " + (index + 1) + "\n" + allTrajectories.get(index))
                .collect(Collectors.joining("\n\n"));
        String prompt = ReMePrompts.COMPARATIVE_ALL_MEMORY_PROMPT
                .replace("{trajectory}", trajectoriesText);
        List<ReMeMemory> memories = new ArrayList<>();
        return llm.asyncGenerate(prompt)
                .thenAccept(response -> SuccessExtractionOp.appendParsedMemories(response, userId, memories))
                .thenRun(() -> {
                    context.set("comparative_memories", memories);
                    LOGGER.info("Extracted %s insights from comparative analysis", memories.size());
                });
    }
}
