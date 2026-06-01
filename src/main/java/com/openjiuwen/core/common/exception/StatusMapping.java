/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.exception;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Resolves which exception class to instantiate for a given {@link StatusCode}.
 *
 * <p>Mirrors Python's {@code build_status_exception_map} integration used by
 * {@code openjiuwen.core.common.exception.errors}.</p>
 * <p>
 * Resolution order:
 * <ol>
 *   <li>Manual overrides</li>
 *   <li>Keyword-based rules (matched against the enum member name)</li>
 *   <li>Code-range-based rules</li>
 *   <li>Fallback: {@link ExecutionError}</li>
 * </ol>
 */
public final class StatusMapping {

    private StatusMapping() {
    }

    // ==================== Keyword Rules ====================

    private static final String[][] KEYWORD_RULES = {
        {"INVALID", "VALIDATE", "NOT_SUPPORTED", "PARAM", "MISSING", "DUPLICATED", "CONFIG", "SCHEMA", "FORMAT", "TEMPLATE"},
        {"INIT", "CONNECT", "SERVICE", "QUEUE", "PROVIDER", "CALL", "INVOKE_LLM", "MODEL", "REMOTE"},
        {"TIMEOUT", "EXECUTE", "EXECUTION", "RUNTIME", "PROCESS", "STREAM", "RESPONSE"},
    };

    private static final String[] KEYWORD_EXCEPTION_NAMES = {
        "ValidationError",
        "FrameworkError",
        "ExecutionError",
    };

    // ==================== Range Rules ====================

    private static final int[][] RANGE_BOUNDS = {
        {100000, 119999},
        {120000, 129999},
        {130000, 139999},
        {140000, 149999},
        {150000, 159999},
        {160000, 179999},
        {180000, 189999},
        {190000, 198999},
        {199000, 199999},
    };

    private static final String[] RANGE_EXCEPTION_NAMES = {
        "WorkflowError",
        "AgentError",
        "RunnerError",
        "GraphError",
        "ContextError",
        "ToolchainError",
        "FrameworkError",
        "SessionError",
        "SysOperationError",
    };

    // ==================== Manual Overrides ====================

    private static final Map<StatusCode, String> MANUAL_OVERRIDES;

    static {
        Map<StatusCode, String> overrides = new EnumMap<>(StatusCode.class);
        putIfExists(overrides, "CONTROLLER_INVOKE_LLM_FAILED", "FrameworkError");
        putIfExists(overrides, "TOOL_EXECUTION_ERROR", "ToolError");
        putIfExists(overrides, "TOOL_NOT_FOUND_ERROR", "ValidationError");
        putIfExists(overrides, "AGENT_GROUP_EXECUTION_ERROR", "AgentError");
        MANUAL_OVERRIDES = Collections.unmodifiableMap(overrides);
    }

    private static void putIfExists(Map<StatusCode, String> map, String enumName, String exceptionName) {
        try {
            StatusCode code = StatusCode.valueOf(enumName);
            map.put(code, exceptionName);
        } catch (IllegalArgumentException ignored) {
            // Enum member does not exist in current version — skip silently.
        }
    }

    // ==================== Exception Class Registry ====================

    @SuppressWarnings("unchecked")
    private static final Map<String, Function<StatusCode, BaseError>> EXCEPTION_REGISTRY = Map.ofEntries(
        Map.entry("BaseError", status -> new BaseError(status, null, null, null, null) {}),
        Map.entry("FrameworkError", FrameworkError::new),
        Map.entry("ExecutionError", ExecutionError::new),
        Map.entry("ValidationError", ValidationError::new),
        Map.entry("Termination", Termination::new),
        Map.entry("WorkflowError", WorkflowError::new),
        Map.entry("AgentError", AgentError::new),
        Map.entry("ToolError", ToolError::new),
        Map.entry("GraphError", GraphError::new),
        Map.entry("SessionError", SessionError::new),
        Map.entry("SysOperationError", SysOperationError::new),
        Map.entry("ToolchainError", ToolchainError::new),
        Map.entry("ContextError", ContextError::new),
        Map.entry("RunnerError", RunnerError::new)
    );

    // ==================== Resolution API ====================

    /**
     * Resolve the concrete exception class (as a factory) for the given status code.
     *
     * @param status the status code to resolve
     * @return a factory function that creates the appropriate exception
     */
    public static Function<StatusCode, BaseError> resolveExceptionFactory(StatusCode status) {
        // 1. Manual override
        String excName = MANUAL_OVERRIDES.get(status);

        // 2. Keyword rule
        if (excName == null) {
            excName = matchKeyword(status.name());
        }

        // 3. Range fallback
        if (excName == null) {
            excName = matchRange(status.getCode());
        }

        // 4. Absolute fallback
        if (excName == null) {
            excName = "ExecutionError";
        }

        return EXCEPTION_REGISTRY.getOrDefault(excName, EXCEPTION_REGISTRY.get("ExecutionError"));
    }

    /**
     * Build an exception for the given status code using resolution rules.
     *
     * @param status the status code
     * @return a BaseError instance appropriate for the status
     */
    public static BaseError resolveException(StatusCode status) {
        return resolveExceptionFactory(status).apply(status);
    }

    /**
     * Generate full StatusCode → exception factory mapping for all status codes.
     *
     * @return an unmodifiable map of status codes to exception factories
     */
    public static Map<StatusCode, Function<StatusCode, BaseError>> buildStatusExceptionMap() {
        EnumMap<StatusCode, Function<StatusCode, BaseError>> mapping = new EnumMap<>(StatusCode.class);
        for (StatusCode status : StatusCode.values()) {
            mapping.put(status, resolveExceptionFactory(status));
        }
        return Collections.unmodifiableMap(mapping);
    }

    // ==================== Internal Matching ====================

    private static String matchKeyword(String name) {
        for (int i = 0; i < KEYWORD_RULES.length; i++) {
            for (String keyword : KEYWORD_RULES[i]) {
                if (name.contains(keyword)) {
                    return KEYWORD_EXCEPTION_NAMES[i];
                }
            }
        }
        return null;
    }

    private static String matchRange(int code) {
        for (int i = 0; i < RANGE_BOUNDS.length; i++) {
            if (code >= RANGE_BOUNDS[i][0] && code <= RANGE_BOUNDS[i][1]) {
                return RANGE_EXCEPTION_NAMES[i];
            }
        }
        return null;
    }
}
