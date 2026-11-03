/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.browser;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.harness.tools.ToolOutput;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * BrowserRuntimeTools.
 * @since 0.1.7
 */
public final class BrowserRuntimeTools {
    /**
     * BrowserRuntimeTools.
     * @since 0.1.7
     */
    private BrowserRuntimeTools() {
    }

    /**
     * buildBrowserRuntimeTools.
     * @param runtime runtime
     * @return the result
     * @since 0.1.7
     */
    public static List<Object> buildBrowserRuntimeTools(BrowserAgentRuntime runtime) {
        return List.of(new BrowserCancelTool(runtime), new BrowserClearCancelTool(runtime),
                new BrowserCustomActionTool(runtime), new BrowserListActionsTool(runtime),
                new BrowserRuntimeHealthTool(runtime));
    }

    /**
     * buildBrowserRuntimeToolFunctions.
     * @param runtime runtime
     * @param ownerId ownerId
     * @return the result
     * @since 0.1.7
     */
    public static List<Tool> buildBrowserRuntimeToolFunctions(BrowserAgentRuntime runtime, String ownerId) {
        String prefix = ownerId != null && !ownerId.isBlank() ? ownerId : "browser_agent";
        return List.of(
                browserTool(prefix, "browser_cancel", "Cancel a browser runtime request.",
                        mapSchema(Map.of("session_id", stringSchema("Browser session id."), "request_id",
                                stringSchema("Browser request id."))),
                        inputs -> runtime.cancelRun(stringValue(inputs.get("session_id")),
                                stringValue(inputs.get("request_id")))),
                browserTool(prefix, "browser_clear_cancel", "Clear browser runtime cancellation state.",
                        mapSchema(Map.of("session_id", stringSchema("Browser session id."), "request_id",
                                stringSchema("Browser request id."))),
                        inputs -> runtime.clearCancel(stringValue(inputs.get("session_id")),
                                stringValue(inputs.get("request_id")))),
                browserTool(prefix, "browser_custom_action", "Run a named browser action.",
                        mapSchema(Map.of("action", stringSchema("Browser action name."), "session_id",
                                stringSchema("Browser session id."), "request_id", stringSchema("Browser request id."),
                                "params", objectSchema("Action parameters."))),
                        inputs -> runtime.runCustomAction(stringValue(inputs.get("action")),
                                stringValue(inputs.get("session_id")), stringValue(inputs.get("request_id")),
                                objectMap(inputs.get("params")))),
                browserTool(prefix, "browser_list_actions", "List browser runtime actions.", mapSchema(Map.of()),
                        inputs -> runtime.listActions()),
                browserTool(prefix, "browser_runtime_health", "Inspect browser runtime health.", mapSchema(Map.of()),
                        inputs -> runtime.runtimeHealth()));
    }

    /**
     * Public record BrowserCancelTool used by the Java parity implementation.
     * @since 0.1.7
     */
    public record BrowserCancelTool(BrowserAgentRuntime runtime) {
        /**
         * invoke.
         * @param inputs inputs
         * @return the result
         * @since 0.1.7
         */
        public ToolOutput invoke(Map<String, Object> inputs) {
            String requestId = inputs.get("request_id") instanceof String value ? value : null;
            return ToolOutput.builder().success(true)
                    .data(runtime.cancelRun(String.valueOf(inputs.get("session_id")), requestId)).build();
        }
    }

    /**
     * Public record BrowserClearCancelTool used by the Java parity implementation.
     * @since 0.1.7
     */
    public record BrowserClearCancelTool(BrowserAgentRuntime runtime) {
        /**
         * invoke.
         * @param inputs inputs
         * @return the result
         * @since 0.1.7
         */
        public ToolOutput invoke(Map<String, Object> inputs) {
            String requestId = inputs.get("request_id") instanceof String value ? value : null;
            return ToolOutput.builder().success(true)
                    .data(runtime.clearCancel(String.valueOf(inputs.get("session_id")), requestId)).build();
        }
    }

    /**
     * Public record BrowserCustomActionTool used by the Java parity implementation.
     * @since 0.1.7
     */
    public record BrowserCustomActionTool(BrowserAgentRuntime runtime) {
        /**
         * invoke.
         * @param inputs inputs
         * @return the result
         * @since 0.1.7
         */
        public ToolOutput invoke(Map<String, Object> inputs) {
            return ToolOutput.builder().success(true)
                    .data(runtime.runCustomAction(String.valueOf(inputs.get("action")),
                            String.valueOf(inputs.get("session_id")), String.valueOf(inputs.get("request_id")),
                            objectMap(inputs.getOrDefault("params", Map.of()))))
                    .build();
        }
    }

    /**
     * Public record BrowserListActionsTool used by the Java parity implementation.
     * @since 0.1.7
     */
    public record BrowserListActionsTool(BrowserAgentRuntime runtime) {
        /**
         * invoke.
         * @param inputs inputs
         * @return the result
         * @since 0.1.7
         */
        public ToolOutput invoke(Map<String, Object> inputs) {
            return ToolOutput.builder().success(true).data(runtime.listActions()).build();
        }
    }

    /**
     * Public record BrowserRuntimeHealthTool used by the Java parity implementation.
     * @since 0.1.7
     */
    public record BrowserRuntimeHealthTool(BrowserAgentRuntime runtime) {
        /**
         * invoke.
         * @param inputs inputs
         * @return the result
         * @since 0.1.7
         */
        public ToolOutput invoke(Map<String, Object> inputs) {
            return ToolOutput.builder().success(true).data(runtime.runtimeHealth()).build();
        }
    }

    /**
     * browserTool.
     * @param prefix prefix
     * @param name name
     * @param description description
     * @param inputParams inputParams
     * @param function function
     * @return the result
     * @since 0.1.7
     */
    private static Tool browserTool(String prefix, String name, String description, Map<String, Object> inputParams,
            java.util.function.Function<Map<String, Object>, Object> function) {
        return new LocalFunction(ToolCard.builder().id(prefix + "." + name).name(name).description(description)
                .inputParams(inputParams).build(), function);
    }

    /**
     * mapSchema.
     * @param properties properties
     * @return the result
     * @since 0.1.7
     */
    private static Map<String, Object> mapSchema(Map<String, Object> properties) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        return schema;
    }

    /**
     * stringSchema.
     * @param description description
     * @return the result
     * @since 0.1.7
     */
    private static Map<String, Object> stringSchema(String description) {
        return Map.of("type", "string", "description", description);
    }

    /**
     * objectSchema.
     * @param description description
     * @return the result
     * @since 0.1.7
     */
    private static Map<String, Object> objectSchema(String description) {
        return Map.of("type", "object", "description", description, "default", Map.of());
    }

    @SuppressWarnings("unchecked")
    /**
     * objectMap.
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private static Map<String, Object> objectMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return result;
        }
        return Map.of();
    }

    /**
     * stringValue.
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private static String stringValue(Object value) {
        return value != null ? String.valueOf(value) : null;
    }
}
