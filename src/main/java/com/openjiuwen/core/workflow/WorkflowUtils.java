/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow;

/**
 * Workflow-level helper utilities.
 * 
 * @since 0.1.7
 */
public final class WorkflowUtils {
    /**
     * WorkflowUtils.
     * 
     * @since 0.1.7
     */
    private WorkflowUtils() {
    }

    /**
     * Mirrors Python's {@code generate_workflow_key(workflow_id, workflow_version)}.
     * 
     * @param workflowId workflowId
     * @param workflowVersion workflowVersion
     * @return the result
     * @since 0.1.7
     */
    public static String generateWorkflowKey(String workflowId, String workflowVersion) {
        return workflowId + "_" + workflowVersion;
    }
}
