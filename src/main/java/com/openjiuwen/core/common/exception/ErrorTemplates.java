// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.core.common.exception;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 错误消息模板工具类
 *
 * <p>用于生成状态码和错误消息模板。</p>
 */
public final class ErrorTemplates {

    private ErrorTemplates() {
    }

    /**
     * 允许的作用域集合
     */
    public static final Set<String> ALLOWED_SCOPES = new HashSet<>(Arrays.asList(
        "WORKFLOW", "COMPONENT", "AGENT", "TOOL", "MODEL",
        "SESSION", "GRAPH", "CONTROLLER", "RUNNER", "PROMPT",
        "COMMON", "CONTEXT", "TOOLCHAIN", "MEMORY", "RETRIEVAL", "SYS_OPERATION"
    ));

    /**
     * 允许的失败类型集合
     */
    public static final Set<String> ALLOWED_FAILURE_TYPES = new HashSet<>(Arrays.asList(
        // Validation
        "INVALID", "NOT_FOUND", "NOT_SUPPORTED", "CONFIG_ERROR", "PARAM_ERROR", "TYPE_ERROR",
        // Framework
        "INIT_FAILED", "CALL_FAILED",
        // Execution
        "EXECUTION_ERROR", "RUNTIME_ERROR", "PROCESS_ERROR", "TIMEOUT", "INTERRUPTED"
    ));

    /**
     * 状态码模板记录
     */
    public record StatusCodeTemplate(
        String name,
        String codeSuggestion,
        String messageTemplate,
        String exceptionSemantic
    ) {}

    /**
     * 错误消息模板记录
     */
    public record ErrorMessageTemplate(
        String template,
        Set<String> params
    ) {}

    /**
     * 状态码规范记录
     */
    public record StatusCodeSpec(
        String name,
        int code,
        String message
    ) {}

    /**
     * 从失败类型获取异常语义
     *
     * @param failureType 失败类型
     * @return 异常语义类型
     */
    public static String exceptionSemanticFromFailure(String failureType) {
        if (Set.of("INVALID", "NOT_FOUND", "NOT_SUPPORTED", "CONFIG_ERROR", "PARAM_ERROR").contains(failureType)) {
            return "ValidationError";
        }
        if (Set.of("INIT_FAILED", "CALL_FAILED").contains(failureType)) {
            return "FrameworkError";
        }
        return "ExecutionError";
    }

    /**
     * 根据作用域获取代码范围
     *
     * @param scope 作用域
     * @return 代码范围字符串
     */
    public static String codeRangeByScope(String scope) {
        return switch (scope) {
            case "WORKFLOW" -> "100000–100999";
            case "COMPONENT" -> "101000–119999";
            case "AGENT" -> "120000–129999";
            case "RUNNER" -> "130000–139999";
            case "GRAPH" -> "140000–149999";
            case "CONTEXT" -> "150000–154999";
            case "RETRIEVAL" -> "155000-157999";
            case "MEMORY" -> "158000-159999";
            case "TOOLCHAIN" -> "160000–179999";
            case "PROMPT" -> "180000-180999";
            case "MODEL" -> "181000-181999";
            case "TOOL" -> "182000-182999";
            case "COMMON" -> "188000-188999";
            case "SESSION" -> "190000–198999";
            case "SYS_OPERATION" -> "199000–199999";
            default -> "custom";
        };
    }

    /**
     * 生成状态码模板
     *
     * @param scope 作用域
     * @param subject 主体
     * @param failureType 失败类型
     * @param detail 详情（可选）
     * @return 状态码模板
     */
    public static StatusCodeTemplate generateStatusCode(String scope, String subject,
                                                       String failureType, String detail) {
        // 验证参数
        validate(scope, failureType);

        // 生成名称
        String name = genName(scope, subject, detail, failureType);

        // 生成消息模板
        ErrorMessageTemplate messageTemplate = generateErrorMessageTemplate(scope, subject, failureType);

        return new StatusCodeTemplate(
            name,
            codeRangeByScope(scope),
            messageTemplate.template(),
            exceptionSemanticFromFailure(failureType)
        );
    }

