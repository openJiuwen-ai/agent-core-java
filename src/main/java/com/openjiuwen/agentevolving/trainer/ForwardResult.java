/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.trainer;

import com.openjiuwen.agentevolving.dataset.EvaluatedCase;
import com.openjiuwen.agentevolving.trajectory.Trajectory;

import java.util.List;

/**
 * Forward result: aggregate score, evaluated cases, extracted trajectories, and sessions.
 * 
 * @since 0.1.7
 */
public record ForwardResult(double score, List<EvaluatedCase> evaluatedCases, List<Trajectory> trajectories,
        List<Object> sessions) {
}
