package com.openjiuwen.auto_harness;

import com.openjiuwen.unit_tests.auto_harness.OrchestratorParityAssertions;

import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's {@code tests.unit_tests.auto_harness.test_orchestrator}.
 */
class AutoHarnessOrchestratorTest {

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
