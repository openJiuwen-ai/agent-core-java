/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.resourcemanager;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sysop.SysOperation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manager for SysOperation instances.
 * <p>
 * Unlike other managers, SysOperationMgr stores instances directly.
 * Mirrors Python's {@code SysOperationMgr} in {@code resources_manager/sys_operation_manager.py}.
 */
public class SysOperationMgr {

    private final ConcurrentHashMap<String, SysOperation> sysOperations = new ConcurrentHashMap<>();
    private final Map<String, String> sandboxKeyOwnerMap = new ConcurrentHashMap<>();

    public void addSysOperation(String sysOperationId, SysOperation sysOperationInstance) {
        if (sysOperationId == null) {
            throw ErrorHelper.buildError(StatusCode.SYS_OPERATION_MANAGER_PROCESS_ERROR,
                    "process", "add", "error_msg", "sys_operation_id can not be none");
        }
        if (sysOperations.containsKey(sysOperationId)) {
            throw ErrorHelper.buildError(StatusCode.SYS_OPERATION_MANAGER_PROCESS_ERROR,
                    "process", "add", "error_msg", "already exists sys_operation_card " + sysOperationId);
        }
        String isolationKeyTemplate = sysOperationInstance.getIsolationKeyTemplate();
        if (isolationKeyTemplate != null && !isolationKeyTemplate.isBlank()) {
            String existingOpId = sandboxKeyOwnerMap.get(isolationKeyTemplate);
            if (existingOpId != null && !existingOpId.equals(sysOperationId)) {
                throw new IllegalArgumentException(
                        "Isolation key template '" + isolationKeyTemplate + "' is already registered "
                                + "by operation '" + existingOpId + "'. Cannot register operation '"
                                + sysOperationId + "' with the same sandbox configuration.");
            }
            sandboxKeyOwnerMap.put(isolationKeyTemplate, sysOperationId);
        }
        sysOperations.put(sysOperationId, sysOperationInstance);
    }

    public SysOperation removeSysOperation(String sysOperationId) {
        if (sysOperationId == null) {
            throw ErrorHelper.buildError(StatusCode.SYS_OPERATION_MANAGER_PROCESS_ERROR,
                    "process", "remove", "error_msg", "sys_operation_id can not be none");
        }
        SysOperation sysOperation = sysOperations.remove(sysOperationId);
        if (sysOperation != null) {
            String isolationKeyTemplate = sysOperation.getIsolationKeyTemplate();
            if (isolationKeyTemplate != null && !isolationKeyTemplate.isBlank()) {
                sandboxKeyOwnerMap.remove(isolationKeyTemplate);
            }
        }
        return sysOperation;
    }

    /**
     * Clear all registered system operations.
     */
    public void clear() {
        sysOperations.clear();
        sandboxKeyOwnerMap.clear();
    }

    public SysOperation getSysOperation(String sysOperationId) {
        if (sysOperationId == null) {
            throw ErrorHelper.buildError(StatusCode.SYS_OPERATION_MANAGER_PROCESS_ERROR,
                    "process", "get", "error_msg", "sys_operation_id can not be none");
        }
        return sysOperations.get(sysOperationId);
    }
}
