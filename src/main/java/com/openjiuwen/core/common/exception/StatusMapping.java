// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.core.common.exception;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * StatusCode到异常类的映射
 *
 * <p>根据StatusCode映射到对应的异常类。映射规则包括：</p>
 * <ul>
 *     <li>手动覆盖规则</li>
 *     <li>关键词规则</li>
 *     <li>范围规则</li>
 *     <li>默认回退规则</li>
 * </ul>
 */
public final class StatusMapping {

    private static volatile Map<String, Class<? extends BaseError>> EXCEPTION_CLASS_REGISTRY;
    private static final Object REGISTRY_LOCK = new Object();

    /**
     * 私有构造函数，防止实例化
     */
    private StatusMapping() {
    }

    /**
     * 关键词规则映射
     */
    private static final List<KeywordRule> KEYWORD_RULES = List.of(
        new KeywordRule(
            List.of("INVALID", "VALIDATE", "NOT_SUPPORTED", "PARAM", "MISSING", "DUPLICATED"),
            "ValidationError"
        ),
        new KeywordRule(
            List.of("CONFIG", "SCHEMA", "FORMAT", "TEMPLATE"),
            "ValidationError"
        ),
        new KeywordRule(
            List.of("INIT", "CONNECT", "SERVICE", "QUEUE", "PROVIDER"),
            "FrameworkError"
        ),
        new KeywordRule(
            List.of("CALL", "INVOKE_LLM", "MODEL", "REMOTE"),
            "FrameworkError"
        ),
        new KeywordRule(
            List.of("TIMEOUT", "EXECUTE", "EXECUTION", "RUNTIME", "PROCESS", "STREAM", "RESPONSE"),
            "ExecutionError"
        )
    );

    /**
     * 范围规则映射
     */
    private static final List<RangeRule> RANGE_RULES = List.of(
        new RangeRule(100000, 119999, "WorkflowError"),
        new RangeRule(120000, 129999, "AgentError"),
        new RangeRule(130000, 139999, "RunnerError"),
        new RangeRule(140000, 149999, "GraphError"),
        new RangeRule(150000, 159999, "ContextError"),
        new RangeRule(160000, 179999, "ToolchainError"),
        new RangeRule(180000, 189999, "FrameworkError"),
        new RangeRule(190000, 198999, "SessionError"),
        new RangeRule(199000, 199999, "SysOperationError")
    );

    /**
     * 手动覆盖规则
     */
    private static final Map<String, String> MANUAL_OVERRIDES = Map.of(
        "CONTROLLER_INVOKE_LLM_FAILED", "FrameworkError",
        "TOOL_EXECUTION_ERROR", "ToolError",
        "TOOL_NOT_FOUND_ERROR", "ValidationError",
        "AGENT_GROUP_EXECUTION_ERROR", "AgentError"
    );

    /**
     * 获取异常类注册表（懒加载，避免循环导入）
     *
     * @return 异常类注册表
     */
    private static Map<String, Class<? extends BaseError>> getExceptionClassRegistry() {
        if (EXCEPTION_CLASS_REGISTRY == null) {
            synchronized (REGISTRY_LOCK) {
                if (EXCEPTION_CLASS_REGISTRY == null) {
                    Map<String, Class<? extends BaseError>> registry = new HashMap<>();
                    registry.put("BaseError", BaseError.class);
                    registry.put("FrameworkError", FrameworkError.class);
                    registry.put("ExecutionError", ExecutionError.class);
                    registry.put("ValidationError", ValidationError.class);
                    registry.put("Termination", Termination.class);
                    registry.put("ConfigurationError", ConfigurationError.class);
                    registry.put("ApplicationError", ApplicationError.class);
                    registry.put("ExternalServiceError", ExternalServiceError.class);
                    registry.put("ExternalDataError", ExternalDataError.class);
                    registry.put("RunnerTermination", RunnerTermination.class);
                    // 以下异常类将在相应模块转换时添加
                    registry.put("WorkflowError", BaseError.class);
                    registry.put("AgentError", BaseError.class);
                    registry.put("RunnerError", BaseError.class);
                    registry.put("GraphError", BaseError.class);
                    registry.put("SessionError", BaseError.class);
                    registry.put("SysOperationError", BaseError.class);
                    registry.put("ToolError", BaseError.class);
                    registry.put("ToolchainError", BaseError.class);
                    registry.put("ContextError", BaseError.class);

                    EXCEPTION_CLASS_REGISTRY = Map.copyOf(registry);
                }
            }
        }
        return new HashMap<>(EXCEPTION_CLASS_REGISTRY);
    }

