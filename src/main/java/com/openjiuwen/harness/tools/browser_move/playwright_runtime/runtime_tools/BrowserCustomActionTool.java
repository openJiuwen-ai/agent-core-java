package com.openjiuwen.harness.tools.browser_move.playwright_runtime.runtime_tools;

import com.openjiuwen.harness.tools.ToolOutput;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.BrowserAgentRuntime;

import java.util.Map;

/**
 * Run a registered custom browser action by name.
 *
 * <p>Mirrors Python's {@code BrowserCustomActionTool} in
 * {@code openjiuwen.harness.tools.browser_move.playwright_runtime.runtime_tools}.</p>
 */
public class BrowserCustomActionTool extends BrowserRuntimeTool {
    public BrowserCustomActionTool(BrowserAgentRuntime runtime) { super(runtime, card("browser_custom_action")); }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        runtime.ensureRuntimeReady();

        String action = getStringInput(inputs, "action", "");
        if (action.isEmpty()) {
            return new ToolOutput(false, null, "action parameter is required");
        }

        String sessionId = getStringInput(inputs, "session_id", "");
        String requestId = getStringInput(inputs, "request_id", "");
        Map<String, Object> params = inputs.containsKey("params") && inputs.get("params") instanceof Map
                ? (Map<String, Object>) inputs.get("params")
                : Map.of();

        Map<String, Object> result = runtime.runCustomAction(action, sessionId, requestId, params);
        return new ToolOutput(
                Boolean.TRUE.equals(result.get("ok")),
                result,
                result.get("error") != null ? String.valueOf(result.get("error")) : null
        );
    }

    protected String getStringInput(Map<String, Object> inputs, String key, String defaultValue) {
        if (!inputs.containsKey(key) || inputs.get(key) == null) {
            return defaultValue;
        }
        return String.valueOf(inputs.get(key)).trim();
    }
}