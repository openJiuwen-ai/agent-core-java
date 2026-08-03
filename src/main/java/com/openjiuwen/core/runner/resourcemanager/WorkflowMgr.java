/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.resourcemanager;

/**
 * 0.1.12-compatible workflow manager alias.
 *
 * <p>Mirrors Python's {@code WorkflowMgr} in
 * {@code openjiuwen/core/runner/resources_manager/workflow_manager.py}.</p>
 */
public class WorkflowMgr extends WorkflowManager {

    public String kind() {
        return "workflow";
    }
}
