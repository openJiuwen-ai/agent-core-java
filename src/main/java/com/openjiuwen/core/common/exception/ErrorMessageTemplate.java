/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.exception;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Mirrors Python's {@code ErrorMessageTemplate} in
 * {@code openjiuwen/core/common/exception/code_template.py}.
 *
 * @param template rendered message template
 * @param params template placeholder names
 */
public record ErrorMessageTemplate(String template, Set<String> params) {

    public static ErrorMessageTemplate generateErrorMessageTemplate(
            String scope,
            String subject,
            String failureType,
            boolean withReason) {
        String scopeLower = scope.toLowerCase();
        String subjectLower = subject.toLowerCase();
        LinkedHashSet<String> params = new LinkedHashSet<>();
        String message = switch (failureType) {
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
            message += ", reason: {error_msg}";
        }
        return new ErrorMessageTemplate(message, Set.copyOf(params));
    }

    public static ErrorMessageTemplate generateErrorMessageTemplate(String scope, String subject, String failureType) {
        return generateErrorMessageTemplate(scope, subject, failureType, true);
    }
}
