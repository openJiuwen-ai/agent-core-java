/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.agent_evolving.trajectory;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TrajectoryUtils.
 * 
 * <p>NOTE: No dedicated Python test file for TrajectoryUtils.
 * Tests are derived from the Java implementation behavior.
 */
@ExtendWith(MockitoExtension.class)
class TrajectoryUtilsTest {

    private Trajectory makeTrajectory(String execId, String caseId, List<TrajectoryStep> steps) {
        Trajectory traj = new Trajectory();
        traj.setExecutionId(execId);
        traj.setCaseId(caseId);
        traj.setSteps(steps != null ? steps : new ArrayList<>());
        return traj;
    }

    private TrajectoryStep makeStep(String operatorId, StepKind kind) {
        TrajectoryStep step = new TrajectoryStep();
        step.setOperatorId(operatorId);
        step.setKind(kind.value());
        return step;
    }

    @Test
    @Tag("level0")
    @DisplayName("Test iterSteps with null trajectories returns empty list")
    void testIterStepsNullTrajectories() {
        List<TrajectoryStep> result = TrajectoryUtils.iterSteps(null, null, null, (StepKind) null);
        assertTrue(result.isEmpty());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test iterSteps with empty trajectories returns empty list")
    void testIterStepsEmptyTrajectories() {
        List<TrajectoryStep> result = TrajectoryUtils.iterSteps(List.of(), null, null, (StepKind) null);
        assertTrue(result.isEmpty());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test iterSteps filters by caseId")
    void testIterStepsFilterByCaseId() {
        TrajectoryStep step1 = makeStep("op1", StepKind.LLM);
        Trajectory traj1 = makeTrajectory("exec1", "case1", List.of(step1));
        
        TrajectoryStep step2 = makeStep("op2", StepKind.LLM);
        Trajectory traj2 = makeTrajectory("exec2", "case2", List.of(step2));

        List<Trajectory> trajectories = List.of(traj1, traj2);
        List<TrajectoryStep> result = TrajectoryUtils.iterSteps(trajectories, "case1", null, (StepKind) null);

        assertEquals(1, result.size());
        assertEquals("op1", result.get(0).getOperatorId());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test iterSteps filters by operatorId")
    void testIterStepsFilterByOperatorId() {
        TrajectoryStep step1 = makeStep("op1", StepKind.LLM);
        TrajectoryStep step2 = makeStep("op2", StepKind.LLM);
        Trajectory traj = makeTrajectory("exec1", "case1", List.of(step1, step2));

        List<Trajectory> trajectories = List.of(traj);
        List<TrajectoryStep> result = TrajectoryUtils.iterSteps(trajectories, null, "op1", (StepKind) null);

        assertEquals(1, result.size());
        assertEquals("op1", result.get(0).getOperatorId());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test iterSteps filters by kind")
    void testIterStepsFilterByKind() {
        TrajectoryStep step1 = makeStep("op1", StepKind.LLM);
        TrajectoryStep step2 = makeStep("op2", StepKind.TOOL);
        Trajectory traj = makeTrajectory("exec1", "case1", List.of(step1, step2));

        List<Trajectory> trajectories = List.of(traj);
        List<TrajectoryStep> result = TrajectoryUtils.iterSteps(trajectories, null, null, StepKind.LLM);

        assertEquals(1, result.size());
        assertEquals(StepKind.LLM, result.get(0).getKindEnum());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test iterSteps with string kind")
    void testIterStepsWithStringKind() {
        TrajectoryStep step1 = makeStep("op1", StepKind.LLM);
        TrajectoryStep step2 = makeStep("op2", StepKind.TOOL);
        Trajectory traj = makeTrajectory("exec1", "case1", List.of(step1, step2));

        List<Trajectory> trajectories = List.of(traj);
        List<TrajectoryStep> result = TrajectoryUtils.iterSteps(trajectories, null, null, "llm");

        assertEquals(1, result.size());
        assertEquals(StepKind.LLM, result.get(0).getKindEnum());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test getStepsForCaseOperator defaults to LLM kind")
    void testGetStepsForCaseOperatorDefaultKind() {
        TrajectoryStep step1 = makeStep("op1", StepKind.LLM);
        TrajectoryStep step2 = makeStep("op1", StepKind.TOOL);
        Trajectory traj = makeTrajectory("exec1", "case1", List.of(step1, step2));

        List<Trajectory> trajectories = List.of(traj);
        List<TrajectoryStep> result = TrajectoryUtils.getStepsForCaseOperator(trajectories, "case1", "op1");

        assertEquals(1, result.size());
        assertEquals(StepKind.LLM, result.get(0).getKindEnum());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test iterSteps combines all filters")
    void testIterStepsCombinedFilters() {
        TrajectoryStep step1 = makeStep("op1", StepKind.LLM);
        TrajectoryStep step2 = makeStep("op2", StepKind.LLM);
        TrajectoryStep step3 = makeStep("op1", StepKind.TOOL);
        Trajectory traj1 = makeTrajectory("exec1", "case1", List.of(step1, step2, step3));

        TrajectoryStep step4 = makeStep("op1", StepKind.LLM);
        Trajectory traj2 = makeTrajectory("exec2", "case2", List.of(step4));

        List<Trajectory> trajectories = List.of(traj1, traj2);
        
        // Filter by case1, op1, LLM
        List<TrajectoryStep> result = TrajectoryUtils.iterSteps(trajectories, "case1", "op1", StepKind.LLM);

        assertEquals(1, result.size());
        assertEquals("op1", result.get(0).getOperatorId());
        assertEquals(StepKind.LLM, result.get(0).getKindEnum());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test iterSteps handles trajectory with null steps")
    void testIterStepsNullSteps() {
        Trajectory traj = makeTrajectory("exec1", "case1", null);

        List<Trajectory> trajectories = List.of(traj);
        List<TrajectoryStep> result = TrajectoryUtils.iterSteps(trajectories, null, null, (StepKind) null);

        assertTrue(result.isEmpty());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test iterSteps skips null trajectories")
    void testIterStepsSkipsNullTrajectory() {
        TrajectoryStep step = makeStep("op1", StepKind.LLM);
        Trajectory traj = makeTrajectory("exec1", "case1", List.of(step));

        List<Trajectory> trajectories = Arrays.asList(null, traj);
        List<TrajectoryStep> result = TrajectoryUtils.iterSteps(trajectories, null, null, (StepKind) null);

        assertEquals(1, result.size());
    }
}