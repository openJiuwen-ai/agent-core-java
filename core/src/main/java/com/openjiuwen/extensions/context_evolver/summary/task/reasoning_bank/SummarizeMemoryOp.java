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
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Summarize a single trajectory into ReasoningBank memories.
 * <p>
 * Mirrors Python's {@code SummarizeMemoryOp} in
 * {@code openjiuwen/extensions/context_evolver/summary/task/reasoning_bank/update.py}.
 * </p>
 */
public class SummarizeMemoryOp extends BaseOp {

    private static final LoggerProtocol LOGGER = Loggers.CONTEXT_ENGINE;

    @Override
    public CompletableFuture<Void> asyncExecute(RuntimeContext context) {
        String matts = getMatts(context);
        if (!"none".equals(matts) && !"sequential".equals(matts)) {
            return CompletableFuture.completedFuture(null);
        }
        String query = getQuery(context);
        if (query == null) {
            return CompletableFuture.completedFuture(null);
        }
        List<Object> trajectories = getTrajectories(context);
        if (trajectories == null) {
            return CompletableFuture.completedFuture(null);
        }

        Object llmObject = getLlm();
        if (!(llmObject instanceof ReasoningBankAsyncLlm llm)) {
            return CompletableFuture.failedFuture(new IllegalStateException("LLM not configured in ServiceContext"));
        }

        return determineLabel(context, llm, query, trajectories.get(0))
                .thenCompose(label -> {
                    String systemPrompt = label
                            ? ReasoningBankPrompts.EXTRACT_SUCCESS_TRAJ_SYSTEM_PROMPT
                            : ReasoningBankPrompts.EXTRACT_FAIL_TRAJ_SYSTEM_PROMPT;
                    String userPrompt = ReasoningBankPrompts.EXTRACT_TRAJ_USER_PROMPT
                            .replace("{query}", query)
                            .replace("{trajectory}", String.valueOf(trajectories.get(0)));
                    LOGGER.info("Extracting memories for %s trajectory...", label ? "successful" : "failed");
                    return llm.asyncGenerate(userPrompt, systemPrompt)
                            .thenAccept(response -> {
                                List<ReasoningBankMemory> memories = MemoryItemParser.parse(response, query, label);
                                context.set("memories", memories);
                                int size = memories.isEmpty() ? 0 : memories.get(0).getMemory().size();
                                LOGGER.info("Extracted %s memories from trajectory.", size);
                            });
                });
    }

    String getMatts(RuntimeContext context) {
        String matts = ReasoningBankUpdateUtils.stringValue(context.get("matts", ""));
        if (matts.isEmpty()) {
            LOGGER.warning("No matts found. Use memory summarization without scaling.");
            return "none";
        }
        return matts;
    }

    String getQuery(RuntimeContext context) {
        String query = ReasoningBankUpdateUtils.stringValue(context.get("query", ""));
        if (query.isEmpty()) {
            LOGGER.warning("No query found. Skipping trajectory summarization.");
            return null;
        }
        return query;
    }

    @SuppressWarnings("unchecked")
    List<Object> getTrajectories(RuntimeContext context) {
        Object value = context.get("trajectories", List.of());
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            LOGGER.warning("No trajectories found. Skipping trajectory summarization.");
            return null;
        }
        return (List<Object>) list;
    }

    private CompletableFuture<Boolean> determineLabel(RuntimeContext context,
                                                      ReasoningBankAsyncLlm llm,
                                                      String query,
                                                      Object trajectory) {
        Object labelObject = context.get("label", List.of());
        if (labelObject instanceof List<?> labels && !labels.isEmpty()) {
            return CompletableFuture.completedFuture(Boolean.TRUE.equals(labels.get(0)));
        }
        return LabelDeterminator.determineLabel(llm, query, trajectory)
                .thenApply(isSuccess -> {
                    context.set("label", List.of(isSuccess));
                    return isSuccess;
                });
    }
}
