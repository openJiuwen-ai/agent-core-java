/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.resource;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Output model for the Memory Write component.
 * <p>
 * Mirrors Python's {@code MemoryWriteOutput}.
 * 
 * @since 0.1.7
 */
public class MemoryWriteOutput {
    private boolean isSuccess = true;

    /**
     * Default constructor.
     * 
     * @since 0.1.7
     */
    public MemoryWriteOutput() {
    }

    /**
     * Constructor with success parameter.
     * 
     * @param isSuccess whether the operation was successful
     * @since 0.1.7
     */
    public MemoryWriteOutput(boolean isSuccess) {
        this.isSuccess = isSuccess;
    }

    /**
     * Check if the operation was successful.
     * 
     * @return true if successful, false otherwise
     * @since 0.1.7
     */
    public boolean isSuccess() {
        return isSuccess;
    }

    /**
     * Set the success status.
     * 
     * @param isSuccess whether the operation was successful
     * @since 0.1.7
     */
    public void setSuccess(boolean isSuccess) {
        this.isSuccess = isSuccess;
    }

    /**
     * Convert to a plain map representation.
     * 
     * @return the map representation
     * @since 0.1.7
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("success", isSuccess);
        return map;
    }
}
