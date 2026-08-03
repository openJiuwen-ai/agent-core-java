/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.lite;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Result of a memory write operation.
 *
 * <p>Mirrors Python's {@code WriteResult} in {@code openjiuwen/core/memory/lite/conflict_types.py}.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WriteResult {

    private boolean success;

    private String path;

    private WriteMode mode;

    private boolean conflictDetected;

    private List<String> conflictingFiles = new ArrayList<>();

    private String note;

    private String error;

    private String type;

    /**
     * Convert to a Python-shaped dictionary for tool responses.
     *
     * @return dictionary view with conditional fields
     */
    public Map<String, Object> toDict() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", success);
        result.put("path", path);
        result.put("mode", mode.value());
        if (type != null) {
            result.put("type", type);
        }
        if (conflictDetected) {
            result.put("conflict_detected", true);
            result.put("conflicting_files", new ArrayList<>(conflictingFiles));
        }
        if (note != null) {
            result.put("note", note);
        }
        if (error != null) {
            result.put("error", error);
        }
        return result;
    }
}
