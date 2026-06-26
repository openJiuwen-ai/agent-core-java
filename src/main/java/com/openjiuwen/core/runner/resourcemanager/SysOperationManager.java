/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.resourcemanager;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sys_operation.SysOperation;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mirrors Python's {@code SysOperationMgr} in
 * {@code openjiuwen/core/runner/resources_manager/sys_operation_manager.py}.
 */
public class SysOperationManager {

    private final ThreadSafeDict<String, SysOperation> sysOperations = new ThreadSafeDict<>();
    private final Map<String, String> sandboxKeyOwnerMap = new LinkedHashMap<>();

    public void addSysOperation(String sysOperationId, SysOperation sysOperationInstance) {
        if (sysOperationId == null) {
            throw ErrorHelper.buildError(
                    StatusCode.SYS_OPERATION_MANAGER_PROCESS_ERROR,
                    "process",
                    "add",
                    "error_msg",
                    "sys_operation_id can not be none"
            );
        }
        if (sysOperations.containsKey(sysOperationId)) {
            throw ErrorHelper.buildError(
                    StatusCode.SYS_OPERATION_MANAGER_PROCESS_ERROR,
                    "process",
                    "add",
                    "error_msg",
                    "already exists sys_operation_card " + sysOperationId
            );
        }
        String isolationKeyTemplate = sysOperationInstance.getIsolationKeyTemplate();
        if (isolationKeyTemplate != null && !isolationKeyTemplate.isEmpty()) {
            if (sandboxKeyOwnerMap.containsKey(isolationKeyTemplate)) {
                String existingOperationId = sandboxKeyOwnerMap.get(isolationKeyTemplate);
                if (!existingOperationId.equals(sysOperationId)) {
                    throw new IllegalArgumentException(
                            "Isolation key template '" + isolationKeyTemplate + "' is already registered "
                                    + "by operation '" + existingOperationId + "'. Cannot register operation '"
                                    + sysOperationId + "' with the same sandbox configuration."
                    );
                }
            }
            sandboxKeyOwnerMap.put(isolationKeyTemplate, sysOperationId);
        }
        sysOperations.put(sysOperationId, sysOperationInstance);
    }

    public SysOperation removeSysOperation(String sysOperationId) {
        if (sysOperationId == null) {
            throw ErrorHelper.buildError(
                    StatusCode.SYS_OPERATION_MANAGER_PROCESS_ERROR,
                    "process",
                    "remove",
                    "error_msg",
                    "sys_operation_id can not be none"
            );
        }
        SysOperation sysOperation = sysOperations.pop(sysOperationId, null);
        if (sysOperation != null && sysOperation.getIsolationKeyTemplate() != null
                && !sysOperation.getIsolationKeyTemplate().isEmpty()) {
            sandboxKeyOwnerMap.remove(sysOperation.getIsolationKeyTemplate());
        }
        return sysOperation;
    }

    public SysOperation getSysOperation(String sysOperationId) {
        if (sysOperationId == null) {
            throw ErrorHelper.buildError(
                    StatusCode.SYS_OPERATION_MANAGER_PROCESS_ERROR,
                    "process",
                    "get",
                    "error_msg",
                    "sys_operation_id can not be none"
            );
        }
        return sysOperations.get(sysOperationId);
    }

    Map<String, String> getSandboxKeyOwnerSnapshot() {
        return new LinkedHashMap<>(sandboxKeyOwnerMap);
    }
}
