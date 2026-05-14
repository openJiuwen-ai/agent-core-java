package com.openjiuwen.auto_harness;

import com.openjiuwen.auto_harness.orchestrator.AutoHarnessOrchestrator;
import com.openjiuwen.auto_harness.schema.AutoHarnessConfig;

/**
 * Minimal Java entry surface for auto_harness.
 */
public final class AutoHarness {

    private AutoHarness() {
    }

    public static AutoHarnessOrchestrator createAutoHarnessOrchestrator(AutoHarnessConfig config, Object agent) {
        return AutoHarnessOrchestrator.createAutoHarnessOrchestrator(config, agent);
    }
}
