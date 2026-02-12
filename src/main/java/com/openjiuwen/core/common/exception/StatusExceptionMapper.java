// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.common.exception;

import java.util.*;
import java.util.function.Function;

/**
 * Maps StatusCode to appropriate exception classes
 * 
 * <p>This class provides automatic exception type resolution based on:
 * <ul>
 *   <li>Manual overrides</li>
 *   <li>Keyword rules (e.g., INVALID -> ValidationError)</li>
 *   <li>Range rules (e.g., 100000-119999 -> WorkflowError)</li>
 * </ul>
 * 
 * @since 0.1.4
 */
public class StatusExceptionMapper {
    
    private static final Map<StatusCode, Class<? extends BaseError>> STATUS_TO_EXCEPTION_MAP = new HashMap<>();
    
    // Keyword rules: if status name contains these keywords, map to specific exception
    private static final List<KeywordRule> KEYWORD_RULES = Arrays.asList(
        new KeywordRule(Arrays.asList("INVALID", "VALIDATE", "NOT_SUPPORTED", "PARAM", "MISSING", "DUPLICATED"), ValidationError.class),
        new KeywordRule(Arrays.asList("CONFIG", "SCHEMA", "FORMAT", "TEMPLATE"), ValidationError.class),
        new KeywordRule(Arrays.asList("INIT", "CONNECT", "SERVICE", "QUEUE", "PROVIDER"), FrameworkError.class),
        new KeywordRule(Arrays.asList("CALL", "INVOKE_LLM", "MODEL", "REMOTE"), FrameworkError.class),
        new KeywordRule(Arrays.asList("TIMEOUT", "EXECUTE", "EXECUTION", "RUNTIME", "PROCESS", "STREAM", "RESPONSE"), ExecutionError.class)
    );
    
    // Range rules: if code is in range, map to specific exception
    private static final List<RangeRule> RANGE_RULES = Arrays.asList(
        new RangeRule(100000, 119999, WorkflowError.class),
        new RangeRule(120000, 129999, AgentError.class),
        new RangeRule(130000, 139999, RunnerError.class),
        new RangeRule(140000, 149999, GraphError.class),
        new RangeRule(150000, 159999, ContextError.class),
        new RangeRule(160000, 179999, ToolchainError.class),
        new RangeRule(180000, 189999, FrameworkError.class),
        new RangeRule(190000, 198999, SessionError.class),
        new RangeRule(199000, 199999, SysOperationError.class)
    );
    
    // Manual overrides for specific status codes
    private static final Map<StatusCode, Class<? extends BaseError>> MANUAL_OVERRIDES = new HashMap<>();
    
    static {
        // Initialize manual overrides (if any status codes need special handling)
        // MANUAL_OVERRIDES.put(StatusCode.SOME_SPECIAL_CODE, SomeSpecificError.class);
        
        // Build the complete mapping
        buildStatusExceptionMap();
    }
    
    /**
     * Build the complete StatusCode to Exception class mapping
     */
    private static void buildStatusExceptionMap() {
        for (StatusCode status : StatusCode.values()) {
            STATUS_TO_EXCEPTION_MAP.put(status, resolveExceptionClass(status));
        }
    }
    
    /**
     * Resolve the exception class for a given StatusCode
     * 
     * @param status the status code
     * @return the exception class
     */
    public static Class<? extends BaseError> resolveExceptionClass(StatusCode status) {
        // 1. Check manual overrides first
        if (MANUAL_OVERRIDES.containsKey(status)) {
            return MANUAL_OVERRIDES.get(status);
        }
        
        String name = status.name();
        int code = status.getCode();
        
        // 2. Apply keyword rules
        for (KeywordRule rule : KEYWORD_RULES) {
            if (rule.matches(name)) {
                return rule.exceptionClass;
            }
        }
        
        // 3. Apply range rules
        for (RangeRule rule : RANGE_RULES) {
            if (rule.matches(code)) {
                return rule.exceptionClass;
            }
        }
        
        // 4. Default fallback
        return ExecutionError.class;
    }
    
    /**
     * Get the exception class for a status code
     * 
     * @param status the status code
     * @return the exception class
     */
    public static Class<? extends BaseError> getExceptionClass(StatusCode status) {
        return STATUS_TO_EXCEPTION_MAP.getOrDefault(status, ExecutionError.class);
    }
    
    /**
     * Create an exception instance for a status code
     * 
     * @param status the status code
     * @param msg custom message
     * @param details additional details
     * @param cause the cause exception
     * @param params template parameters
     * @return the exception instance
     */
    public static BaseError createException(
            StatusCode status,
            String msg,
            Object details,
            Throwable cause,
            Map<String, Object> params) {
        
        Class<? extends BaseError> exceptionClass = getExceptionClass(status);
        
        try {
            // Use reflection to create instance
            return exceptionClass
                    .getConstructor(StatusCode.class, String.class, Object.class, Throwable.class, Map.class)
                    .newInstance(status, msg, details, cause, params);
        } catch (Exception e) {
            // Fallback to BaseError if reflection fails
            return new BaseError(status, msg, details, cause, params);
        }
    }
    
    // Helper classes for rules
    
    private static class KeywordRule {
        private final List<String> keywords;
        private final Class<? extends BaseError> exceptionClass;
        
        KeywordRule(List<String> keywords, Class<? extends BaseError> exceptionClass) {
            this.keywords = keywords;
            this.exceptionClass = exceptionClass;
        }
        
        boolean matches(String statusName) {
            for (String keyword : keywords) {
                if (statusName.contains(keyword)) {
                    return true;
                }
            }
            return false;
        }
    }
    
    private static class RangeRule {
        private final int start;
        private final int end;
        private final Class<? extends BaseError> exceptionClass;
        
        RangeRule(int start, int end, Class<? extends BaseError> exceptionClass) {
            this.start = start;
            this.end = end;
            this.exceptionClass = exceptionClass;
        }
        
        boolean matches(int code) {
            return code >= start && code <= end;
        }
    }
}