    /**
     * 生成状态码规范
     *
     * @param template 状态码模板
     * @param code 状态码值
     * @return 状态码规范
     */
    public static StatusCodeSpec generateStatusCodeSpec(StatusCodeTemplate template, int code) {
        return new StatusCodeSpec(template.name(), code, template.messageTemplate());
    }

    /**
     * 渲染枚举成员
     *
     * @param spec 状态码规范
     * @return 渲染后的字符串
     */
    public static String renderEnumMember(StatusCodeSpec spec) {
        return String.format("    %s = (%d, \"%s\")", spec.name(), spec.code(), spec.message());
    }

    /**
     * 生成错误消息模板
     *
     * @param scope 作用域
     * @param subject 主体
     * @param failureType 失败类型
     * @param withReason 是否包含原因
     * @return 错误消息模板
     */
    public static ErrorMessageTemplate generateErrorMessageTemplate(String scope, String subject,
                                                                  String failureType, boolean withReason) {
        String scopeLower = scope.toLowerCase();
        String subjectLower = subject.toLowerCase();

        Set<String> params = new HashSet<>();
        String msg;

        // 基础句子
        switch (failureType) {
            case "INVALID" -> msg = String.format("%s %s is invalid", scopeLower, subjectLower);
            case "PARAM_ERROR" -> msg = String.format("%s %s parameter error", scopeLower, subjectLower);
            case "NOT_FOUND" -> msg = String.format("%s %s not found", scopeLower, subjectLower);
            case "NOT_SUPPORTED", "NOT_SUPPORT" -> msg = String.format("%s %s is not supported", scopeLower, subjectLower);
            case "CONFIG_ERROR" -> msg = String.format("%s %s config error", scopeLower, subjectLower);
            case "INIT_FAILED" -> msg = String.format("%s %s initialization failed", scopeLower, subjectLower);
            case "CALL_FAILED" -> msg = String.format("%s %s call failed", scopeLower, subjectLower);
            case "EXECUTION_ERROR" -> msg = String.format("%s %s execution error", scopeLower, subjectLower);
            case "RUNTIME_ERROR" -> msg = String.format("%s %s runtime error", scopeLower, subjectLower);
            case "PROCESS_ERROR" -> msg = String.format("%s %s process error", scopeLower, subjectLower);
            case "TIMEOUT" -> {
                params.add("timeout");
                msg = String.format("%s %s timeout ({timeout}s)", scopeLower, subjectLower);
            }
            case "INTERRUPTED" -> msg = String.format("%s %s interrupted", scopeLower, subjectLower);
            default -> throw new IllegalArgumentException("Unsupported failure type: " + failureType);
        }

        // 可选原因
        if (withReason) {
            params.add("error_msg");
            msg += ", reason: {error_msg}";
        }

        return new ErrorMessageTemplate(msg, params);
    }

    /**
     * 生成错误消息模板（默认包含原因）
     *
     * @param scope 作用域
     * @param subject 主体
     * @param failureType 失败类型
     * @return 错误消息模板
     */
    public static ErrorMessageTemplate generateErrorMessageTemplate(String scope, String subject,
                                                                  String failureType) {
        return generateErrorMessageTemplate(scope, subject, failureType, true);
    }

    /**
     * 生成名称
     */
    private static String genName(String scope, String subject, String detail, String failureType) {
        StringBuilder sb = new StringBuilder();
        sb.append(scope);
        sb.append("_");
        if (detail != null && !detail.isEmpty()) {
            sb.append(detail);
            sb.append("_");
        }
        sb.append(subject);
        sb.append("_");
        sb.append(failureType);
        return sb.toString();
    }

    /**
     * 验证参数
     *
     * @param scope 作用域
     * @param failureType 失败类型
     * @throws IllegalArgumentException 如果参数无效
     */
    private static void validate(String scope, String failureType) {
        if (!ALLOWED_SCOPES.contains(scope)) {
            throw new IllegalArgumentException("Invalid scope: " + scope);
        }
        if (!ALLOWED_FAILURE_TYPES.contains(failureType)) {
            throw new IllegalArgumentException("Invalid failure type: " + failureType);
        }
    }
}