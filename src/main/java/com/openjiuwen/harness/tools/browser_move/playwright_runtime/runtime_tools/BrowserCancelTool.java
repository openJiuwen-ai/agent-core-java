package com.openjiuwen.harness.tools.browser_move.playwright_runtime.runtime_tools;

import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.harness.tools.ToolOutput;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.BrowserAgentRuntime;

import java.util.Map;

/**
 * Cancel an in-progress browser task by session_id.
 *
 * <p>Mirrors Python's {@code BrowserCancelTool} in
 * {@code openjiuwen.harness.tools.browser_move.playwright_runtime.runtime_tools}.</p>
 */
public class BrowserCancelTool extends BrowserRuntimeTool {
    public BrowserCancelTool(BrowserAgentRuntime runtime) { super(runtime, card("browser_cancel_run")); }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        runtime.ensureRuntimeReady();
        String sessionId = getStringInput(inputs, "session_id", "");
        String requestId = getStringInput(inputs, "request_id", null);

        try {
            Map<String, Object> result = runtime.cancelRun(sessionId, requestId);
            return new ToolOutput(
                    Boolean.TRUE.equals(result.get("ok")),
                    result,
                    result.get("error") != null ? String.valueOf(result.get("error")) : null
            );
        } catch (Exception exc) {
            return new ToolOutput(false, null, exc.getMessage());
        }
    }

    protected String getStringInput(Map<String, Object> inputs, String key, String defaultValue) {
        if (!inputs.containsKey(key) || inputs.get(key) == null) {
            return defaultValue;
        }
        return String.valueOf(inputs.get(key)).trim();
    }
}