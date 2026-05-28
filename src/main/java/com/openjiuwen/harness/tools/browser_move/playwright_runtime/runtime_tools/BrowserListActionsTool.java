package com.openjiuwen.harness.tools.browser_move.playwright_runtime.runtime_tools;

import com.openjiuwen.harness.tools.ToolOutput;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.BrowserAgentRuntime;

import java.util.Map;

/**
 * List available custom browser actions and detailed parameter guidance.
 *
 * <p>Mirrors Python's {@code BrowserListActionsTool} in
 * {@code openjiuwen.harness.tools.browser_move.playwright_runtime.runtime_tools}.</p>
 */
public class BrowserListActionsTool extends BrowserRuntimeTool {
    public BrowserListActionsTool(BrowserAgentRuntime runtime) { super(runtime, card("browser_list_custom_actions")); }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        Map<String, Object> result = runtime.listActions();
        return new ToolOutput(true, result, null);
    }
}