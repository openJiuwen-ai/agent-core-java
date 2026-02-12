// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.common.exception;

/**
 * Base exception for JiuWen framework
 * 
 * <p>This is the most basic runtime exception in the system.
 * 
 * @since 0.1.4
 */
public class JiuWenBaseException extends RuntimeException {
    
    private final int errorCode;
    private final String errorMessage;
    
    /**
     * Constructor
     * 
     * @param errorCode the error code
     * @param message the error message
     */
    public JiuWenBaseException(int errorCode, String message) {
        super(String.format("[%d] %s", errorCode, message));
        this.errorCode = errorCode;
        this.errorMessage = message;
    }
    
    /**
     * Gets the error code
     * 
     * @return the error code
     */
    public int getErrorCode() {
        return errorCode;
    }
    
    /**
     * Gets the error message (without code prefix)
     * 
     * @return the error message
     */
    public String getErrorMessage() {
        return errorMessage;
    }
    
    @Override
    public String toString() {
        return String.format("[%d] %s", errorCode, errorMessage);
    }
}

