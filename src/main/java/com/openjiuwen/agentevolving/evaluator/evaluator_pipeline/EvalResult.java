/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025-2025. All rights reserved.
 */

package com.openjiuwen.agentevolving.evaluator.evaluator_pipeline;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Evaluation result for one agent iteration.
 * <p>
 * Mirrors Python's {@code EvalResult} in
 * {@code openjiuwen/agent_evolving/evaluator/evaluator_pipeline/models.py}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EvalResult {

    private boolean passed = false;
    private double passRate = 0.0;
    private String testOutput = "";
    private int returncode = -1;
    private List<String> failedTests = new ArrayList<>();
    private Map<String, Object> testDetails = new LinkedHashMap<>();
}
