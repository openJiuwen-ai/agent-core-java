package com.openjiuwen.core.common.security;

import com.openjiuwen.core.common.exception.JiuWenBaseException;
import com.openjiuwen.core.common.exception.StatusCode;
import jakarta.validation.ConstraintViolation;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * 异常工具类
 * 
 * <p>提供统一的异常抛出和验证错误格式化功能。
 * 
 * @author OpenJiuwen
 * @since 2026-01-29
 */
public class ExceptionUtils {

    private ExceptionUtils() {
        // Utility class
    }

    /**
     * 抛出异常
     * 
     * @param errorCode 状态码
     * @param errorMsg 错误消息
     * @param exception 原始异常（可选）
     * @throws JiuWenBaseException 总是抛出此异常
     */
    public static void raiseException(StatusCode errorCode, String errorMsg, Exception exception) {
        java.util.Map<String, Object> params = new java.util.HashMap<>();
        params.put("error_msg", errorMsg);
        String message = errorCode.formatMessage(params);
        
        JiuWenBaseException baseException = new JiuWenBaseException(errorCode.getCode(), message);
        if (exception != null) {
            baseException.initCause(exception);
        }
        throw baseException;
    }

    /**
     * 格式化验证错误
     * 
     * <p>将Jakarta Bean Validation的ConstraintViolation集合格式化为可读字符串。
     * 
     * @param violations 验证错误集合
     * @return 格式化后的错误字符串，每个错误一行
     */
    public static String formatValidationError(Set<ConstraintViolation<Object>> violations) {
        if (violations == null || violations.isEmpty()) {
            return "";
        }
        
        return violations.stream()
            .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
            .collect(Collectors.joining("\n"));
    }
}

