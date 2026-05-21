package com.openjiuwen.auto_harness;

import com.openjiuwen.auto_harness.orchestrator.AutoHarnessOrchestrator;
import com.openjiuwen.auto_harness.schema.AutoHarnessConfig;
import com.openjiuwen.auto_harness.schema.CycleResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code tests.unit_tests.auto_harness.test_orchestrator}.
 * Tests for AutoHarnessOrchestrator initialization, cycle result recording, and factory methods.
 */
class AutoHarnessOrchestratorTest {

    @Test
    void orchestratorInitializesPathsRuntimeProfileAndRegistries() {
        AutoHarnessConfig config = new AutoHarnessConfig();
        config.setDataDir("/tmp/ah");
        config.setWorkspace("/repo/local");
        config.setConfigBootstrapped(true);
        config.setSuggestedLocalRepo("/repo/cache");

        Object agent = new Object();
        AutoHarnessOrchestrator orchestrator = new AutoHarnessOrchestrator(config, agent);

        assertSame(config, orchestrator.getConfig());
        assertSame(agent, orchestrator.getAgent());
        assertEquals("/tmp/ah/experience", orchestrator.getPaths().getExperienceDir().replace('\\', '/'));
        assertEquals("/repo/local", orchestrator.getRuntime().getCurrentWorkspace());
        assertTrue(orchestrator.getRuntime().isConfigBootstrapped());
        assertEquals("/repo/cache", orchestrator.getRuntime().getSuggestedLocalRepo());
        assertNotNull(orchestrator.getProjectProfile());
        assertNotNull(orchestrator.getStageRegistry());
        assertNotNull(orchestrator.getPipelineRegistry());
    }

    @Test
    void orchestratorRecordsAndResetsCycleResults() {
        AutoHarnessOrchestrator orchestrator = new AutoHarnessOrchestrator(new AutoHarnessConfig(), null);
        CycleResult first = new CycleResult();
        first.setSummary("first");
        CycleResult second = new CycleResult();
        second.setError("kaboom");

        orchestrator.recordCycleResult(first);
        orchestrator.recordCycleResult(second);

        assertEquals(2, orchestrator.getResults().size());
        assertEquals("kaboom", orchestrator.getLastCycleResult().getError());

        orchestrator.resetSessionState();
        assertTrue(orchestrator.getResults().isEmpty());
        assertEquals("", orchestrator.getLastCycleResult().getSummary());
    }

    @Test
    void topLevelFactoryCreatesOrchestrator() {
        AutoHarnessOrchestrator orchestrator = AutoHarness.createAutoHarnessOrchestrator(new AutoHarnessConfig(), null);
        assertNotNull(orchestrator);
    }
}
