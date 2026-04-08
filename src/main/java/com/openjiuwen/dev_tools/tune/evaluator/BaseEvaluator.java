// coding: utf-8
/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.tune.evaluator;

import com.openjiuwen.dev_tools.tune.Case;
import com.openjiuwen.dev_tools.tune.EvaluatedCase;

import java.util.List;
import java.util.Map;

/**
 * Base evaluator for tuning.
 *
 * <p>Mirrors Python's {@code BaseEvaluator} in {@code openjiuwen.dev_tools.tune.evaluator.evaluator}.
 */
public abstract class BaseEvaluator {

    /**
     * Evaluates a single case against the prediction.
     *
     * @param case the case to evaluate
     * @param predict the model prediction
     * @return the evaluated case with score and reason
     */
    public abstract EvaluatedCase evaluate(Case case_, Map<String, Object> predict);

    /**
     * Batch evaluates multiple cases against predictions.
     *
     * @param cases the list of cases
     * @param predicts the list of predictions
     * @return the list of evaluated cases
     */
    public List<EvaluatedCase> batchEvaluate(List<Case> cases, List<Map<String, Object>> predicts) {
        if (cases.size() != predicts.size()) {
            throw new IllegalArgumentException(
                    String.format("length of cases: %d does not equal with length of predicts: %d",
                            cases.size(), predicts.size())
            );
        }
        
        return cases.stream()
                .map(case_ -> evaluate(case_, predicts.get(cases.indexOf(case_))))
                .toList();
    }
}