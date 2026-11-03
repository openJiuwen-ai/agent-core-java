/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.retrieve.task.reasoning_bank;

import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.extensions.context_evolver.core.context.RuntimeContext;
import com.openjiuwen.extensions.context_evolver.core.op.BaseOp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Sequential MaTTS scaling operation with iterative self-checking.
 *
 * <p>Mirrors Python's {@code SequentialScalingOp} in
 * {@code openjiuwen/extensions/context_evolver/retrieve/task/reasoning_bank/matts.py}.</p>
 */
public class SequentialScalingOp extends BaseOp {

    private static final LoggerProtocol LOGGER = Loggers.CONTEXT_ENGINE;

    private final int k;

    public SequentialScalingOp() {
        this(3);
    }

    public SequentialScalingOp(int k) {
        super(Map.of("k", k));
        this.k = k;
    }

    @Override
    public CompletableFuture<Void> asyncExecute(RuntimeContext context) {
        LOGGER.info("Executing sequential scaling with k=%s refinement rounds", k);
        Object query = MattsSupport.requireContext(context, "query");
        MattsSupport.requireContext(context, "user_id");
        MattsAsyncLlm llm;
        try {
            llm = MattsSupport.requireLlm(getLlm());
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }

        List<Map<String, Object>> refinementHistory = new ArrayList<>();
        AtomicReference<String> currentAnswer = new AtomicReference<>(
                MattsSupport.stringValue(context.get("answer", "")));
        context.get("trajectory", "");

        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (int i = 0; i < k; i++) {
            final int roundIndex = i;
            chain = chain.thenCompose(ignored -> {
                LOGGER.info("Refinement round %s/%s", roundIndex + 1, k);
                String prompt = buildRefinementPrompt(roundIndex, currentAnswer.get(), String.valueOf(query));
                return llm.asyncGenerate(prompt).thenAccept(response -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("round", roundIndex + 1);
                    item.put("prompt", prompt);
                    item.put("response", response);
                    refinementHistory.add(item);
                    currentAnswer.set(response);
                });
            });
        }

        return chain.thenRun(() -> {
            context.set("refinement_history", refinementHistory);
            context.set("refined_answer", currentAnswer.get());
            context.set("answer", currentAnswer.get());
            context.set("scaling_factor", k);
            LOGGER.info("Completed %s refinement rounds", k);
        });
    }

    private static String buildRefinementPrompt(int roundIndex, String currentAnswer, String query) {
        if (roundIndex == 0) {
            return """
                    Important: Let's carefully re-examine the previous trajectory,
                    including your reasoning steps and actions taken.

                    Pay special attention to whether you used the correct approach, and whether your response addresses
                    the user query. If you find inconsistencies, correct them. If everything seems correct, confirm your final answer.

                    Previous answer: %s

                    Query: %s""".formatted(currentAnswer, query);
        }
        return """
                Let's check again.

                Previous answer: %s

                Query: %s""".formatted(currentAnswer, query);
    }
}
