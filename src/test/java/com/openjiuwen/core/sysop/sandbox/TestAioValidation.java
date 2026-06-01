/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.core.sysop.SysOperationCard;
import com.openjiuwen.core.sysop.config.ContainerScope;
import com.openjiuwen.core.sysop.config.PreDeployLauncherConfig;
import com.openjiuwen.core.sysop.config.SandboxGatewayConfig;
import com.openjiuwen.core.sysop.config.SandboxIsolationConfig;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Sandbox phase-1 validation tests.
 * <p>
 * Mirrors Python's {@code test_aio_validation.py} in
 * {@code tests/unit_tests/core/sys_operation/sandbox/test_aio_validation.py}.
 */
class TestAioValidation {

    @Test
    void testPreDeployAioConfigIsAllowed() {
        SysOperationCard card = SysOperationCard.builder()
                .id("sandbox_ok")
                .mode(OperationMode.SANDBOX)
                .gatewayConfig(SandboxGatewayConfig.builder()
                        .isolation(SandboxIsolationConfig.builder()
                                .containerScope(ContainerScope.SYSTEM)
                                .build())
                        .launcherConfig(PreDeployLauncherConfig.create("http://localhost:8080", "aio"))
                        .build())
                .build();

        SysOperation op = new SysOperation(card);
        assertEquals(OperationMode.SANDBOX, op.getMode());
    }

    @Test
    void testMissingLauncherConfigIsRejected() {
        SysOperationCard card = SysOperationCard.builder()
                .id("sandbox_missing_launcher")
                .mode(OperationMode.SANDBOX)
                .gatewayConfig(SandboxGatewayConfig.builder()
                        .isolation(SandboxIsolationConfig.builder()
                                .containerScope(ContainerScope.SYSTEM)
                                .build())
                        .build())
                .build();

        BaseError err = assertThrows(BaseError.class, () -> new SysOperation(card));
        assertEquals(StatusCode.SYS_OPERATION_CARD_PARAM_ERROR.getCode(), err.getCode());
        assertTrue(err.getMessage().contains("sandbox mode requires launcher_config"));
    }

    @Test
    void testMissingSandboxTypeIsRejected() {
        PreDeployLauncherConfig launcherConfig = PreDeployLauncherConfig.create("http://localhost:8080", "");
        launcherConfig.setSandboxType("");
        SysOperationCard card = SysOperationCard.builder()
                .id("sandbox_missing_sandbox_type")
                .mode(OperationMode.SANDBOX)
                .gatewayConfig(SandboxGatewayConfig.builder()
                        .isolation(SandboxIsolationConfig.builder()
                                .containerScope(ContainerScope.SYSTEM)
                                .build())
                        .launcherConfig(launcherConfig)
                        .build())
                .build();

        BaseError err = assertThrows(BaseError.class, () -> new SysOperation(card));
        assertEquals(StatusCode.SYS_OPERATION_CARD_PARAM_ERROR.getCode(), err.getCode());
        assertTrue(err.getMessage().contains("sandbox mode requires sandbox_type"));
    }
}
