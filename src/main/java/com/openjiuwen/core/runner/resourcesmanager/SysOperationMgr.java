// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner.resourcesmanager;

import com.openjiuwen.core.common.exception.ErrorBuilder;
import com.openjiuwen.core.common.exception.StatusCode;

/**
 * SysOperation管理器
 * 
 * 对应Python: resources_manager/sys_operation_manager.py - SysOperationMgr
 */
public class SysOperationMgr {
    
    private final ThreadSafeDict<String, Object> sysOperations = new ThreadSafeDict<>();
    
    public SysOperationMgr() {
    }
    
    /**
     * 添加SysOperation实例
     * 
     * @param sysOperationId SysOperation ID
     * @param sysOperationInstance SysOperation实例
     * @throws com.openjiuwen.core.common.exception.BaseError 如果ID为null或已存在
     */
    public void addSysOperation(String sysOperationId, Object sysOperationInstance) {
        if (sysOperationId == null) {
            throw ErrorBuilder.build(
                StatusCode.SYS_OPERATION_MANAGER_PROCESS_ERROR,
                "sys_operation_id can not be none"
            );
        }
        if (sysOperations.containsKey(sysOperationId)) {
            throw ErrorBuilder.build(
                StatusCode.SYS_OPERATION_MANAGER_PROCESS_ERROR,
                "already exists sys_operation_card " + sysOperationId
            );
        }
        sysOperations.put(sysOperationId, sysOperationInstance);
    }
    
    /**
     * 移除SysOperation实例
     * 
     * @param sysOperationId SysOperation ID
     * @return 被移除的实例，不存在返回null
     * @throws com.openjiuwen.core.common.exception.BaseError 如果ID为null
     */
    public Object removeSysOperation(String sysOperationId) {
        if (sysOperationId == null) {
            throw ErrorBuilder.build(
                StatusCode.SYS_OPERATION_MANAGER_PROCESS_ERROR,
                "sys_operation_id can not be none"
            );
        }
        return sysOperations.pop(sysOperationId, null);
    }
    
    /**
     * 获取SysOperation实例
     * 
     * @param sysOperationId SysOperation ID
     * @return SysOperation实例，不存在返回null
     * @throws com.openjiuwen.core.common.exception.BaseError 如果ID为null
     */
    public Object getSysOperation(String sysOperationId) {
        if (sysOperationId == null) {
            throw ErrorBuilder.build(
                StatusCode.SYS_OPERATION_MANAGER_PROCESS_ERROR,
                "sys_operation_id can not be none"
            );
        }
        return sysOperations.get(sysOperationId);
    }
}

