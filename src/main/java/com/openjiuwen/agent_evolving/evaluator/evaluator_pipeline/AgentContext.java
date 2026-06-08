/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025-2025. All rights reserved.
 */

package com.openjiuwen.agent_evolving.evaluator.evaluator_pipeline;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Context carried between evaluation iterations.
 * <p>
 * Mirrors Python's {@code AgentContext} in
 * {@code openjiuwen/agent_evolving/evaluator/evaluator_pipeline/models.py}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentContext {

    private int iteration = 1;
    private boolean hasSkill = false;
    private IterationResult previousResult;
    private String evolutionSuggestions;
    private Map<String, String> evolutionFiles;
    private int nInputTokens = 0;
    private int nOutputTokens = 0;
    private Map<String, Object> metadata = new LinkedHashMap<>();
}
