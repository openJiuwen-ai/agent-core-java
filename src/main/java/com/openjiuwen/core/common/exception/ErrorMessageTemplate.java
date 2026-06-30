/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.exception;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Generates human-readable error message templates from structured inputs.
 *
 * @param template the message template with {placeholders}
 * @param params   the placeholder names used in the template
 */
public record ErrorMessageTemplate(String template, Set<String> params) {

    /**
     * Generate an error message template from structured inputs.
     *
     * @param scope       the scope (lowercased in output)
     * @param subject     the subject (lowercased in output)
     * @param failureType the failure type
     * @param withReason  whether to append reason placeholder
     * @return generated template
     */
    public static ErrorMessageTemplate generate(String scope, String subject, String failureType, boolean withReason) {
        String scopeLower = scope.toLowerCase(Locale.ROOT);
        String subjectLower = subject.toLowerCase(Locale.ROOT);
        Set<String> params = new HashSet<>();
        String msg;

        msg = switch (failureType) {
            case "INVALID" -> scopeLower + " " + subjectLower + " is invalid";
            case "PARAM_ERROR" -> scopeLower + " " + subjectLower + " parameter error";
            case "NOT_FOUND" -> scopeLower + " " + subjectLower + " not found";
            case "NOT_SUPPORT", "NOT_SUPPORTED" -> scopeLower + " " + subjectLower + " is not supported";
            case "CONFIG_ERROR" -> scopeLower + " " + subjectLower + " config error";
            case "INIT_FAILED" -> scopeLower + " " + subjectLower + " initialization failed";
            case "CALL_FAILED" -> scopeLower + " " + subjectLower + " call failed";
            case "EXECUTION_ERROR" -> scopeLower + " " + subjectLower + " execution error";
            case "RUNTIME_ERROR" -> scopeLower + " " + subjectLower + " runtime error";
            case "PROCESS_ERROR" -> scopeLower + " " + subjectLower + " process error";
            case "TIMEOUT" -> {
                params.add("timeout");
                yield scopeLower + " " + subjectLower + " timeout ({timeout}s)";
            }
            case "INTERRUPTED" -> scopeLower + " " + subjectLower + " interrupted";
            default -> throw new IllegalArgumentException("Unsupported failure type: " + failureType);
        };

        if (withReason) {
            params.add("error_msg");
            msg += ", reason: {error_msg}";
        }

        return new ErrorMessageTemplate(msg, Set.copyOf(params));
    }

    /**
     * Overload with withReason defaulting to true.
     */
    public static ErrorMessageTemplate generate(String scope, String subject, String failureType) {
        return generate(scope, subject, failureType, true);
    }
}
