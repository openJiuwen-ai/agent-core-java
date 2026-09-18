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

/**
 * Extract ReMe memories from failed trajectories.
 * <p>
 * Mirrors Python's {@code FailureExtractionOp} in
 * {@code openjiuwen/extensions/context_evolver/summary/task/reme/update.py}.
 * </p>
 */
public class FailureExtractionOp extends BaseOp {

    private static final LoggerProtocol LOGGER = Loggers.CONTEXT_ENGINE;

    private final boolean useExtraction;

    public FailureExtractionOp() {
        this(true);
    }

    public FailureExtractionOp(boolean useExtraction) {
        super(Map.of("use_extraction", useExtraction));
        this.useExtraction = useExtraction;
    }

    @Override
    public CompletableFuture<Void> asyncExecute(RuntimeContext context) {
        if (!useExtraction) {
            context.set("failure_memories", List.of());
            return CompletableFuture.completedFuture(null);
        }
        List<Object> failureTrajectories = TrajectoryPreprocessOp.objectList(context.get("failure_trajectories", List.of()));
        if (failureTrajectories.isEmpty()) {
            LOGGER.info("No failure trajectories to extract from");
            context.set("failure_memories", List.of());
            return CompletableFuture.completedFuture(null);
        }
        Object llmObject = getLlm();
        if (!(llmObject instanceof ReMeSummaryAsyncLlm llm)) {
            return CompletableFuture.failedFuture(new IllegalStateException("LLM not configured in ServiceContext"));
        }
        String userId = String.valueOf(context.get("user_id", "default"));
        String query = String.valueOf(context.get("query", ""));
        List<ReMeMemory> memories = new ArrayList<>();
        CompletableFuture<Void> future = CompletableFuture.completedFuture(null);
        for (Object trajectory : failureTrajectories) {
            String prompt = ReMePrompts.FAILURE_MEMORY_PROMPT
                    .replace("{query}", query)
                    .replace("{step_sequence}", String.valueOf(trajectory))
                    .replace("{outcome}", "failed");
            future = future.thenCompose(ignored -> llm.asyncGenerate(prompt)
                    .thenAccept(response -> SuccessExtractionOp.appendParsedMemories(response, userId, memories)));
        }
        return future.thenRun(() -> {
            context.set("failure_memories", memories);
            LOGGER.info("Extracted %s insights from failure trajectories", memories.size());
        });
    }
}
