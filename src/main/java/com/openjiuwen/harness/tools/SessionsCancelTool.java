/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import java.util.Map;

/**
 * Public class SessionsCancelTool used by the Java parity implementation.
 *
 * @since 1.0
 */
public class SessionsCancelTool {
    private final SessionToolkit toolkit;

    /**
     * Auto-generated for codecheck compliance.
     */
    public SessionsCancelTool(SessionToolkit toolkit) {
        this.toolkit = toolkit;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public ToolOutput cancel(String taskId) {
        SessionTaskRow row = toolkit.get(taskId);
        if (row == null) {
            return ToolOutput.builder()
                    .success(false)
                    .error("Task " + taskId + " not found")
                    .build();
        }
        toolkit.markCanceled(taskId);
        return ToolOutput.builder()
                .success(true)
                .data(Map.of("task_id", taskId, "status", "canceled"))
                .build();
    }
}
