/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.exception;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Mirrors Python's {@code status_mapping.py} in
 * {@code openjiuwen/core/common/exception/status_mapping.py}.
 */
public final class StatusMapping {

    private static final String[][] KEYWORD_RULES = {
            {"INVALID", "VALIDATE", "NOT_SUPPORTED", "PARAM", "MISSING", "DUPLICATED"},
            {"CONFIG", "SCHEMA", "FORMAT", "TEMPLATE"},
            {"INIT", "CONNECT", "SERVICE", "QUEUE", "PROVIDER"},
            {"CALL", "INVOKE_LLM", "MODEL", "REMOTE"},
            {"TIMEOUT", "EXECUTE", "EXECUTION", "RUNTIME", "PROCESS", "STREAM", "RESPONSE"},
    };

    private static final String[] KEYWORD_EXCEPTION_NAMES = {
            "ValidationError",
            "ValidationError",
            "FrameworkError",
            "FrameworkError",
            "ExecutionError",
    };

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

    private static final Map<StatusCode, String> MANUAL_OVERRIDES;

    @SuppressWarnings("unchecked")
    private static final Map<String, Function<StatusCode, BaseError>> EXCEPTION_REGISTRY = Map.ofEntries(
            Map.entry("BaseError", status -> new BaseError(status) {}),
            Map.entry("FrameworkError", FrameworkError::new),
            Map.entry("ConfigurationError", ConfigurationError::new),
            Map.entry("ExecutionError", ExecutionError::new),
            Map.entry("ValidationError", ValidationError::new),
            Map.entry("Termination", Termination::new),
            Map.entry("WorkflowError", WorkflowError::new),
            Map.entry("ComponentError", ComponentError::new),
            Map.entry("AgentError", AgentError::new),
            Map.entry("RunnerError", RunnerError::new),
            Map.entry("GraphError", GraphError::new),
            Map.entry("ModelError", ModelError::new),
            Map.entry("ToolError", status -> new ToolError(status)),
            Map.entry("ContextError", ContextError::new),
            Map.entry("ToolchainError", ToolchainError::new),
            Map.entry("SessionError", SessionError::new),
            Map.entry("SysOperationError", SysOperationError::new),
            Map.entry("GuardrailError", GuardrailError::new),
            Map.entry("ApplicationError", ApplicationError::new),
            Map.entry("ExternalServiceError", ExternalServiceError::new),
            Map.entry("ExternalDataError", ExternalDataError::new),
            Map.entry("CryptError", CryptError::new)
    );

    static {
        Map<StatusCode, String> overrides = new EnumMap<>(StatusCode.class);
        putIfExists(overrides, "CONTROLLER_INVOKE_LLM_FAILED", "FrameworkError");
        putIfExists(overrides, "MODEL_PROVIDER_INVALID", "ModelError");
        putIfExists(overrides, "MODEL_CALL_FAILED", "ModelError");
        putIfExists(overrides, "MODEL_SERVICE_CONFIG_ERROR", "ModelError");
        putIfExists(overrides, "MODEL_CONFIG_ERROR", "ModelError");
        putIfExists(overrides, "MODEL_INVOKE_PARAM_ERROR", "ModelError");
        putIfExists(overrides, "MODEL_CLIENT_CONFIG_INVALID", "ModelError");
        putIfExists(overrides, "TOOL_EXECUTION_ERROR", "ToolError");
        putIfExists(overrides, "TOOL_NOT_FOUND_ERROR", "ValidationError");
        putIfExists(overrides, "AGENT_TEAM_EXECUTION_ERROR", "AgentError");
        putIfExists(overrides, "STORE_GRAPH_BACKEND_ALREADY_EXISTS", "ValidationError");
        putIfExists(overrides, "STORE_GRAPH_PROTOCOL_NOT_IMPLEMENTED", "ValidationError");
        putIfExists(overrides, "STORE_GRAPH_BACKEND_NOT_FOUND", "ValidationError");
        putIfExists(overrides, "AGENT_RL_PROXY_SERVER_START_FAILED", "FrameworkError");
        putIfExists(overrides, "AGENT_RL_PROCESSOR_NOT_FOUND", "ValidationError");
        putIfExists(overrides, "AGENT_RL_REWARD_NOT_FOUND", "ValidationError");
        putIfExists(overrides, "COMMON_ENCRYPTION_ERROR", "CryptError");
        putIfExists(overrides, "COMMON_DECRYPTION_ERROR", "CryptError");
        MANUAL_OVERRIDES = Collections.unmodifiableMap(overrides);
    }

    private StatusMapping() {
    }

    public static Function<StatusCode, BaseError> resolveExceptionFactory(StatusCode status) {
        String excName = MANUAL_OVERRIDES.get(status);
        if (excName == null) {
            excName = matchKeyword(status.name());
        }
        if (excName == null) {
            excName = matchRange(status.getCode());
        }
        if (excName == null) {
            excName = "ExecutionError";
        }
        return EXCEPTION_REGISTRY.getOrDefault(excName, EXCEPTION_REGISTRY.get("ExecutionError"));
    }

    public static BaseError resolveException(StatusCode status) {
        return resolveExceptionFactory(status).apply(status);
    }

    public static Map<StatusCode, Function<StatusCode, BaseError>> buildStatusExceptionMap() {
        EnumMap<StatusCode, Function<StatusCode, BaseError>> mapping = new EnumMap<>(StatusCode.class);
        for (StatusCode status : StatusCode.values()) {
            mapping.put(status, resolveExceptionFactory(status));
        }
        return Collections.unmodifiableMap(mapping);
    }

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

    private static void putIfExists(Map<StatusCode, String> map, String enumName, String exceptionName) {
        try {
            map.put(StatusCode.valueOf(enumName), exceptionName);
        } catch (IllegalArgumentException ignored) {
        }
    }
}
