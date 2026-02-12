// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.controller.schema;

/**
 * Intent Type Enumeration.
 *
 * <p>Defines all intent types that users may express, used by the controller
 * to identify user requests and route them to appropriate processing logic.
 *
 * @author OpenJiuwen
 * @since 1.0.0
 */
public enum IntentType {

    /** Execute new task / interrupt executing tasks and execute new task. */
    CREATE_TASK("create_task"),

    /** Pause executing task. */
    PAUSE_TASK("pause_task"),

    /** Resume task (resume previously paused task). */
    RESUME_TASK("resume_task"),

    /** Continue task (continue executing task based on completed task). */
    CONTINUE_TASK("continue_task"),

    /** Supplement necessary information for task. */
    SUPPLEMENT_TASK("supplement_task"),

    /** Cancel currently executing task. */
    CANCEL_TASK("cancel_task"),

    /** Modify executing task. */
    MODIFY_TASK("modify_task"),

    /** Switch task (interrupt current task and execute another task). */
    SWITCH_TASK("switch_task"),

    /** Unknown intent, requires user clarification. */
    UNKNOWN_TASK("unknown_task");

    private final String value;

    IntentType(String value) {
        this.value = value;
    }

    /**
     * Gets the string value of the intent type.
     *
     * @return the intent type value
     */
    public String getValue() {
        return value;
    }

    /**
     * Returns the IntentType from its string value.
     *
     * @param value the string value
     * @return the corresponding IntentType
     * @throws IllegalArgumentException if value is not recognized
     */
    public static IntentType fromValue(String value) {
        for (IntentType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown IntentType value: " + value);
    }
}

