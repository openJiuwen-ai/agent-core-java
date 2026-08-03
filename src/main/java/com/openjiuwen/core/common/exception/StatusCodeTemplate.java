/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.exception;

import java.util.Set;

/**
 * Mirrors Python's {@code StatusCodeTemplate} in
 * {@code openjiuwen/core/common/exception/code_template.py}.
 *
 * @param name suggested enum member name
 * @param codeSuggestion human-readable code range hint
 * @param messageTemplate message template with placeholders
 * @param exceptionSemantic suggested exception semantic
 */
public record StatusCodeTemplate(
        String name,
        String codeSuggestion,
        String messageTemplate,
        String exceptionSemantic) {

    public static final Set<String> ALLOWED_SCOPES = Set.of(
            "WORKFLOW",
            "COMPONENT",
            "AGENT",
            "TOOL",
            "MODEL",
            "SESSION",
            "GRAPH",
            "CONTROLLER",
            "RUNNER",
            "PROMPT",
            "COMMON",
            "CONTEXT",
            "TOOLCHAIN",
            "MEMORY",
            "RETRIEVAL",
            "SYS_OPERATION");

    public static final Set<String> ALLOWED_FAILURE_TYPES = Set.of(
            "INVALID",
            "NOT_FOUND",
            "NOT_SUPPORTED",
            "CONFIG_ERROR",
            "PARAM_ERROR",
            "TYPE_ERROR",
            "INIT_FAILED",
            "CALL_FAILED",
            "EXECUTION_ERROR",
            "RUNTIME_ERROR",
            "PROCESS_ERROR",
            "TIMEOUT",
            "INTERRUPTED");

    public static StatusCodeTemplate generateStatusCode(
            String scope,
            String subject,
            String failureType,
            String detail) {
        validate(scope, failureType);
        return new StatusCodeTemplate(
                generateName(scope, subject, detail, failureType),
                codeRangeByScope(scope),
                ErrorMessageTemplate.generateErrorMessageTemplate(scope, subject, failureType).template(),
                exceptionSemanticFromFailure(failureType));
    }

    public static StatusCodeTemplate generateStatusCode(String scope, String subject, String failureType) {
        return generateStatusCode(scope, subject, failureType, null);
    }

    public static StatusCodeSpec generateStatusCodeSpec(StatusCodeTemplate template, int code) {
        return StatusCodeSpec.generateStatusCodeSpec(template, code);
    }

    private static void validate(String scope, String failureType) {
        if (!ALLOWED_SCOPES.contains(scope)) {
            throw new IllegalArgumentException("Invalid scope: " + scope);
        }
        if (!ALLOWED_FAILURE_TYPES.contains(failureType)) {
            throw new IllegalArgumentException("Invalid failure type: " + failureType);
        }
    }

    private static String generateName(String scope, String subject, String detail, String failureType) {
        StringBuilder builder = new StringBuilder(scope);
        if (detail != null && !detail.isBlank()) {
            builder.append('_').append(detail);
        }
        builder.append('_').append(subject).append('_').append(failureType);
        return builder.toString();
    }

    private static String exceptionSemanticFromFailure(String failureType) {
        return switch (failureType) {
            case "INVALID", "NOT_FOUND", "NOT_SUPPORTED", "CONFIG_ERROR", "PARAM_ERROR" -> "ValidationError";
            case "INIT_FAILED", "CALL_FAILED" -> "FrameworkError";
            default -> "ExecutionError";
        };
    }

    static String codeRangeByScope(String scope) {
        return switch (scope) {
            case "WORKFLOW" -> "100000-100999";
            case "COMPONENT" -> "101000-119999";
            case "AGENT" -> "120000-129999";
            case "RUNNER" -> "130000-139999";
            case "GRAPH" -> "140000-149999";
            case "CONTEXT" -> "150000-154999";
            case "RETRIEVAL" -> "155000-157999";
            case "MEMORY" -> "158000-159999";
            case "TOOLCHAIN" -> "160000-179999";
            case "PROMPT" -> "180000-180999";
            case "MODEL" -> "181000-181999";
            case "TOOL" -> "182000-182999";
            case "COMMON" -> "188000-188999";
            case "SESSION" -> "190000-198999";
            case "SYS_OPERATION" -> "199000-199999";
            default -> "custom";
        };
    }
}
