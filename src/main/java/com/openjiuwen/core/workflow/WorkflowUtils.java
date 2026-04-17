/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow;

/**
 * Workflow-level helper utilities.
 */
public final class WorkflowUtils {

    private WorkflowUtils() {
    }

    /**
     * Mirrors Python's {@code generate_workflow_key(workflow_id, workflow_version)}.
     */
    public static String generateWorkflowKey(String workflowId, String workflowVersion) {
        return workflowId + "_" + workflowVersion;
    }
}
