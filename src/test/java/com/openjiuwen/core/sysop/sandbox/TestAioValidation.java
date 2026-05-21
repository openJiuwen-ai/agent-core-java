/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.core.sysop.SysOperationCard;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.config.SandboxGatewayConfig;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test sandbox phase-1 validation.
 * <p>
 * Mirrors Python's {@code test_aio_validation.py} in
 * {@code tests/unit_tests/core/sys_operation/sandbox/test_aio_validation.py}.
 */
class TestAioValidation {

    @Test
    void testPreDeployAioConfigIsAllowed() {
        /** Test that pre-deploy aio config is allowed. */
        SysOperationCard card = SysOperationCard.builder()
                .id("sandbox_ok")
                .mode(OperationMode.SANDBOX)
                .gatewayConfig(SandboxGatewayConfig.builder()
                        .gatewayUrl("http://localhost:8080")
                        .build())
                .build();

        SysOperation op = new SysOperation(card);
        assertEquals(OperationMode.SANDBOX, op.getMode());
    }

    @Test
    void testMissingLauncherConfigIsRejected() {
        /** Test that missing launcher config is rejected. */
        SysOperationCard card = SysOperationCard.builder()
                .id("sandbox_missing_launcher")
                .mode(OperationMode.SANDBOX)
                // Missing gatewayConfig - should be rejected
                .build();

        // In Java, validation happens in constructor
        assertThrows(Exception.class, () -> new SysOperation(card));
    }

    @Test
    void testMissingSandboxTypeIsRejected() {
        /** Test that missing sandbox type is rejected. */
        SysOperationCard card = SysOperationCard.builder()
                .id("sandbox_missing_sandbox_type")
                .mode(OperationMode.SANDBOX)
                .gatewayConfig(SandboxGatewayConfig.builder()
                        .gatewayUrl("")  // Empty URL
                        .build())
                .build();

        assertThrows(Exception.class, () -> new SysOperation(card));
    }
}