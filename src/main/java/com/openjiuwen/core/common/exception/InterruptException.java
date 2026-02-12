// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.common.exception;

/**
 * Exception for interrupting execution
 * 
 * @since 0.1.4
 */
public class InterruptException extends JiuWenBaseException {
    
    /**
     * Constructor
     * 
     * @param errorCode the error code
     * @param message the error message
     */
    public InterruptException(int errorCode, String message) {
        super(errorCode, message);
    }
}

