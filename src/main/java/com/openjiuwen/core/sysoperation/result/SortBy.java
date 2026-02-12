// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
package com.openjiuwen.core.sysoperation.result;

/**
 * Enum for file listing sort field.
 * 
 * <p>对应 Python: Literal['name', 'modified_time', 'size']
 * 
 * @author OpenJiuwen
 * @since 2026-02-05
 */
public enum SortBy {
    /**
     * Sort by file name.
     */
    NAME("name"),
    
    /**
     * Sort by last modification time.
     */
    MODIFIED_TIME("modified_time"),
    
    /**
     * Sort by file size.
     */
    SIZE("size");

    private final String value;

    SortBy(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static SortBy fromValue(String value) {
        for (SortBy sortBy : values()) {
            if (sortBy.value.equalsIgnoreCase(value)) {
                return sortBy;
            }
        }
        throw new IllegalArgumentException("Invalid sort by: " + value);
    }

    @Override
    public String toString() {
        return value;
    }
}

