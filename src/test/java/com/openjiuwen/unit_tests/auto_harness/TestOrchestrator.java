/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.auto_harness;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Orchestrator tests.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.auto_harness.test_orchestrator}.</p>
 */
@DisplayName("TestOrchestrator")
class TestOrchestrator {

    @Test
    void testSessionSelectsMetaPipelineBeforeRunning() throws Exception {
        OrchestratorParityAssertions.testSessionSelectsMetaPipelineBeforeRunning();
    }

    @Test
    void testAssessAndPlanUseReadonlySnapshot() throws Exception {
        OrchestratorParityAssertions.testAssessAndPlanUseReadonlySnapshot();
    }

    @Test
    void testDirectTasksSkipAssessAndPlan() throws Exception {
        OrchestratorParityAssertions.testDirectTasksSkipAssessAndPlan();
    }

    @Test
    void testSessionStreamPassthroughsAssessAndPlanChunks() throws Exception {
        OrchestratorParityAssertions.testSessionStreamPassthroughsAssessAndPlanChunks();
    }

    @Test
    void testPlanStageKeepsOnlyFirstPlannedTask() throws Exception {
        OrchestratorParityAssertions.testPlanStageKeepsOnlyFirstPlannedTask();
    }

    @Test
    void testCapsTasks() throws Exception {
        OrchestratorParityAssertions.testCapsTasks();
    }

    @Test
    void testPrepareTaskRuntimeCreatesTaskSessionAndFixAgent() throws Exception {
        OrchestratorParityAssertions.testPrepareTaskRuntimeCreatesTaskSessionAndFixAgent();
    }

    @Test
    void testTimeoutHandling() throws Exception {
        OrchestratorParityAssertions.testTimeoutHandling();
    }

    @Test
    void testExceptionHandling() throws Exception {
        OrchestratorParityAssertions.testExceptionHandling();
    }

    @Test
    void testResolveTaskResultFromArtifact() throws Exception {
        OrchestratorParityAssertions.testResolveTaskResultFromArtifact();
    }

    @Test
    void testOrchestratorInitializesTaskContexts() throws Exception {
        OrchestratorParityAssertions.testOrchestratorInitializesTaskContexts();
    }
}
