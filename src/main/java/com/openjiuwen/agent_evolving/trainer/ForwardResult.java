package com.openjiuwen.agent_evolving.trainer;

import com.openjiuwen.agent_evolving.dataset.EvaluatedCase;
import com.openjiuwen.agent_evolving.trajectory.Trajectory;

import java.util.List;

/**
 * Forward result: aggregate score, evaluated cases, extracted trajectories, and sessions.
 */
public record ForwardResult(
        double score,
        List<EvaluatedCase> evaluatedCases,
        List<Trajectory> trajectories,
        List<Object> sessions
) {
}
