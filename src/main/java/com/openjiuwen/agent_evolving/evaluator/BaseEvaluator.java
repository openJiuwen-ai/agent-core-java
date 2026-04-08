/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.evaluator;

import com.openjiuwen.agent_evolving.TuneConstant;
import com.openjiuwen.agent_evolving.TuneUtils;
import com.openjiuwen.agent_evolving.dataset.Case;
import com.openjiuwen.agent_evolving.dataset.CaseLoader;
import com.openjiuwen.agent_evolving.dataset.EvaluatedCase;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Abstract evaluator for converting (case, prediction) to EvaluatedCase.
 *
 * <p>Implement evaluate() for single case, use batchEvaluate() for parallel execution.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.evaluator.evaluator.BaseEvaluator}.
 */
public abstract class BaseEvaluator {

    /**
     * Evaluate single case with model prediction.
     *
     * @param caseData Original Case with inputs and label
     * @param predict  Model prediction to evaluate
     * @return EvaluatedCase with score and reasoning
     */
    public abstract EvaluatedCase evaluate(Case caseData, Map<String, Object> predict);

    /**
     * Evaluate multiple cases in parallel.
     *
     * @param cases       List of Cases
     * @param predicts    List of model predictions
     * @param numParallel Number of parallel workers
     * @return List of EvaluatedCases
     */
    public List<EvaluatedCase> batchEvaluate(
            List<Case> cases,
            List<Map<String, Object>> predicts,
            int numParallel
    ) {
        List<Case> safeCases = cases != null ? cases : List.of();
        List<Map<String, Object>> safePredicts = predicts != null ? predicts : List.of();
        if (safeCases.size() != safePredicts.size()) {
            throw ErrorHelper.buildError(
                    StatusCode.TOOLCHAIN_EVALUATOR_EXECUTION_ERROR,
                    "error_msg",
                    "length of cases: " + safeCases.size()
                            + " does not equal with length of predicts: " + safePredicts.size()
            );
        }
        TuneUtils.validateDigitalParameter(
                numParallel,
                "num_parallel",
                TuneConstant.MIN_PARALLEL_NUM,
                TuneConstant.MAX_PARALLEL_NUM
        );
        int workers = Math.min(numParallel, safeCases.size());
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        try {
            List<Future<EvaluatedCase>> futures = new ArrayList<>(safeCases.size());
            for (int i = 0; i < safeCases.size(); i++) {
                final int index = i;
                futures.add(executor.submit(() -> evaluate(safeCases.get(index), safePredicts.get(index))));
            }
            List<EvaluatedCase> results = new ArrayList<>(safeCases.size());
            for (Future<EvaluatedCase> future : futures) {
                try {
                    results.add(future.get());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Evaluator batch execution interrupted", e);
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    if (cause instanceof RuntimeException runtimeException) {
                        throw runtimeException;
                    }
                    throw new RuntimeException("Evaluator batch execution failed", cause);
                }
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * Evaluate multiple cases with default single-threaded execution.
     *
     * @param cases    List of Cases
     * @param predicts List of model predictions
     * @return List of evaluated cases
     */
    public List<EvaluatedCase> batchEvaluate(List<Case> cases, List<Map<String, Object>> predicts) {
        return batchEvaluate(cases, predicts, TuneConstant.DEFAULT_PARALLEL_NUM);
    }

    /**
     * Evaluate multiple cases from a case loader.
     *
     * @param cases       CaseLoader instance
     * @param predicts    List of model predictions
     * @param numParallel Number of parallel workers
     * @return List of evaluated cases
     */
    public List<EvaluatedCase> batchEvaluate(CaseLoader cases, List<Map<String, Object>> predicts, int numParallel) {
        return batchEvaluate(cases != null ? cases.getCases() : List.of(), predicts, numParallel);
    }
}
