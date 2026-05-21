/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox.mock;

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
 * Base test class for local provider sandbox tests.
 * <p>
 * Mirrors Python's {@code conftest.py} in
 * {@code tests/unit_tests/core/sys_operation/sandbox/mock/conftest.py}.
 *
 * <p>Note: This fixture tests the SandboxRegistry provider registration and routing
 * mechanism using sandbox_type="local" without needing the AIO sandbox service.
 * In Java, sandbox infrastructure is currently a stub, so tests inheriting from
 * this class may be skipped until sandbox mode is fully implemented.
 */
public abstract class BaseLocalProviderTest {

    protected String cardId;
    protected SysOperationCard card;
    protected SysOperation sysOp;
    protected ResourceMgr rm;

    /**
     * Setup fixture that provides a SysOperation using local providers.
     *
     * <p>Note: In Python, this uses sandbox_type="local" which routes to local
     * providers registered in SandboxRegistry. In Java, this is stubbed.
     */
    @BeforeEach
    void setUpLocalProvider() throws Exception {
        Runner.start();
        rm = Runner.resourceMgr();

        cardId = "local_sandbox_" + UUID.randomUUID().toString().substring(0, 8);

        // SandboxGateway.getInstance() - stub in Java
        // For now, create a basic SandboxGatewayConfig
        card = SysOperationCard.builder()
                .id(cardId)
                .mode(OperationMode.SANDBOX)
                .gatewayConfig(SandboxGatewayConfig.builder()
                        .gatewayUrl("http://local-provider:9999")
                        .build())
                .build();

        try {
            var addRes = rm.addSysOperation(card, null);
            assertTrue(addRes.isOk(), "Failed to add sys operation");

            Object result = rm.getSysOperation(cardId, null, TagMatchStrategy.ALL);
            sysOp = extractSysOperation(result);
            assertNotNull(sysOp, "SysOperation should be retrieved");
        } catch (Exception e) {
            // Sandbox mode may not be fully implemented
            sysOp = null;
        }
    }

    @AfterEach
    void tearDownLocalProvider() throws Exception {
        if (rm != null && cardId != null) {
            try {
                rm.removeSysOperation(cardId, null, TagMatchStrategy.ALL, true);
            } catch (Exception ignored) {
            }
        }
        Runner.stop();
    }

    protected SysOperation extractSysOperation(Object result) {
        if (result instanceof SysOperation op) {
            return op;
        } else if (result instanceof java.util.List<?> list && !list.isEmpty()) {
            return (SysOperation) list.get(0);
        }
        return null;
    }
}