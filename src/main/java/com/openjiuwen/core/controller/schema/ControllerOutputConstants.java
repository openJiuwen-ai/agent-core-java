// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.controller.schema;

/**
 * Constants for controller output types.
 *
 * @author OpenJiuwen
 * @since 1.0.0
 */
public final class ControllerOutputConstants {

    private ControllerOutputConstants() {
        // Utility class
    }

    /**
     * Processing status - task is still being processed.
     */
    public static final String TASK_PROCESSING = "processing";

    /**
     * All tasks have been processed.
     */
    public static final String ALL_TASKS_PROCESSED = "all_tasks_processed";
}

