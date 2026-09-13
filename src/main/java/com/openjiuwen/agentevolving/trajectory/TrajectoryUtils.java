/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.trajectory;

import java.util.ArrayList;
import java.util.List;

/**
 * Query helpers for trajectories.
 * 
 * @since 0.1.7
 */
public final class TrajectoryUtils {
    /**
     * TrajectoryUtils.
     * 
     * @since 0.1.7
     */
    private TrajectoryUtils() {
    }

    /**
     * iterSteps.
     * 
     * @param trajectories trajectories
     * @param caseId caseId
     * @param operatorId operatorId
     * @param kind kind
     * @return the result
     * @since 0.1.7
     */
    public static List<TrajectoryStep> iterSteps(List<Trajectory> trajectories, String caseId, String operatorId,
            StepKind kind) {
        List<TrajectoryStep> result = new ArrayList<>();
        for (Trajectory trajectory : trajectories != null ? trajectories : List.<Trajectory>of()) {
            if (trajectory == null) {
                continue;
            }
            if (caseId != null && !caseId.equals(trajectory.getCaseId())) {
                continue;
            }
            for (TrajectoryStep step : trajectory.getSteps() != null
                    ? trajectory.getSteps()
                    : List.<TrajectoryStep>of()) {
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

    /**
     * iterSteps.
     * 
     * @param trajectories trajectories
     * @param caseId caseId
     * @param operatorId operatorId
     * @param kind kind
     * @return the result
     * @since 0.1.7
     */
    public static List<TrajectoryStep> iterSteps(List<Trajectory> trajectories, String caseId, String operatorId,
            String kind) {
        return iterSteps(trajectories, caseId, operatorId, kind != null ? StepKind.fromValue(kind) : null);
    }

    /**
     * getStepsForCaseOperator.
     * 
     * @param trajectories trajectories
     * @param caseId caseId
     * @param operatorId operatorId
     * @return the result
     * @since 0.1.7
     */
    public static List<TrajectoryStep> getStepsForCaseOperator(List<Trajectory> trajectories, String caseId,
            String operatorId) {
        return getStepsForCaseOperator(trajectories, caseId, operatorId, StepKind.LLM);
    }

    /**
     * getStepsForCaseOperator.
     * 
     * @param trajectories trajectories
     * @param caseId caseId
     * @param operatorId operatorId
     * @param kind kind
     * @return the result
     * @since 0.1.7
     */
    public static List<TrajectoryStep> getStepsForCaseOperator(List<Trajectory> trajectories, String caseId,
            String operatorId, StepKind kind) {
        return iterSteps(trajectories, caseId, operatorId, kind);
    }
}
