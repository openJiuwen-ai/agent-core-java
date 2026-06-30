/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.lite;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Result of a memory write operation.
 */
public class WriteResult {
    private final boolean isSuccess;
    private final String path;
    private final WriteMode mode;
    private boolean isConflictDetected;
    private List<String> conflictingFiles = new ArrayList<>();
    private String note;
    private String error;
    private String type;

    /**
     * Auto-generated for codecheck compliance.
     */
    public WriteResult(boolean isSuccess, String path, WriteMode mode) {
        this.isSuccess = isSuccess;
        this.path = path;
        this.mode = mode;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public WriteResult conflictDetected(boolean isConflictDetected) {
        this.isConflictDetected = isConflictDetected;
        return this;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public WriteResult conflictingFiles(List<String> conflictingFiles) {
        this.conflictingFiles = new ArrayList<>(conflictingFiles);
        return this;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public WriteResult note(String note) {
        this.note = note;
        return this;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public WriteResult error(String error) {
        this.error = error;
        return this;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public WriteResult type(String type) {
        this.type = type;
        return this;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", isSuccess);
        result.put("path", path);
        result.put("mode", mode.name().toLowerCase(Locale.ROOT));
        if (type != null) {
            result.put("type", type);
        }
        if (isConflictDetected) {
            result.put("conflict_detected", true);
            result.put("conflicting_files", conflictingFiles);
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
