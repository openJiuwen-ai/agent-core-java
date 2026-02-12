/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.stream;

/**
 * Enum for stream data message types.
 * 
 * @author OpenJiuwen
 * @since 1.0.0
 */
public enum StreamDataMsg {
    
    /**
     * Success message.
     */
    SUCCESS("success"),
    
    /**
     * Failure message.
     */
    FAIL("fail"),
    
    /**
     * Message end indicator.
     */
    MESSAGE_END("message_end"),
    
    /**
     * Finish indicator.
     */
    FINISH("finish");
    
    private final String value;
    
    StreamDataMsg(String value) {
        this.value = value;
    }
    
    /**
     * Gets the message value.
     * 
     * @return the value
     */
    public String getValue() {
        return value;
    }
}

