package com.openjiuwen.harness.tools.browser_move.playwright_runtime;

import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.rails.DeepAgentRail;

/**
 * Mirrors Python's {@code BrowserRuntimeRail} in
 * {@code openjiuwen.harness.tools.browser_move.playwright_runtime.runtime}.
 */
public class BrowserRuntimeRail extends DeepAgentRail {

    private final BrowserAgentRuntime runtime;

    public BrowserRuntimeRail(BrowserAgentRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    public void init(Object agent) {
        if (agent instanceof DeepAgent) {
            runtime.ensureStarted();
        }
    }

    public BrowserAgentRuntime getRuntime() {
        return runtime;
    }
}
