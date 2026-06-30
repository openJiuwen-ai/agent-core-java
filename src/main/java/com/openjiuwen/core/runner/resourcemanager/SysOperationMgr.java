/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.resourcemanager;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sysop.SysOperation;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Manager for SysOperation instances.
 * <p>
 * Unlike other managers, SysOperationMgr stores instances directly.
 * Mirrors Python's {@code SysOperationMgr} in {@code resources_manager/sys_operation_manager.py}.
 */
public class SysOperationMgr {

    private final ConcurrentHashMap<String, SysOperation> sysOperations = new ConcurrentHashMap<>();

    /**
     * Auto-generated for codecheck compliance.
     */
    public void addSysOperation(String sysOperationId, SysOperation sysOperationInstance) {
        if (sysOperationId == null) {
            throw ErrorHelper.buildError(StatusCode.SYS_OPERATION_MANAGER_PROCESS_ERROR,
                    "process", "add", "error_msg", "sys_operation_id can not be none");
        }
        if (sysOperations.containsKey(sysOperationId)) {
            throw ErrorHelper.buildError(StatusCode.SYS_OPERATION_MANAGER_PROCESS_ERROR,
                    "process", "add", "error_msg", "already exists sys_operation_card " + sysOperationId);
        }
        sysOperations.put(sysOperationId, sysOperationInstance);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public SysOperation removeSysOperation(String sysOperationId) {
        if (sysOperationId == null) {
            throw ErrorHelper.buildError(StatusCode.SYS_OPERATION_MANAGER_PROCESS_ERROR,
                    "process", "remove", "error_msg", "sys_operation_id can not be none");
        }
        return sysOperations.remove(sysOperationId);
    }

    /**
     * Clear all registered system operations.
     */
    public void clear() {
        sysOperations.clear();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public SysOperation getSysOperation(String sysOperationId) {
        if (sysOperationId == null) {
            throw ErrorHelper.buildError(StatusCode.SYS_OPERATION_MANAGER_PROCESS_ERROR,
                    "process", "get", "error_msg", "sys_operation_id can not be none");
        }
        return sysOperations.get(sysOperationId);
    }
}
