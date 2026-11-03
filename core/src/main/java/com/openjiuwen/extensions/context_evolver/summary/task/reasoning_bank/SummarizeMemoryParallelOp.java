/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.summary.task.reasoning_bank;

import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.extensions.context_evolver.core.context.RuntimeContext;
import com.openjiuwen.extensions.context_evolver.core.op.BaseOp;
import com.openjiuwen.extensions.context_evolver.schema.ReasoningBankMemory;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Summarize multiple MaTTS trajectories into ReasoningBank memories.
 * <p>
 * Mirrors Python's {@code SummarizeMemoryParallelOp} in
 * {@code openjiuwen/extensions/context_evolver/summary/task/reasoning_bank/update.py}.
 * </p>
 */
public class SummarizeMemoryParallelOp extends BaseOp {

    private static final LoggerProtocol LOGGER = Loggers.CONTEXT_ENGINE;

    @Override
    public CompletableFuture<Void> asyncExecute(RuntimeContext context) {
        SummarizeMemoryOp singleOp = new SummarizeMemoryOp();
        String matts = singleOp.getMatts(context);
        if (!"parallel".equals(matts) && !"combined".equals(matts)) {
            return CompletableFuture.completedFuture(null);
        }
        String query = singleOp.getQuery(context);
        if (query == null) {
            return CompletableFuture.completedFuture(null);
        }
        List<Object> trajectories = singleOp.getTrajectories(context);
        if (trajectories == null) {
            return CompletableFuture.completedFuture(null);
        }
        if (trajectories.size() < 2) {
            LOGGER.warning("Not enough trajectories for parallel summarization.");
            return CompletableFuture.completedFuture(null);
        }

        Object llmObject = getLlm();
        if (!(llmObject instanceof ReasoningBankAsyncLlm llm)) {
            return CompletableFuture.failedFuture(new IllegalStateException("LLM not configured in ServiceContext"));
        }

        String userPrompt = ReasoningBankPrompts.PARALLEL_SCALING_USER_PROMPT
                .replace("{query}", query)
                .replace("{trajectories}", formatTrajectories(trajectories));
        LOGGER.info("Extracting parallel memories from %s trajectories...", trajectories.size());
        return llm.asyncGenerate(userPrompt, ReasoningBankPrompts.PARALLEL_SCALING_SYSTEM_PROMPT)
                .thenAccept(response -> {
                    List<ReasoningBankMemory> memories = MemoryItemParser.parse(response, query, null);
                    context.set("memories", memories);
                    int size = memories.isEmpty() ? 0 : memories.get(0).getMemory().size();
                    LOGGER.info("Extracted %s memories from trajectory.", size);
                });
    }

    private static String formatTrajectories(List<Object> trajectories) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < trajectories.size(); index++) {
            builder.append("<Trajectory ").append(index + 1).append(">\n")
                    .append(trajectories.get(index))
                    .append("\n\n");
        }
        return builder.toString();
    }
}
