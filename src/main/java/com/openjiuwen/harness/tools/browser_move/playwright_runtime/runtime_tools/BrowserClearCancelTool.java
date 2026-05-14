package com.openjiuwen.harness.tools.browser_move.playwright_runtime.runtime_tools;

import com.openjiuwen.harness.tools.ToolOutput;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.BrowserAgentRuntime;

import java.util.Map;

public class BrowserClearCancelTool extends BrowserRuntimeTool {
    public BrowserClearCancelTool(BrowserAgentRuntime runtime) { super(runtime, card("browser_clear_cancel")); }
    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        return new ToolOutput(true, Map.of(
                "ok", true,
                "tool", "browser_clear_cancel"
        ), null);
    }
}
