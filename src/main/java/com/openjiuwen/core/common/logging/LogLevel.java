// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.common.logging;

/**
 * Log level enumeration
 * 
 * @since 0.1.4
 */
public enum LogLevel {
    
    DEBUG("DEBUG"),
    INFO("INFO"),
    WARNING("WARNING"),
    ERROR("ERROR"),
    CRITICAL("CRITICAL");
    
    private final String value;
    
    LogLevel(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
    
    /**
     * Get log level from string value
     * 
     * @param value the string value
     * @return the log level
     */
    public static LogLevel fromValue(String value) {
        for (LogLevel level : values()) {
            if (level.value.equalsIgnoreCase(value)) {
                return level;
            }
        }
        throw new IllegalArgumentException("Unknown log level: " + value);
    }
}

