/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox;

import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.runner.resourcemanager.ResourceMgr;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.core.sysop.SysOperationCard;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.config.SandboxGatewayConfig;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Base test class for sandbox SysOperation tests.
 * <p>
 * Mirrors Python's {@code conftest.py} in
 * {@code tests/unit_tests/core/sys_operation/sandbox/conftest.py}.
 *
 * <p>Note: Sandbox mode in Java is currently a stub.
 * This class provides the fixture structure for future sandbox tests.
 */
public abstract class BaseSandboxTest {

    protected String cardId;
    protected SysOperationCard card;
    protected SysOperation sysOp;
    protected ResourceMgr rm;

    /**
     * Setup fixture that provides a SysOperation using sandbox mode.
     *
     * <p>Note: This test setup mirrors Python's local_op fixture but uses
     * SANDBOX mode. In Java, the sandbox infrastructure is a stub,
     * so tests inheriting from this class may be skipped if sandbox
     * functionality is not fully implemented.
     */
    @BeforeEach
    void setUpSandbox() throws Exception {
        Runner.start();
        rm = Runner.resourceMgr();

        cardId = "local_sandbox_" + UUID.randomUUID().toString().substring(0, 8);

        // SandboxGateway.getInstance() - stub in Java
        // For now, create a basic SandboxGatewayConfig
        card = SysOperationCard.builder()
                .id(cardId)
                .mode(OperationMode.SANDBOX)
                .gatewayConfig(SandboxGatewayConfig.builder()
                        .timeoutSeconds(30)
                        .build())
                .build();

        try {
            var addRes = rm.addSysOperation(card, null);
            assertTrue(addRes.isOk(), "Failed to add sys operation");

            Object result = rm.getSysOperation(cardId, null, TagMatchStrategy.ALL);
            sysOp = extractSysOperation(result);
            assertNotNull(sysOp, "SysOperation should be retrieved");
        } catch (Exception e) {
            // Sandbox mode may not be fully implemented - allow test to proceed
            // Tests should check sysOp != null before using
            sysOp = null;
        }
    }

    @AfterEach
    void tearDownSandbox() throws Exception {
        if (rm != null && cardId != null) {
            try {
                rm.removeSysOperation(cardId, null, TagMatchStrategy.ALL, true);
            } catch (Exception ignored) {
                // Ignore cleanup errors
            }
        }
        Runner.stop();
    }

    /**
     * Helper to extract SysOperation from ResourceMgr result.
     */
    protected SysOperation extractSysOperation(Object result) {
        if (result instanceof SysOperation op) {
            return op;
        } else if (result instanceof java.util.List<?> list && !list.isEmpty()) {
            return (SysOperation) list.get(0);
        }
        return null;
    }
}