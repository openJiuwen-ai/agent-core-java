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
 * Agent execution payload for one iteration.
 * <p>
 * Mirrors Python's {@code AgentRunResult} in
 * {@code openjiuwen/agent_evolving/evaluator/evaluator_pipeline/models.py}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentRunResult {

    private String finalResponse = "";
    private List<Map<String, Object>> trajectory = new ArrayList<>();
    private double executionTime = 0.0;
    private int tokensUsed = 0;
    private String rawOutput = "";
    private String stderr = "";
    private List<Map<String, Object>> evolutionEvents = new ArrayList<>();
    private Map<String, Object> metadata = new LinkedHashMap<>();
    private Map<String, String> llmLogs;
}
