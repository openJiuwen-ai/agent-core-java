  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.agent_evolving.trainer;

import com.openjiuwen.agent_evolving.dataset.EvaluatedCase;

import java.util.List;

/**
 * Evaluation result: aggregate score plus evaluated cases.
 */
public record EvaluationResult(double score, List<EvaluatedCase> evaluatedCases) {
}
