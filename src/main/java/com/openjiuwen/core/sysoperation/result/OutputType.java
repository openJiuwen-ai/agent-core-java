// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
package com.openjiuwen.core.sysoperation.result;

/**
 * Enum for output stream type.
 * 
 * <p>对应 Python: Literal["stdout", "stderr"]
 * 
 * @author OpenJiuwen
 * @since 2026-02-05
 */
public enum OutputType {
    /**
     * Standard output stream.
     */
    STDOUT("stdout"),
    
    /**
     * Standard error stream.
     */
    STDERR("stderr");

    private final String value;

    OutputType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static OutputType fromValue(String value) {
        for (OutputType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid output type: " + value);
    }

    @Override
    public String toString() {
        return value;
    }
}
