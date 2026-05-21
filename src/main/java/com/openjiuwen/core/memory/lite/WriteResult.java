/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.lite;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Result of a memory write operation.
 * <p>
 * Mirrors Python's {@code WriteResult} dataclass from
 * <code>memory/lite/conflict_types.py</code>.
 */
public class WriteResult {

    private final boolean success;
    private final String path;
    private final WriteMode mode;
    private boolean conflictDetected;
    private List<String> conflictingFiles = new ArrayList<>();
    private String note;
    private String error;
    private String type;

    public WriteResult(boolean success, String path, WriteMode mode) {
        this.success = success;
        this.path = path;
        this.mode = mode;
    }

    public boolean isSuccess() { return success; }
    public String getPath() { return path; }
    public WriteMode getMode() { return mode; }

    public boolean isConflictDetected() { return conflictDetected; }
    public void setConflictDetected(boolean conflictDetected) { this.conflictDetected = conflictDetected; }

    public List<String> getConflictingFiles() { return conflictingFiles; }
    public void setConflictingFiles(List<String> conflictingFiles) { this.conflictingFiles = conflictingFiles; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    /**
     * Convert to map for tool response.
     */
    public Map<String, Object> toDict() {
        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("path", path);
        result.put("mode", mode.getValue());
        if (type != null) result.put("type", type);
        if (conflictDetected) {
            result.put("conflict_detected", true);
            result.put("conflicting_files", conflictingFiles);
        }
        if (note != null) result.put("note", note);
        if (error != null) result.put("error", error);
        return result;
    }
}
