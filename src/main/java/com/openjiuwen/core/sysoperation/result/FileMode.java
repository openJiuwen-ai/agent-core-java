// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
package com.openjiuwen.core.sysoperation.result;

/**
 * Enum for file read/write mode.
 * 
 * <p>对应 Python: Literal['text', 'bytes']
 * 
 * @author OpenJiuwen
 * @since 2026-02-05
 */
public enum FileMode {
    /**
     * Text mode - file content as string.
     */
    TEXT("text"),
    
    /**
     * Binary mode - file content as bytes.
     */
    BYTES("bytes");

    private final String value;

    FileMode(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static FileMode fromValue(String value) {
        for (FileMode mode : values()) {
            if (mode.value.equalsIgnoreCase(value)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Invalid file mode: " + value);
    }

    @Override
    public String toString() {
        return value;
    }
}
