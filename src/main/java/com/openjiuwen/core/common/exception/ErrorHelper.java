/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.exception;

import java.util.Map;

/**
 * Convenience factory methods for building and raising exceptions.
 * <p>
 * Java equivalent of Python's {@code build_error}, {@code raise_error}, etc.
 * Since Java cannot directly "return and throw" from a void method the way Python does,
 * the {@code raiseXxx} methods throw immediately; callers should not catch the return.
 */
public final class ErrorHelper {

    private ErrorHelper() {
    }

    /**
     * Build exception instance without throwing.
     * Useful for deferred throw or wrapping.
     *
     * @param status the status code
     * @return a BaseError instance
     */
    public static BaseError buildError(StatusCode status) {
        return StatusMapping.resolveException(status);
    }

    /**
     * Build exception with key-value parameter pairs for template substitution.
     * <p>Example: {@code buildError(StatusCode.TOOL_CARD_INVALID, "card", card, "reason", "card is None")}
     *
     * @param status the status code
     * @param kvPairs alternating key/value pairs (must be even number)
     * @return a BaseError with the parameters applied to the message template
     */
    public static BaseError buildError(StatusCode status, String... kvPairs) {
        if (kvPairs == null || kvPairs.length == 0) {
            return buildError(status);
        }
        Map<String, Object> params = new java.util.LinkedHashMap<>();
        for (int i = 0; i + 1 < kvPairs.length; i += 2) {
            params.put(kvPairs[i], kvPairs[i + 1]);
        }
        return createWithDetails(status, null, null, null, params);
    }

    /**
     * Build exception with custom message and details.
     *
     * @param status  the status code
     * @param msg     optional custom message
     * @param details optional additional details
     * @param cause   optional root cause
     * @param params  template parameters for message rendering
     * @return a BaseError instance with the specified details
     */
    public static BaseError buildError(StatusCode status, String msg, Object details,
                                       Throwable cause, Map<String, Object> params) {
        var factory = StatusMapping.resolveExceptionFactory(status);
        BaseError err = factory.apply(status);
        // For full control, use the isResolved class constructor explicitly.
        // Here we create via factory and augment.
        return createWithDetails(status, msg, details, cause, params);
    }

    /**
     * Unified error raising — throws immediately.
     *
     * @param status the status code
     * @throws BaseError always thrown with the given status
     */
    public static void raiseError(StatusCode status) {
        throw StatusMapping.resolveException(status);
    }

    /**
     * Unified error raising with details — throws immediately.
     *
     * @param status  the status code
     * @param msg     optional custom message
     * @param details optional additional details
     * @param cause   optional root cause
     * @param params  template parameters for message rendering
     * @throws BaseError always thrown with the specified details
     */
    public static void raiseError(StatusCode status, String msg, Object details,
                                  Throwable cause, Map<String, Object> params) {
        throw createWithDetails(status, msg, details, cause, params);
    }

    /**
     * Raise a FrameworkError.
     *
     * @param status the status code
     * @throws FrameworkError always thrown
     */
    public static void systemError(StatusCode status) {
        throw new FrameworkError(status);
    }

    /**
     * Raise a FrameworkError with cause.
     *
     * @param status the status code
     * @param cause  the root cause
     * @param params template parameters for message rendering
     * @throws FrameworkError always thrown
     */
    public static void systemError(StatusCode status, Throwable cause, Map<String, Object> params) {
        throw new FrameworkError(status, null, null, cause, params);
    }

    /**
     * Raise a ValidationError.
     *
     * @param status the status code
     * @throws ValidationError always thrown
     */
    public static void validateError(StatusCode status) {
        throw new ValidationError(status);
    }

    /**
     * Raise a ValidationError with cause.
     *
     * @param status the status code
     * @param cause  the root cause
     * @param params template parameters for message rendering
     * @throws ValidationError always thrown
     */
    public static void validateError(StatusCode status, Throwable cause, Map<String, Object> params) {
        throw new ValidationError(status, null, null, cause, params);
    }

    /**
     * Raise a Termination.
     *
     * @param status the status code
     * @throws Termination always thrown
     */
    public static void terminate(StatusCode status) {
        throw new Termination(status);
    }

    /**
     * Raise a Termination with params.
     *
     * @param status the status code
     * @param params template parameters for message rendering
     * @throws Termination always thrown
     */
    public static void terminate(StatusCode status, Map<String, Object> params) {
        throw new Termination(status, params);
    }

    // ==================== Internal ====================

    /**
     * Create exception with full details using reflection-free approach:
     * resolve via StatusMapping then construct the appropriate class.
     */
    private static BaseError createWithDetails(StatusCode status, String msg, Object details,
                                               Throwable cause, Map<String, Object> params) {
        String excName = resolveExceptionName(status);
        return switch (excName) {
            case "FrameworkError" -> new FrameworkError(status, msg, details, cause, params);
            case "ConfigurationError" -> new ConfigurationError(status, msg, details, cause, params);
            case "ValidationError" -> new ValidationError(status, msg, details, cause, params);
            case "ExecutionError" -> new ExecutionError(status, msg, details, cause, params);
            case "ApplicationError" -> new ApplicationError(status, msg, details, cause, params);
            case "ExternalServiceError" -> new ExternalServiceError(status, msg, details, cause, params);
            case "ExternalDataError" -> new ExternalDataError(status, msg, details, cause, params);
            case "Termination" -> new Termination(status, params);
            case "WorkflowError" -> new WorkflowError(status, msg, details, cause, params);
            case "AgentError" -> new AgentError(status, msg, details, cause, params);
            case "RunnerError" -> new RunnerError(status, msg, details, cause, params);
            case "GraphError" -> new GraphError(status, msg, details, cause, params);
            case "ContextError" -> new ContextError(status, msg, details, cause, params);
            case "ToolchainError" -> new ToolchainError(status, msg, details, cause, params);
            case "SessionError" -> new SessionError(status, msg, details, cause, params);
            case "SysOperationError" -> new SysOperationError(status, msg, details, cause, params);
            case "ToolError" -> new ToolError(status, msg, details, cause, null, params);
            case "GuardrailError" -> new GuardrailError(status, msg, details, cause, params);
            default -> new ExecutionError(status, msg, details, cause, params);
        };
    }

    /**
     * Resolve exception class name from status code (mirrors StatusMapping logic inline
     * for the switch-expression).
     */
    private static String resolveExceptionName(StatusCode status) {
        var factory = StatusMapping.resolveExceptionFactory(status);
        BaseError sample = factory.apply(status);
        return sample.getClass().getSimpleName();
    }
}
