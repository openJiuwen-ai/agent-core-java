/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025-2025. All rights reserved.
 */

package com.openjiuwen.agent_evolving.evaluator.evaluator_pipeline;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * One pipeline iteration result.
 * <p>
 * Mirrors Python's {@code IterationResult} in
 * {@code openjiuwen/agent_evolving/evaluator/evaluator_pipeline/models.py}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IterationResult {

    private int iteration;
    private AgentRunResult agentResult = new AgentRunResult();
    private EvalResult evalResult = new EvalResult();
    private SkillDelta skillDelta = new SkillDelta();
    private boolean skillChanged = false;
    private LocalDateTime startedAt = LocalDateTime.now();
    private LocalDateTime completedAt = LocalDateTime.now();
}
