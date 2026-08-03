/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.resource;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Output schema for memory write.
 *
 * <p>Mirrors Python's {@code MemoryWriteOutput} in
 * {@code openjiuwen/core/workflow/components/resource/memory_write_comp.py}.</p>
 */
public class MemoryWriteOutput {

    @JsonProperty("success")
    private boolean success = true;

    public MemoryWriteOutput() {
    }

    public MemoryWriteOutput(boolean success) {
        this.success = success;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    /**
     * Convert to Python's {@code model_dump()} dictionary shape.
     *
     * @return plain output map with Python field names
     */
    public Map<String, Object> toMap() {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("success", success);
        return output;
    }
}
