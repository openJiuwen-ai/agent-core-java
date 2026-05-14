package com.openjiuwen.harness.tools.browser_move.playwright_runtime.runtime_tools;

import com.openjiuwen.harness.tools.ToolOutput;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.BrowserAgentRuntime;

import java.util.Map;

public class BrowserListActionsTool extends BrowserRuntimeTool {
    public BrowserListActionsTool(BrowserAgentRuntime runtime) { super(runtime, card("browser_list_custom_actions")); }
    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        return new ToolOutput(true, Map.of(
                "ok", true,
                "actions", java.util.List.of(
                        "browser_cancel",
                        "browser_clear_cancel",
                        "browser_custom_action",
                        "browser_list_custom_actions",
                        "browser_runtime_health"
                )
        ), null);
    }
}
