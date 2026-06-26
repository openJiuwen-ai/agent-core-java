/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.resourcemanager;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sys_operation.OperationMode;
import com.openjiuwen.core.sys_operation.SysOperation;
import com.openjiuwen.core.sys_operation.SysOperationCard;
import com.openjiuwen.core.sys_operation.config.ContainerScope;
import com.openjiuwen.core.sys_operation.config.SandboxGatewayConfig;
import com.openjiuwen.core.sys_operation.config.SandboxIsolationConfig;
import com.openjiuwen.core.sys_operation.config.SandboxLauncherConfig;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python tests for
 * {@code openjiuwen/core/runner/resources_manager/sys_operation_manager.py}.
 */
class SysOperationManagerTest {

    @Test
    void addGetAndRemoveLocalSysOperation() {
        SysOperationManager manager = new SysOperationManager();
        SysOperation operation = new SysOperation(new SysOperationCard("local-1", OperationMode.LOCAL, null));

        manager.addSysOperation("local-1", operation);

        assertSame(operation, manager.getSysOperation("local-1"));
        assertSame(operation, manager.removeSysOperation("local-1"));
        assertNull(manager.getSysOperation("local-1"));
    }

    @Test
    void nullIdsAndDuplicateIdsUsePythonErrorStatus() {
        SysOperationManager manager = new SysOperationManager();
        SysOperation operation = new SysOperation(new SysOperationCard("local-1", OperationMode.LOCAL, null));

        BaseError addError = assertThrows(BaseError.class, () -> manager.addSysOperation(null, operation));
        assertEquals(StatusCode.SYS_OPERATION_MANAGER_PROCESS_ERROR, addError.getStatus());

        manager.addSysOperation("local-1", operation);
        BaseError duplicateError = assertThrows(BaseError.class, () -> manager.addSysOperation("local-1", operation));
        assertEquals(StatusCode.SYS_OPERATION_MANAGER_PROCESS_ERROR, duplicateError.getStatus());

        BaseError getError = assertThrows(BaseError.class, () -> manager.getSysOperation(null));
        assertEquals(StatusCode.SYS_OPERATION_MANAGER_PROCESS_ERROR, getError.getStatus());

        BaseError removeError = assertThrows(BaseError.class, () -> manager.removeSysOperation(null));
        assertEquals(StatusCode.SYS_OPERATION_MANAGER_PROCESS_ERROR, removeError.getStatus());
    }

    @Test
    void sandboxIsolationKeyConflictIsRejectedUntilOwnerIsRemoved() {
        SysOperationManager manager = new SysOperationManager();
        SysOperation first = sandboxOperation("sandbox-1");
        SysOperation second = sandboxOperation("sandbox-2");

        manager.addSysOperation("sandbox-1", first);

        IllegalArgumentException conflict = assertThrows(IllegalArgumentException.class,
                () -> manager.addSysOperation("sandbox-2", second));
        assertTrue(conflict.getMessage().contains("sandbox-1"));
        assertTrue(conflict.getMessage().contains("sandbox-2"));

        assertSame(first, manager.removeSysOperation("sandbox-1"));
        manager.addSysOperation("sandbox-2", second);

        assertSame(second, manager.getSysOperation("sandbox-2"));
        assertEquals("sandbox-2", manager.getSandboxKeyOwnerSnapshot().get(second.getIsolationKeyTemplate()));
    }

    private static SysOperation sandboxOperation(String id) {
        SysOperationCard card = new SysOperationCard();
        card.setId(id);
        card.setMode(OperationMode.SANDBOX);
        card.setGatewayConfig(SandboxGatewayConfig.builder()
                .isolation(SandboxIsolationConfig.builder()
                        .containerScope(ContainerScope.SYSTEM)
                        .prefix("shared")
                        .build())
                .launcherConfig(SandboxLauncherConfig.builder()
                        .launcherType("pre_deploy")
                        .sandboxType("aio")
                        .build())
                .build());
        return new SysOperation(card);
    }
}
