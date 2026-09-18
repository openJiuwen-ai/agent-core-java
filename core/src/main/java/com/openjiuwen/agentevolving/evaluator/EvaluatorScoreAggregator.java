/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.evaluator;

import java.util.List;

/**
 * Score aggregation helper.
 *
 * <p>Mirrors Python's {@code _agg_score} in
 * {@code openjiuwen/agent_evolving/evaluator/evaluator.py}.</p>
 */
final class EvaluatorScoreAggregator {

    private EvaluatorScoreAggregator() {
    }

    static double aggregateScore(List<Double> results, String aggregate) {
        if (results == null || results.isEmpty()) {
            return 0.0d;
        }
        if ("first".equals(aggregate)) {
            return results.get(0);
        }
        return mean(results);
    }

    private static double mean(List<Double> results) {
        double total = 0.0d;
        for (double result : results) {
            total += result;
        }
        return total / results.size();
    }
}
