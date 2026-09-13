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
 * 
 * @since 0.1.7
 */
public class WriteResult {
    private final boolean isSuccess;
    private final String path;
    private final WriteMode mode;
    private boolean isConflictDetected;

    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private List<String> conflictingFiles = new ArrayList<>();
    private String note;
    private String error;
    private String type;

    /**
     * WriteResult.
     * 
     * @param isSuccess isSuccess
     * @param path path
     * @param mode mode
     * @since 0.1.7
     */
    public WriteResult(boolean isSuccess, String path, WriteMode mode) {
        this.isSuccess = isSuccess;
        this.path = path;
        this.mode = mode;
    }

    /**
     * conflictDetected.
     * 
     * @param isConflictDetected isConflictDetected
     * @return the result
     * @since 0.1.7
     */
    public WriteResult conflictDetected(boolean isConflictDetected) {
        this.isConflictDetected = isConflictDetected;
        return this;
    }

    /**
     * conflictingFiles.
     * 
     * @param conflictingFiles conflictingFiles
     * @return the result
     * @since 0.1.7
     */
    public WriteResult conflictingFiles(List<String> conflictingFiles) {
        this.conflictingFiles = new ArrayList<>(conflictingFiles);
        return this;
    }

    /**
     * note.
     * 
     * @param note note
     * @return the result
     * @since 0.1.7
     */
    public WriteResult note(String note) {
        this.note = note;
        return this;
    }

    /**
     * error.
     * 
     * @param error error
     * @return the result
     * @since 0.1.7
     */
    public WriteResult error(String error) {
        this.error = error;
        return this;
    }

    /**
     * type.
     * 
     * @param type type
     * @return the result
     * @since 0.1.7
     */
    public WriteResult type(String type) {
        this.type = type;
        return this;
    }

    /**
     * toMap.
     * 
     * @return the result
     * @since 0.1.7
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
