/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025-2025. All rights reserved.
 */

package com.openjiuwen.agentevolving.evaluator.evaluator_pipeline;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Command execution result.
 * <p>
 * Mirrors Python's {@code ExecResult} in
 * {@code openjiuwen/agent_evolving/evaluator/evaluator_pipeline/models.py}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExecResult {

    private String stdout = "";
    private String stderr = "";
    private int returncode = -1;
    private boolean timedOut = false;

    public boolean isSuccess() {
        return returncode == 0;
    }
}
