package com.openjiuwen.harness.tools.browser_move.playwright_runtime.runtime_tools;

import com.openjiuwen.harness.tools.ToolOutput;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.BrowserAgentRuntime;

import java.util.Map;

public class BrowserRuntimeHealthTool extends BrowserRuntimeTool {
    public BrowserRuntimeHealthTool(BrowserAgentRuntime runtime) { super(runtime, card("browser_runtime_health")); }
    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        Map<String, Object> health = runtime.health();
        health.put("ok", true);
        health.put("tool", "browser_runtime_health");
        return new ToolOutput(true, health, null);
    }
}
