/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.exception;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mirrors Python's module-level error helpers in
 * {@code openjiuwen/core/common/exception/errors.py}.
 */
public final class ErrorHelper {

    private ErrorHelper() {
    }

    public static BaseError buildError(StatusCode status) {
        return StatusMapping.resolveException(status);
    }

    public static BaseError buildError(StatusCode status, String... kvPairs) {
        if (kvPairs == null || kvPairs.length == 0) {
            return buildError(status);
        }
        Map<String, Object> params = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kvPairs.length; i += 2) {
            params.put(kvPairs[i], kvPairs[i + 1]);
        }
        return createWithDetails(status, null, null, null, params);
    }

    public static BaseError buildError(StatusCode status, String msg, Object details, Throwable cause, Map<String, Object> params) {
        return createWithDetails(status, msg, details, cause, params);
    }

    public static void raiseError(StatusCode status) {
        throw StatusMapping.resolveException(status);
    }

    public static void raiseError(StatusCode status, String msg, Object details, Throwable cause, Map<String, Object> params) {
        throw createWithDetails(status, msg, details, cause, params);
    }

    public static void systemError(StatusCode status) {
        throw new FrameworkError(status);
    }

    public static void systemError(StatusCode status, Throwable cause, Map<String, Object> params) {
        throw new FrameworkError(status, null, null, cause, params);
    }

    public static void validateError(StatusCode status) {
        throw new ValidationError(status);
    }

    public static void validateError(StatusCode status, Throwable cause, Map<String, Object> params) {
        throw new ValidationError(status, null, null, cause, params);
    }

    public static void terminate(StatusCode status) {
        throw new Termination(status);
    }

    public static void terminate(StatusCode status, Map<String, Object> params) {
        throw new Termination(status, params);
    }

    private static BaseError createWithDetails(StatusCode status, String msg, Object details, Throwable cause, Map<String, Object> params) {
        String excName = StatusMapping.resolveException(status).getClass().getSimpleName();
        return switch (excName) {
            case "FrameworkError" -> new FrameworkError(status, msg, details, cause, params);
            case "ConfigurationError" -> new ConfigurationError(status, msg, details, cause, params);
            case "ValidationError" -> new ValidationError(status, msg, details, cause, params);
            case "ExecutionError" -> new ExecutionError(status, msg, details, cause, params);
            case "ApplicationError" -> new ApplicationError(status, msg, details, cause, params);
            case "ExternalServiceError" -> new ExternalServiceError(status, msg, details, cause, params);
            case "ExternalDataError" -> new ExternalDataError(status, msg, details, cause, params);
            case "Termination" -> new Termination(status, msg, details, cause, params);
            case "WorkflowError" -> new WorkflowError(status, msg, details, cause, params);
            case "ComponentError" -> new ComponentError(status, msg, details, cause, params);
            case "AgentError" -> new AgentError(status, msg, details, cause, params);
            case "RunnerError" -> new RunnerError(status, msg, details, cause, params);
            case "GraphError" -> new GraphError(status, msg, details, cause, params);
            case "ModelError" -> new ModelError(status, msg, details, cause, params);
            case "ToolError" -> new ToolError(status, msg, details, cause, null, params);
            case "ContextError" -> new ContextError(status, msg, details, cause, params);
            case "ToolchainError" -> new ToolchainError(status, msg, details, cause, params);
            case "SessionError" -> new SessionError(status, msg, details, cause, params);
            case "SysOperationError" -> new SysOperationError(status, msg, details, cause, params);
            case "GuardrailError" -> new GuardrailError(status, msg, details, cause, params);
            case "CryptError" -> new CryptError(status, msg, details, cause, params);
            default -> new ExecutionError(status, msg, details, cause, params);
        };
    }
}
