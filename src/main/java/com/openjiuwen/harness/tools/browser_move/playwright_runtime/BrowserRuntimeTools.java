/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.browser_move.playwright_runtime;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.harness.tools.AbstractHarnessTool;
import com.openjiuwen.harness.tools.ToolOutput;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * DeepAgent tool wrappers around {@link BrowserAgentRuntime}.
 *
 * <p>Mirrors Python's runtime tools in
 * {@code openjiuwen/harness/tools/browser_move/playwright_runtime/runtime_tools.py}.</p>
 */
public final class BrowserRuntimeTools {

    private BrowserRuntimeTools() {
    }

    public static List<Tool> buildBrowserRuntimeTools(BrowserAgentRuntime runtime, String language) {
        List<Tool> tools = new ArrayList<>();
        tools.add(new BrowserCancelTool(runtime));
        tools.add(new BrowserClearCancelTool(runtime));
        tools.add(new BrowserCustomActionTool(runtime));
        tools.add(new BrowserListActionsTool(runtime));
        tools.add(new BrowserProbeInteractivesTool(runtime));
        tools.add(new BrowserProbeCardsTool(runtime));
        tools.add(new BrowserRuntimeHealthTool(runtime));
        return tools;
    }

    /**
     * Cancel an in-progress browser task.
     *
     * <p>Mirrors Python's {@code BrowserCancelTool} in
     * {@code openjiuwen/harness/tools/browser_move/playwright_runtime/runtime_tools.py}.</p>
     */
    public static final class BrowserCancelTool extends AbstractHarnessTool {
        private final BrowserAgentRuntime runtime;

        public BrowserCancelTool(BrowserAgentRuntime runtime) {
            super(toolCard("browser_cancel_run", "browser_cancel_run",
                    "Cancel an in-progress browser task by session_id."));
            this.runtime = runtime;
        }

        @Override
        protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) {
            return ToolOutput.success(runtime.cancelRun(requiredString(inputs, "session_id"),
                    stringValue(inputs.get("request_id"))));
        }
    }

    /**
     * Clear a cancellation flag for a browser task.
     *
     * <p>Mirrors Python's {@code BrowserClearCancelTool} in
     * {@code openjiuwen/harness/tools/browser_move/playwright_runtime/runtime_tools.py}.</p>
     */
    public static final class BrowserClearCancelTool extends AbstractHarnessTool {
        private final BrowserAgentRuntime runtime;

        public BrowserClearCancelTool(BrowserAgentRuntime runtime) {
            super(toolCard("browser_clear_cancel", "browser_clear_cancel",
                    "Clear the cancellation flag for a browser session or request."));
            this.runtime = runtime;
        }

        @Override
        protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) {
            return ToolOutput.success(runtime.clearCancel(requiredString(inputs, "session_id"),
                    stringValue(inputs.get("request_id"))));
        }
    }

    /**
     * Run a registered custom browser action.
     *
     * <p>Mirrors Python's {@code BrowserCustomActionTool} in
     * {@code openjiuwen/harness/tools/browser_move/playwright_runtime/runtime_tools.py}.</p>
     */
    public static final class BrowserCustomActionTool extends AbstractHarnessTool {
        private final BrowserAgentRuntime runtime;

        public BrowserCustomActionTool(BrowserAgentRuntime runtime) {
            super(toolCard("browser_custom_action", "browser_custom_action",
                    "Run a registered custom browser action by name."));
            this.runtime = runtime;
        }

        @Override
        protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) {
            Map<String, Object> data = runtime.runCustomAction(
                    requiredString(inputs, "action"),
                    stringValue(inputs.get("session_id")),
                    stringValue(inputs.get("request_id")),
                    stringObjectMap(inputs.get("params"))
            );
            return ToolOutput.of(Boolean.TRUE.equals(data.get("ok")), data, stringValue(data.get("error")));
        }
    }

    /**
     * List available custom browser actions.
     *
     * <p>Mirrors Python's {@code BrowserListActionsTool} in
     * {@code openjiuwen/harness/tools/browser_move/playwright_runtime/runtime_tools.py}.</p>
     */
    public static final class BrowserListActionsTool extends AbstractHarnessTool {
        private final BrowserAgentRuntime runtime;

        public BrowserListActionsTool(BrowserAgentRuntime runtime) {
            super(toolCard("browser_list_custom_actions", "browser_list_custom_actions",
                    "List available custom browser actions and parameter guidance."));
            this.runtime = runtime;
        }

        @Override
        protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) {
            return ToolOutput.success(runtime.listActions());
        }
    }

    /**
     * Compact visible-interactive-element probe.
     *
     * <p>Mirrors Python's {@code BrowserProbeInteractivesTool} in
     * {@code openjiuwen/harness/tools/browser_move/playwright_runtime/runtime_tools.py}.</p>
     */
    public static final class BrowserProbeInteractivesTool extends AbstractHarnessTool {
        private final BrowserAgentRuntime runtime;

        public BrowserProbeInteractivesTool(BrowserAgentRuntime runtime) {
            super(toolCard("browser_probe_interactives", "browser_probe_interactives",
                    "Return compact visible, high-value interactive elements on the current page."));
            this.runtime = runtime;
        }

        @Override
        protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) {
            Map<String, Object> data = runtime.probeInteractives(
                    intValue(inputs.get("max_items"), 50),
                    boolValue(inputs.get("viewport_only"), true),
                    stringValue(inputs.get("query"))
            );
            return ToolOutput.of(Boolean.TRUE.equals(data.get("ok")), data, stringValue(data.get("error")));
        }
    }

    /**
     * Compact repeated-card/listing probe.
     *
     * <p>Mirrors Python's {@code BrowserProbeCardsTool} in
     * {@code openjiuwen/harness/tools/browser_move/playwright_runtime/runtime_tools.py}.</p>
     */
    public static final class BrowserProbeCardsTool extends AbstractHarnessTool {
        private final BrowserAgentRuntime runtime;

        public BrowserProbeCardsTool(BrowserAgentRuntime runtime) {
            super(toolCard("browser_probe_cards", "browser_probe_cards",
                    "Return compact repeated card/listing structures from the current page."));
            this.runtime = runtime;
        }

        @Override
        protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) {
            Map<String, Object> data = runtime.probeCards(
                    intValue(inputs.get("max_cards"), 20),
                    boolValue(inputs.get("viewport_only"), true),
                    boolValue(inputs.get("include_buttons"), true),
                    stringValue(inputs.get("query"))
            );
            return ToolOutput.of(Boolean.TRUE.equals(data.get("ok")), data, stringValue(data.get("error")));
        }
    }

    /**
     * Return runtime readiness and heartbeat metadata.
     *
     * <p>Mirrors Python's {@code BrowserRuntimeHealthTool} in
     * {@code openjiuwen/harness/tools/browser_move/playwright_runtime/runtime_tools.py}.</p>
     */
    public static final class BrowserRuntimeHealthTool extends AbstractHarnessTool {
        private final BrowserAgentRuntime runtime;

        public BrowserRuntimeHealthTool(BrowserAgentRuntime runtime) {
            super(toolCard("browser_runtime_health", "browser_runtime_health",
                    "Return runtime readiness, heartbeat status, and provider/model configuration."));
            this.runtime = runtime;
        }

        @Override
        protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) {
            return ToolOutput.success(runtime.runtimeHealth());
        }
    }
}
