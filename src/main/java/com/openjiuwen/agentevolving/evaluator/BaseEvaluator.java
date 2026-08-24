/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.evaluator;

import com.openjiuwen.agentevolving.TuneConstant;
import com.openjiuwen.agentevolving.TuneUtils;
import com.openjiuwen.agentevolving.dataset.Case;
import com.openjiuwen.agentevolving.dataset.CaseLoader;
import com.openjiuwen.agentevolving.dataset.EvaluatedCase;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Abstract evaluator for converting a case and prediction into an evaluated case.
 *
 * <p>Mirrors Python's {@code BaseEvaluator} in
 * {@code openjiuwen/agent_evolving/evaluator/evaluator.py}.</p>
 */
public abstract class BaseEvaluator {

    public abstract EvaluatedCase evaluate(Case caseValue, Map<String, Object> predict);

    public List<EvaluatedCase> batchEvaluate(List<Case> cases, List<Map<String, Object>> predicts) {
        return batchEvaluate(cases, predicts, 1);
    }

    public List<EvaluatedCase> batchEvaluate(CaseLoader cases, List<Map<String, Object>> predicts) {
        return batchEvaluate(cases, predicts, 1);
    }

    public List<EvaluatedCase> batchEvaluate(CaseLoader cases, List<Map<String, Object>> predicts, int numParallel) {
        return batchEvaluate(cases.getCases(), predicts, numParallel);
    }

    public List<EvaluatedCase> batchEvaluate(List<Case> cases, List<Map<String, Object>> predicts, int numParallel) {
        if (cases.size() != predicts.size()) {
            throw ErrorHelper.buildError(
                    StatusCode.TOOLCHAIN_EVALUATOR_EXECUTION_ERROR,
                    "error_msg",
                    "length of cases: " + cases.size()
                            + " dose not equal with length of predicts: " + predicts.size() + " "
            );
        }

        TuneUtils.validateDigitalParameter(
                numParallel,
                "num_parallel",
                TuneConstant.minParallelNum,
                TuneConstant.maxParallelNum
        );
        int numWorkers = Math.min(numParallel, cases.size());
        ExecutorService executor = Executors.newFixedThreadPool(numWorkers);
        try {
            List<Future<EvaluatedCase>> futures = new ArrayList<>(cases.size());
            for (int index = 0; index < cases.size(); index++) {
                Case caseValue = cases.get(index);
                Map<String, Object> predict = predicts.get(index);
                futures.add(executor.submit(() -> evaluate(caseValue, predict)));
            }
            List<EvaluatedCase> evaluatedCases = new ArrayList<>(futures.size());
            for (Future<EvaluatedCase> future : futures) {
                evaluatedCases.add(getFutureResult(future));
            }
            return evaluatedCases;
        } finally {
            executor.shutdown();
        }
    }

    private static EvaluatedCase getFutureResult(Future<EvaluatedCase> future) {
        try {
            return future.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("batch evaluation interrupted", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new RuntimeException(cause);
        }
    }
}
