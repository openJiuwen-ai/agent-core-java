// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.common.logging;

/**
 * Event status enumeration
 * 
 * @since 0.1.4
 */
public enum EventStatus {
    
    SUCCESS("success"),
    FAILURE("failure"),
    PENDING("pending"),
    TIMEOUT("timeout"),
    CANCELLED("cancelled");
    
    private final String value;
    
    EventStatus(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
    
    /**
     * Get event status from string value
     * 
     * @param value the string value
     * @return the event status
     */
    public static EventStatus fromValue(String value) {
        for (EventStatus status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown event status: " + value);
    }
}

