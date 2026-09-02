/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.exception;

import java.util.Set;

/**
 * Template for generating new StatusCode entries.
 * Used by tooling / code generation rather than at runtime.
 * @since 0.1.7
 */
public record StatusCodeTemplate(String name, String codeSuggestion, String messageTemplate, String exceptionSemantic) {

    // ==================== Allowed Values ====================
    /**
     * ALLOWED_SCOPES.
     * @since 0.1.7
     */
    public static final Set<String> ALLOWED_SCOPES =
        Set.of("WORKFLOW", "COMPONENT", "AGENT", "TOOL", "MODEL", "SESSION", "GRAPH", "CONTROLLER", "RUNNER", "PROMPT",
                "COMMON", "CONTEXT", "TOOLCHAIN", "MEMORY", "RETRIEVAL", "SYS_OPERATION");
    /**
     * ALLOWED_FAILURE_TYPES.
     * @since 0.1.7
     */
    public static final Set<String> ALLOWED_FAILURE_TYPES =
        Set.of("INVALID", "NOT_FOUND", "NOT_SUPPORTED", "CONFIG_ERROR", "PARAM_ERROR", "TYPE_ERROR", "INIT_FAILED",
                "CALL_FAILED", "EXECUTION_ERROR", "RUNTIME_ERROR", "PROCESS_ERROR", "TIMEOUT", "INTERRUPTED");
    /**
     * generate.
     * @param scope scope
     * @param subject subject
     * @param failureType failureType
     * @param detail detail
     * @return the result
     * @since 0.1.7
     */
    public static StatusCodeTemplate generate(String scope, String subject, String failureType, String detail) {
        if (!ALLOWED_SCOPES.contains(scope)) {
            throw new IllegalArgumentException("Invalid scope: " + scope);
        }
        if (!ALLOWED_FAILURE_TYPES.contains(failureType)) {
            throw new IllegalArgumentException("Invalid failure type: " + failureType);
        }

        String generatedName = generateName(scope, subject, detail, failureType);
        String codeRange = codeRangeByScope(scope);
        ErrorMessageTemplate msgTemplate = ErrorMessageTemplate.generate(scope, subject, failureType, true);
        String exceptionSemantic = exceptionSemanticFromFailure(failureType);

        return new StatusCodeTemplate(generatedName, codeRange, msgTemplate.template(), exceptionSemantic);
    }

    /**
     * generate.
     * @param scope scope
     * @param subject subject
     * @param failureType failureType
     * @return the result
     * @since 0.1.7
     */
    public static StatusCodeTemplate generate(String scope, String subject, String failureType) {
        return generate(scope, subject, failureType, null);
    }
    /**
     * generateName.
     * @param scope scope
     * @param subject subject
     * @param detail detail
     * @param failureType failureType
     * @return the result
     * @since 0.1.7
     */
    private static String generateName(String scope, String subject, String detail, String failureType) {
        StringBuilder sb = new StringBuilder(scope);
        if (detail != null && !detail.isEmpty()) {
            sb.append('_').append(detail);
        }
        sb.append('_').append(subject);
        sb.append('_').append(failureType);
        return sb.toString();
    }

    /**
     * exceptionSemanticFromFailure.
     * @param failureType failureType
     * @return the result
     * @since 0.1.7
     */
    private static String exceptionSemanticFromFailure(String failureType) {
        return switch (failureType) {
            case "INVALID", "NOT_FOUND", "NOT_SUPPORTED", "CONFIG_ERROR", "PARAM_ERROR" -> "ValidationError";
            case "INIT_FAILED", "CALL_FAILED" -> "FrameworkError";
            default -> "ExecutionError";
        };
    }

    static String codeRangeByScope(String scope) {
        return switch (scope) {
            case "WORKFLOW" -> "100000–100999";
            case "COMPONENT" -> "101000–119999";
            case "AGENT" -> "120000–129999";
            case "RUNNER" -> "130000–139999";
            case "GRAPH" -> "140000–149999";
            case "CONTEXT" -> "150000–154999";
            case "RETRIEVAL" -> "155000–157999";
            case "MEMORY" -> "158000–159999";
            case "TOOLCHAIN" -> "160000–179999";
            case "PROMPT" -> "180000–180999";
            case "MODEL" -> "181000–181999";
            case "TOOL" -> "182000–182999";
            case "COMMON" -> "188000–188999";
            case "SESSION" -> "190000–198999";
            case "SYS_OPERATION" -> "199000–199999";
            default -> "custom";
        };
    }
}
