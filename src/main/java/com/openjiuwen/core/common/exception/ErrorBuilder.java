// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.common.exception;

import java.util.Map;

/**
 * Utility class for building and raising errors
 * 
 * <p>Provides convenient methods for creating and throwing exceptions
 * with automatic type resolution based on StatusCode.
 * 
 * @since 0.1.4
 */
public final class ErrorBuilder {
    
    private ErrorBuilder() {
        // Utility class
    }
    
    /**
     * Build an error instance without throwing it
     * 
     * @param status the status code
     * @return the error instance
     */
    public static BaseError build(StatusCode status) {
        return StatusExceptionMapper.createException(status, null, null, null, null);
    }
    
    /**
     * Build an error instance with message
     * 
     * @param status the status code
     * @param msg custom message
     * @return the error instance
     */
    public static BaseError build(StatusCode status, String msg) {
        return StatusExceptionMapper.createException(status, msg, null, null, null);
    }
    
    /**
     * Build an error instance with full parameters
     * 
     * @param status the status code
     * @param msg custom message
     * @param details additional details
     * @param cause the cause exception
     * @param params template parameters
     * @return the error instance
     */
    public static BaseError build(
            StatusCode status,
            String msg,
            Object details,
            Throwable cause,
            Map<String, Object> params) {
        return StatusExceptionMapper.createException(status, msg, details, cause, params);
    }
    
    /**
     * Build and throw an error
     * 
     * @param status the status code
     * @throws BaseError always thrown
     */
    public static void raise(StatusCode status) {
        throw build(status);
    }
    
    /**
     * Build and throw an error with message
     * 
     * @param status the status code
     * @param msg custom message
     * @throws BaseError always thrown
     */
    public static void raise(StatusCode status, String msg) {
        throw build(status, msg);
    }
    
    /**
     * Build and throw an error with full parameters
     * 
     * @param status the status code
     * @param msg custom message
     * @param details additional details
     * @param cause the cause exception
     * @param params template parameters
     * @throws BaseError always thrown
     */
    public static void raise(
            StatusCode status,
            String msg,
            Object details,
            Throwable cause,
            Map<String, Object> params) {
        throw build(status, msg, details, cause, params);
    }
    
    /**
     * Raise a system/framework error
     * 
     * @param status the status code
     * @param cause the cause exception
     * @param params template parameters
     * @throws FrameworkError always thrown
     */
    public static void systemError(StatusCode status, Throwable cause, Map<String, Object> params) {
        throw new FrameworkError(status, null, null, cause, params);
    }
    
    /**
     * Raise a validation error
     * 
     * @param status the status code
     * @param cause the cause exception
     * @param params template parameters
     * @throws ValidationError always thrown
     */
    public static void validateError(StatusCode status, Throwable cause, Map<String, Object> params) {
        throw new ValidationError(status, null, null, cause, params);
    }
    
    /**
     * Raise a termination (non-error control flow)
     * 
     * @param status the status code
     * @param params template parameters
     * @throws Termination always thrown
     */
    public static void terminate(StatusCode status, Map<String, Object> params) {
        throw new Termination(status, null, null, null, params);
    }
}

