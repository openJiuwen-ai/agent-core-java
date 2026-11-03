/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.resourcemanager;

/**
 * 0.1.12-compatible sys-operation manager alias.
 *
 * <p>Mirrors Python's {@code SysOperationMgr} in
 * {@code openjiuwen/core/runner/resources_manager/sys_operation_manager.py}.</p>
 */
public class SysOperationMgr extends SysOperationManager {

    public String kind() {
        return "sys_operation";
    }
}
