/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.common.exception;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * 状态码模板生成工具类
 * 
 * <p>提供状态码名称、消息模板和异常语义的生成功能，用于统一状态码定义规范。
 * 
 * <p>对应 Python: code_template.py
 */
public final class CodeTemplate {
    
    /**
     * 允许的作用域集合
     */
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
        "SYS_OPERATION"
    );
    
    /**
     * 允许的失败类型集合
     */
    public static final Set<String> ALLOWED_FAILURE_TYPES = Set.of(
        // Validation
        "INVALID",
        "NOT_FOUND",
        "NOT_SUPPORTED",
        "CONFIG_ERROR",
        "PARAM_ERROR",
        "TYPE_ERROR",
        // Framework
        "INIT_FAILED",
        "CALL_FAILED",
        // Execution
        "EXECUTION_ERROR",
        "RUNTIME_ERROR",
        "PROCESS_ERROR",
        "TIMEOUT",
        "INTERRUPTED"
    );
    
    /**
     * 代码范围映射：作用域 → 代码范围字符串
     */
    private static final Map<String, String> CODE_RANGE_BY_SCOPE = Map.ofEntries(
        Map.entry("WORKFLOW", "100000–100999"),
        Map.entry("COMPONENT", "101000–119999"),
        Map.entry("AGENT", "120000–129999"),
        Map.entry("RUNNER", "130000–139999"),
        Map.entry("GRAPH", "140000–149999"),
        Map.entry("CONTEXT", "150000–154999"),
        Map.entry("RETRIEVAL", "155000-157999"),
        Map.entry("MEMORY", "158000-159999"),
        Map.entry("TOOLCHAIN", "160000–179999"),
        Map.entry("PROMPT", "180000-180999"),
        Map.entry("MODEL", "181000–181999"),
        Map.entry("TOOL", "182000-182999"),
        Map.entry("COMMON", "188000-188999"),
        Map.entry("SESSION", "190000–198999"),
        Map.entry("SYS_OPERATION", "199000–199999")
    );
    
    private CodeTemplate() {
        // 工具类，禁止实例化
    }
    
    /**
     * 状态码模板
     * 
     * @param name 状态码名称
     * @param codeSuggestion 建议的代码范围
     * @param messageTemplate 消息模板
     * @param exceptionSemantic 异常语义（对应的异常类类型）
     */
    public record StatusCodeTemplate(
        String name,
        String codeSuggestion,
        String messageTemplate,
        String exceptionSemantic
    ) {}
    
    /**
     * 错误消息模板
     * 
     * @param template 消息模板字符串
     * @param params 模板中使用的参数名集合
     */
    public record ErrorMessageTemplate(
        String template,
        Set<String> params
    ) {}
    
    /**
     * 状态码规格
     * 
     * @param name 状态码名称
     * @param code 状态码数值
     * @param message 消息模板
     */
    public record StatusCodeSpec(
        String name,
        int code,
        String message
    ) {}
    
    /**
     * 根据失败类型推断异常语义
     * 
     * @param failureType 失败类型
     * @return 对应的异常类名称
     */
    public static String exceptionSemanticFromFailure(String failureType) {
        return switch (failureType) {
            case "INVALID", "NOT_FOUND", "NOT_SUPPORTED", "CONFIG_ERROR", "PARAM_ERROR" -> "ValidationError";
            case "INIT_FAILED", "CALL_FAILED" -> "FrameworkError";
            default -> "ExecutionError";
        };
    }
    
    /**
     * 根据作用域获取代码范围
     * 
     * @param scope 作用域
     * @return 代码范围字符串，若未找到则返回"custom"
     */
    public static String codeRangeByScope(String scope) {
        return CODE_RANGE_BY_SCOPE.getOrDefault(scope, "custom");
    }
    
    /**
     * 生成状态码模板
     * 
     * @param scope 作用域
     * @param subject 主题
     * @param failureType 失败类型
     * @param detail 详细信息（可选）
     * @return 状态码模板
     * @throws IllegalArgumentException 如果scope或failureType无效
     */
    public static StatusCodeTemplate generateStatusCode(
            String scope,
            String subject,
            String failureType,
            String detail) {
        
        // 验证参数
        validate(scope, failureType);
        
        // 生成名称
        String name = generateName(scope, subject, detail, failureType);
        
        // 生成消息模板
        ErrorMessageTemplate messageTemplate = generateErrorMessageTemplate(
            scope, subject, failureType, true);
        
        return new StatusCodeTemplate(
            name,
            codeRangeByScope(scope),
            messageTemplate.template(),
            exceptionSemanticFromFailure(failureType)
        );
    }
    
    /**
     * 生成状态码模板（无detail）
     * 
     * @param scope 作用域
     * @param subject 主题
     * @param failureType 失败类型
     * @return 状态码模板
     */
    public static StatusCodeTemplate generateStatusCode(
            String scope,
            String subject,
            String failureType) {
        return generateStatusCode(scope, subject, failureType, null);
    }
    
    /**
     * 生成状态码名称
     */
    private static String generateName(String scope, String subject, String detail, String failureType) {
        StringBuilder sb = new StringBuilder(scope);
        
        if (detail != null && !detail.isEmpty()) {
            sb.append("_").append(detail);
        }
        
        sb.append("_").append(subject);
        sb.append("_").append(failureType);
        
        return sb.toString();
    }
    
    /**
     * 验证参数有效性
     */
    private static void validate(String scope, String failureType) {
        if (!ALLOWED_SCOPES.contains(scope)) {
            throw new IllegalArgumentException("Invalid scope: " + scope);
        }
        
        if (!ALLOWED_FAILURE_TYPES.contains(failureType)) {
            throw new IllegalArgumentException("Invalid failure type: " + failureType);
        }
    }
    
    /**
     * 生成错误消息模板
     * 
     * @param scope 作用域
     * @param subject 主题
     * @param failureType 失败类型
     * @param withReason 是否包含reason参数
     * @return 错误消息模板
     * @throws IllegalArgumentException 如果failureType不支持
     */
    public static ErrorMessageTemplate generateErrorMessageTemplate(
            String scope,
            String subject,
            String failureType,
            boolean withReason) {
        
        String lowerScope = scope.toLowerCase();
        String lowerSubject = subject.toLowerCase();
        
        Set<String> params = new java.util.HashSet<>();
        String msg;
        
        switch (failureType) {
            case "INVALID" -> msg = lowerScope + " " + lowerSubject + " is invalid";
            case "PARAM_ERROR" -> msg = lowerScope + " " + lowerSubject + " parameter error";
            case "NOT_FOUND" -> msg = lowerScope + " " + lowerSubject + " not found";
            case "NOT_SUPPORT", "NOT_SUPPORTED" -> msg = lowerScope + " " + lowerSubject + " is not supported";
            case "CONFIG_ERROR" -> msg = lowerScope + " " + lowerSubject + " config error";
            case "INIT_FAILED" -> msg = lowerScope + " " + lowerSubject + " initialization failed";
            case "CALL_FAILED" -> msg = lowerScope + " " + lowerSubject + " call failed";
            case "EXECUTION_ERROR" -> msg = lowerScope + " " + lowerSubject + " execution error";
            case "RUNTIME_ERROR" -> msg = lowerScope + " " + lowerSubject + " runtime error";
            case "PROCESS_ERROR" -> msg = lowerScope + " " + lowerSubject + " process error";
            case "TIMEOUT" -> {
                params.add("timeout");
                msg = lowerScope + " " + lowerSubject + " timeout ({timeout}s)";
            }
            case "INTERRUPTED" -> msg = lowerScope + " " + lowerSubject + " interrupted";
            default -> throw new IllegalArgumentException("Unsupported failure type: " + failureType);
        }
        
        // 添加可选的reason
        if (withReason) {
            params.add("error_msg");
            msg += ", reason: {error_msg}";
        }
        
        return new ErrorMessageTemplate(msg, Collections.unmodifiableSet(params));
    }
    
    /**
     * 生成错误消息模板（默认包含reason）
     */
    public static ErrorMessageTemplate generateErrorMessageTemplate(
            String scope,
            String subject,
            String failureType) {
        return generateErrorMessageTemplate(scope, subject, failureType, true);
    }
    
    /**
     * 从模板生成状态码规格
     * 
     * @param template 状态码模板
     * @param code 状态码数值
     * @return 状态码规格
     */
    public static StatusCodeSpec generateStatusCodeSpec(StatusCodeTemplate template, int code) {
        return new StatusCodeSpec(
            template.name(),
            code,
            template.messageTemplate()
        );
    }
    
    /**
     * 渲染枚举成员代码
     * 
     * @param spec 状态码规格
     * @return Java枚举成员定义字符串
     */
    public static String renderEnumMember(StatusCodeSpec spec) {
        return String.format("    %s(%d, \"%s\")", spec.name(), spec.code(), spec.message());
    }
}