    /**
     * 根据StatusCode解析对应的异常类
     *
     * @param status 状态码
     * @return 异常类
     */
    public static Class<? extends BaseError> resolveExceptionClass(StatusCode status) {
        Map<String, Class<? extends BaseError>> registry = getExceptionClassRegistry();

        String statusName = status.name();
        int code = status.code();

        // 1. 手动覆盖规则
        String excName = MANUAL_OVERRIDES.get(statusName);
        if (excName != null) {
            Class<? extends BaseError> clazz = registry.get(excName);
            if (clazz != null) {
                return clazz;
            }
        }

        // 2. 关键词规则
        excName = matchKeyword(statusName);
        if (excName != null) {
            Class<? extends BaseError> clazz = registry.get(excName);
            if (clazz != null) {
                return clazz;
            }
        }

        // 3. 范围回退规则
        excName = matchRange(code);
        if (excName != null) {
            Class<? extends BaseError> clazz = registry.get(excName);
            if (clazz != null) {
                return clazz;
            }
        }

        // 4. 绝对回退
        return registry.get("ExecutionError");
    }

    /**
     * 创建对应的异常实例
     *
     * @param status 状态码
     * @param params 模板参数
     * @return 异常实例
     */
    public static BaseError createException(StatusCode status, Map<String, Object> params) {
        Class<? extends BaseError> exceptionClass = resolveExceptionClass(status);
        try {
            return exceptionClass.getConstructor(StatusCode.class, Map.class).newInstance(status, params);
        } catch (NoSuchMethodException e) {
            try {
                return exceptionClass.getConstructor(StatusCode.class).newInstance(status);
            } catch (Exception ex) {
                throw new RuntimeException(
                    "Failed to create exception instance for status: " + status.name(), ex
                );
            }
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(
                "Failed to create exception instance for status: " + status.name(), e
            );
        }
    }

    /**
     * 创建对应的异常实例（无参数）
     *
     * @param status 状态码
     * @return 异常实例
     */
    public static BaseError createException(StatusCode status) {
        return createException(status, null);
    }

    /**
     * 构建完整的StatusCode到异常类的映射
     *
     * @return 完整的映射
     */
    public static Map<StatusCode, Class<? extends BaseError>> buildStatusExceptionMap() {
        Map<StatusCode, Class<? extends BaseError>> mapping = new EnumMap<>(StatusCode.class);
        for (StatusCode status : StatusCode.values()) {
            mapping.put(status, resolveExceptionClass(status));
        }
        return Map.copyOf(mapping);
    }

    /**
     * 匹配关键词规则
     *
     * @param name 状态码名称
     * @return 异常类名称，如果未匹配则返回null
     */
    private static String matchKeyword(String name) {
        for (KeywordRule rule : KEYWORD_RULES) {
            for (String keyword : rule.keywords()) {
                if (name.contains(keyword)) {
                    return rule.exceptionClassName();
                }
            }
        }
        return null;
    }

    /**
     * 匹配范围规则
     *
     * @param code 状态码值
     * @return 异常类名称，如果未匹配则返回null
     */
    private static String matchRange(int code) {
        for (RangeRule rule : RANGE_RULES) {
            if (code >= rule.start() && code <= rule.end()) {
                return rule.exceptionClassName();
            }
        }
        return null;
    }

    /**
     * 关键词规则
     *
     * @param keywords 关键词列表
     * @param exceptionClassName 异常类名称
     */
    private record KeywordRule(List<String> keywords, String exceptionClassName) {}

    /**
     * 范围规则
     *
     * @param start 起始码
     * @param end 结束码
     * @param exceptionClassName 异常类名称
     */
    private record RangeRule(int start, int end, String exceptionClassName) {}
}