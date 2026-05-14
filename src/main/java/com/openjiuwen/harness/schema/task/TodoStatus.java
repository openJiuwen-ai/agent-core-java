package com.openjiuwen.harness.schema.task;

/**
 * Mirrors Python's {@code TodoStatus} in {@code openjiuwen.harness.schema.task}.
 */
public enum TodoStatus {
    PENDING("pending"),
    IN_PROGRESS("in_progress"),
    COMPLETED("completed"),
    CANCELLED("cancelled");

    private final String value;

    TodoStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static TodoStatus fromValue(String value) {
        if (value == null || value.isBlank()) {
            return PENDING;
        }
        for (TodoStatus status : values()) {
            if (status.value.equalsIgnoreCase(value) || status.name().equalsIgnoreCase(value)) {
                return status;
            }
        }
        return PENDING;
    }
}
