// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
package com.openjiuwen.core.sysoperation.base;

/**
 * Enum for operation mode.
 * 
 * <p>对应 Python: openjiuwen.core.sys_operation.base.OperationMode
 * 
 * <p>Defines the available running modes for system operations:
 * <ul>
 *   <li>{@code LOCAL} - Operations run locally on the host machine</li>
 *   <li>{@code SANDBOX} - Operations run in a remote sandbox environment</li>
 * </ul>
 * 
 * @author OpenJiuwen
 * @since 2026-02-05
 */
public enum OperationMode {
    /**
     * Local mode - operations run directly on the host machine
     */
    LOCAL("local"),
    
    /**
     * Sandbox mode - operations run in a remote sandbox environment
     */
    SANDBOX("sandbox");

    private final String value;

    OperationMode(String value) {
        this.value = value;
    }

    /**
     * Gets the string value of the mode.
     * 
     * @return the mode value as string
     */
    public String getValue() {
        return value;
    }

    /**
     * Creates an OperationMode from its string value.
     * 
     * @param value the string value (case-insensitive)
     * @return the corresponding OperationMode
     * @throws IllegalArgumentException if the value is not valid
     */
    public static OperationMode fromValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Mode value cannot be null");
        }
        String lowerValue = value.toLowerCase();
        for (OperationMode mode : values()) {
            if (mode.value.equals(lowerValue)) {
                return mode;
            }
        }
        throw new IllegalArgumentException(
            String.format("Invalid operation mode: %s. Must be one of: local, sandbox", value));
    }

    @Override
    public String toString() {
        return value;
    }
}

