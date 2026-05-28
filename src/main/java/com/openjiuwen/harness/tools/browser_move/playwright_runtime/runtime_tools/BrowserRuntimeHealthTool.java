package com.openjiuwen.harness.tools.browser_move.playwright_runtime.runtime_tools;

import com.openjiuwen.harness.tools.ToolOutput;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.BrowserAgentRuntime;

import java.util.Map;

/**
 * Return runtime readiness, heartbeat status, and provider/model configuration.
 *
 * <p>Mirrors Python's {@code BrowserRuntimeHealthTool} in
 * {@code openjiuwen.harness.tools.browser_move.playwright_runtime.runtime_tools}.</p>
 */
public class BrowserRuntimeHealthTool extends BrowserRuntimeTool {
    public BrowserRuntimeHealthTool(BrowserAgentRuntime runtime) { super(runtime, card("browser_runtime_health")); }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        Map<String, Object> health = runtime.runtimeHealth();
        return new ToolOutput(
                Boolean.TRUE.equals(health.get("ok")),
                health,
                null
        );
    }
}