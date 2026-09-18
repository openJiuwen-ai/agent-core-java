/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow;

/**
 * Backward-compatible workflow helper facade.
 *
 * <p>Mirrors Python's {@code generate_workflow_key} in
 * {@code openjiuwen/core/workflow/base.py}.</p>
 */
public final class WorkflowUtils {

    private WorkflowUtils() {
    }

    public static String generateWorkflowKey(String workflowId, String workflowVersion) {
        return WorkflowKeys.generateWorkflowKey(workflowId, workflowVersion);
    }
}
