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
import com.openjiuwen.core.sysop.config.ContainerScope;
import com.openjiuwen.core.sysop.config.PreDeployLauncherConfig;
import com.openjiuwen.core.sysop.config.SandboxGatewayConfig;
import com.openjiuwen.core.sysop.config.SandboxIsolationConfig;

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
 * <p>Like the Python fixture, this uses sandbox_type="local" so the tests
 * exercise sandbox routing without depending on an external AIO service.
 */
public abstract class BaseSandboxTest {

    protected String cardId;
    protected SysOperationCard card;
    protected SysOperation sysOp;
    protected ResourceMgr rm;

    /**
     * Setup fixture that provides a SysOperation using sandbox mode.
     *
     * <p>This mirrors Python's local_op fixture: SANDBOX mode with a
     * pre-deployed local provider registered for fs/shell/code.
     */
    @BeforeEach
    void setUpSandbox() throws Exception {
        Runner.start();
        rm = Runner.resourceMgr();
        LocalSandboxProviders.register();

        cardId = "local_sandbox_" + UUID.randomUUID().toString().substring(0, 8);

        card = SysOperationCard.builder()
                .id(cardId)
                .mode(OperationMode.SANDBOX)
                .gatewayConfig(SandboxGatewayConfig.builder()
                        .isolation(SandboxIsolationConfig.builder()
                                .containerScope(ContainerScope.SYSTEM)
                                .build())
                        .launcherConfig(PreDeployLauncherConfig.create("http://local-provider:9999", "local"))
                        .timeoutSeconds(30)
                        .build())
                .build();

        var addRes = rm.addSysOperation(card, null);
        assertTrue(addRes.isOk(), "Failed to add sys operation");

        Object result = rm.getSysOperation(cardId, null, TagMatchStrategy.ALL);
        sysOp = extractSysOperation(result);
        assertNotNull(sysOp, "SysOperation should be retrieved");
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
