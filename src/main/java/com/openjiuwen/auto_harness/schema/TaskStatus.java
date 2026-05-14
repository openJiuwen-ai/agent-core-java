package com.openjiuwen.auto_harness.schema;

/**
 * Mirrors Python's {@code TaskStatus} in {@code openjiuwen.auto_harness.schema}.
 */
public enum TaskStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED,
    TIMEOUT,
    REVERTED;

    @Override
    public String toString() {
        return name().toLowerCase();
    }
}
