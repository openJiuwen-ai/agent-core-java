package com.openjiuwen.harness.tools.browser_move.playwright_runtime.runtime_tools;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.BrowserAgentRuntime;

import java.util.List;

public final class BrowserRuntimeTools {
    private BrowserRuntimeTools() {}

    public static List<Tool> buildBrowserRuntimeTools(BrowserAgentRuntime runtime) {
        return List.of(
                new BrowserCancelTool(runtime),
                new BrowserClearCancelTool(runtime),
                new BrowserCustomActionTool(runtime),
                new BrowserListActionsTool(runtime),
                new BrowserRuntimeHealthTool(runtime)
        );
    }
}
