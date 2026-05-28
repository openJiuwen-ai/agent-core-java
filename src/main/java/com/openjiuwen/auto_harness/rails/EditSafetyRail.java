/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.rails;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Edit-safety rail — atomic change tracking + ruff check.
 *
 * <p>Mirrors Python's {@code EditSafetyRail} in {@code openjiuwen.auto_harness.rails.edit_safety_rail}.</p>
 */
public class EditSafetyRail {

    private static final Logger logger = Logger.getLogger(EditSafetyRail.class.getName());
    private static final Set<String> WRITE_TOOLS = Set.of("write_file", "edit_file");

    private final int maxFiles;
    private final Set<String> editedFiles = new HashSet<>();

    public EditSafetyRail() {
        this(3);
    }

    public EditSafetyRail(int maxFiles) {
        this.maxFiles = maxFiles;
    }

    /**
     * Hard-block writes outside the allowed repo scope.
     *
     * @param ctx the agent callback context
     */
    public void beforeToolCall(Object ctx) {
        // TODO: Implement path validation
    }

    /**
     * Record edit, check file count, run ruff.
     *
     * @param ctx the agent callback context
     */
    public void afterToolCall(Object ctx) {
        // TODO: Implement edit tracking and ruff check
    }

    /**
     * Reset the edited files tracking.
     */
    public void reset() {
        editedFiles.clear();
    }

    /**
     * Get the edited files.
     *
     * @return the set of edited file paths
     */
    public Set<String> getEditedFiles() {
        return new HashSet<>(editedFiles);
    }

    /**
     * Get the maximum files limit.
     *
     * @return the max files
     */
    public int getMaxFiles() {
        return maxFiles;
    }
}