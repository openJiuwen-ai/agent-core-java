/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow;

/**
 * Mirrors Python's {@code generate_workflow_key} in
 * {@code openjiuwen/core/workflow/base.py}.
 */
public final class WorkflowKeys {

    private WorkflowKeys() {
    }

    public static String generateWorkflowKey(String workflowId, String workflowVersion) {
        return workflowId + "_" + workflowVersion;
    }
}
