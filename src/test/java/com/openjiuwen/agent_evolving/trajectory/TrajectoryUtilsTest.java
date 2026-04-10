/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.agent_evolving.trajectory;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrajectoryUtilsTest {

    @Test
    void executionSpecTwoArgConstructorKeepsOptionalFieldsNull() {
        ExecutionSpec spec = new ExecutionSpec("case_1", "exec_1");

        assertEquals("case_1", spec.getCaseId());
        assertEquals("exec_1", spec.getExecutionId());
        assertEquals(null, spec.getSeed());
        assertEquals(null, spec.getTags());
    }

    @Test
    void trajectoryStepStringKindMapsPluginAliasToTool() {
        TrajectoryStep step = TrajectoryStep.builder()
                .kind("plugin")
                .operatorId("op_1")
                .inputs(Map.of())
                .outputs(Map.of())
                .meta(Map.of())
                .build();

        assertEquals("tool", step.getKind());
    }

    @Test
    void iterStepsReturnsAllStepsWithoutFilters() {
        Trajectory trajectory = new Trajectory(
                "case_1",
                "exec_1",
                null,
                List.of(
                        step(StepKind.LLM, "op_1"),
                        step(StepKind.TOOL, "op_2")
                ),
                null
        );

        List<TrajectoryStep> result = TrajectoryUtils.iterSteps(List.of(trajectory), null, null, (StepKind) null);

        assertEquals(2, result.size());
    }

    @Test
    void iterStepsFiltersByCaseOperatorAndKind() {
        Trajectory first = new Trajectory(
                "case_1",
                "exec_1",
                null,
                List.of(
                        step(StepKind.LLM, "op_1"),
                        step(StepKind.TOOL, "op_1")
                ),
                null
        );
        Trajectory second = new Trajectory(
                "case_2",
                "exec_2",
                null,
                List.of(step(StepKind.LLM, "op_1")),
                null
        );

        List<TrajectoryStep> result = TrajectoryUtils.iterSteps(List.of(first, second), "case_1", "op_1", StepKind.LLM);

        assertEquals(1, result.size());
        assertEquals("llm", result.getFirst().getKind());
        assertEquals("op_1", result.getFirst().getOperatorId());
    }

    @Test
    void getStepsForCaseOperatorDefaultsToLlmKind() {
        Trajectory trajectory = new Trajectory(
                "case_1",
                "exec_1",
                null,
                List.of(
                        step(StepKind.LLM, "op_1"),
                        step(StepKind.TOOL, "op_1")
                ),
                null
        );

        List<TrajectoryStep> result = TrajectoryUtils.getStepsForCaseOperator(List.of(trajectory), "case_1", "op_1");

        assertEquals(1, result.size());
        assertEquals("llm", result.getFirst().getKind());
    }

    @Test
    void iterStepsReturnsEmptyWhenNothingMatches() {
        Trajectory trajectory = new Trajectory(
                "case_1",
                "exec_1",
                null,
                List.of(step(StepKind.LLM, "op_1")),
                null
        );

        List<TrajectoryStep> result = TrajectoryUtils.iterSteps(List.of(trajectory), "case_1", "op_1", StepKind.TOOL);

        assertTrue(result.isEmpty());
    }

    private static TrajectoryStep step(StepKind kind, String operatorId) {
        return TrajectoryStep.builder()
                .kind(kind)
                .operatorId(operatorId)
                .inputs(Map.of())
                .outputs(Map.of())
                .meta(Map.of())
                .build();
    }
}
