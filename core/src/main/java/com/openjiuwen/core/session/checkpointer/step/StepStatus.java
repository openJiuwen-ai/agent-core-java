/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.checkpointer.step;

/**
 * Lifecycle status of a single agent step recorded in the {@link StepCursor}.
 *
 * <p>The status travels with the full state snapshot written by the checkpointer,
 * so recovery can tell whether the last persisted step finished or died mid-flight.</p>
 */
public enum StepStatus {
    /** The step started but has not finished; recovery treats it as unfinished work. */
    RUNNING,
    /** The step completed successfully; recovery resumes from the next boundary. */
    COMPLETED,
    /** The step failed; recovery applies the failure policy instead of blind replay. */
    FAILED;

    /**
     * Parse a status from its persisted string form.
     *
     * @param value raw value from a serialized cursor map, may be null
     * @return the parsed status, or null when the value is not a known status name
     */
    public static StepStatus fromString(Object value) {
        if (value == null) {
            return null;
        }
        String name = String.valueOf(value);
        for (StepStatus status : values()) {
            if (status.name().equals(name)) {
                return status;
            }
        }
        return null;
    }
}
