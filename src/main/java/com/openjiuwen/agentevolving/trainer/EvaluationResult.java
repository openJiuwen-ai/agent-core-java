/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.trainer;

import com.openjiuwen.agentevolving.dataset.EvaluatedCase;

import java.util.List;

/**
 * Evaluation result: aggregate score plus evaluated cases.
 * 
 * @since 0.1.7
 */
public record EvaluationResult(double score, List<EvaluatedCase> evaluatedCases) {
}
