package com.openjiuwen.agent_evolving.trajectory;

import java.util.ArrayList;
import java.util.List;

/**
 * Query helpers for trajectories.
 */
public final class TrajectoryUtils {

    private TrajectoryUtils() {
    }

    public static List<TrajectoryStep> iterSteps(
            List<Trajectory> trajectories,
            String caseId,
            String operatorId,
            StepKind kind
    ) {
        List<TrajectoryStep> result = new ArrayList<>();
        for (Trajectory trajectory : trajectories != null ? trajectories : List.<Trajectory>of()) {
            if (trajectory == null) {
                continue;
            }
            if (caseId != null && !caseId.equals(trajectory.getCaseId())) {
                continue;
            }
            for (TrajectoryStep step : trajectory.getSteps() != null ? trajectory.getSteps() : List.<TrajectoryStep>of()) {
                if (operatorId != null && !operatorId.equals(step.getOperatorId())) {
                    continue;
                }
                if (kind != null && step.getKindEnum() != kind) {
                    continue;
                }
                result.add(step);
            }
        }
        return result;
    }

    public static List<TrajectoryStep> iterSteps(
            List<Trajectory> trajectories,
            String caseId,
            String operatorId,
            String kind
    ) {
        return iterSteps(trajectories, caseId, operatorId, kind != null ? StepKind.fromValue(kind) : null);
    }

    public static List<TrajectoryStep> getStepsForCaseOperator(
            List<Trajectory> trajectories,
            String caseId,
            String operatorId
    ) {
        return getStepsForCaseOperator(trajectories, caseId, operatorId, StepKind.LLM);
    }

    public static List<TrajectoryStep> getStepsForCaseOperator(
            List<Trajectory> trajectories,
            String caseId,
            String operatorId,
            StepKind kind
    ) {
        return iterSteps(trajectories, caseId, operatorId, kind);
    }
}
