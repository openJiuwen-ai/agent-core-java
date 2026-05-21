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

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.Disabled;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test sandbox isolation key template conflict detection.
 * <p>
 * Mirrors Python's {@code test_templateid_conflict.py} in
 * {@code tests/unit_tests/core/sys_operation/sandbox/test_templateid_conflict.py}.
 *
 * <p>Note: Sandbox mode is stubbed in Java - tests are disabled until implemented.
 */
@Disabled("Sandbox mode is not fully implemented in Java")
class TestTemplateidConflict {

    private ResourceMgr rm;
    private List<String> createdOperationIds;

    @BeforeEach
    void setUp() throws Exception {
        Runner.start();
        rm = Runner.resourceMgr();
        createdOperationIds = new ArrayList<>();
    }

    @AfterEach
    void tearDown() throws Exception {
        for (int i = createdOperationIds.size() - 1; i >= 0; i--) {
            try {
                rm.removeSysOperation(createdOperationIds.get(i), null, TagMatchStrategy.ALL, true);
            } catch (Exception ignored) {
            }
        }
        Runner.stop();
    }

    @Test
    void testAddTwoSandboxOpsWithSameConfigShouldRaiseConflict() {
        /** Test that adding two sandbox operations with identical configs raises conflict error. */
        String cardId1 = "sandbox_op_1_" + UUID.randomUUID().toString().substring(0, 8);
        String cardId2 = "sandbox_op_2_" + UUID.randomUUID().toString().substring(0, 8);

        // Both cards have identical sandbox configuration
        SandboxGatewayConfig gatewayConfig = SandboxGatewayConfig.builder()
                .gatewayUrl("http://localhost:8080")
                .build();

        SysOperationCard card1 = SysOperationCard.builder()
                .id(cardId1)
                .mode(OperationMode.SANDBOX)
                .gatewayConfig(gatewayConfig)
                .build();

        // Add first card - should succeed
        var addRes1 = rm.addSysOperation(card1, null);
        assertTrue(addRes1.isOk());
        createdOperationIds.add(cardId1);

        // Add second card with same config - should raise conflict
        SysOperationCard card2 = SysOperationCard.builder()
                .id(cardId2)
                .mode(OperationMode.SANDBOX)
                .gatewayConfig(gatewayConfig)  // Same config
                .build();

        // This should fail due to isolation key template conflict
        assertThrows(Exception.class, () -> rm.addSysOperation(card2, null));
    }

    @Test
    void testAddTwoSandboxOpsWithDifferentConfigShouldSucceed() {
        /** Test that adding two sandbox operations with different configs succeeds. */
        String cardId1 = "sandbox_op_1_" + UUID.randomUUID().toString().substring(0, 8);
        String cardId2 = "sandbox_op_2_" + UUID.randomUUID().toString().substring(0, 8);

        // Different configurations
        SandboxGatewayConfig config1 = SandboxGatewayConfig.builder()
                .gatewayUrl("http://localhost:8080")
                .build();

        SandboxGatewayConfig config2 = SandboxGatewayConfig.builder()
                .gatewayUrl("http://localhost:9090")  // Different URL
                .build();

        SysOperationCard card1 = SysOperationCard.builder()
                .id(cardId1)
                .mode(OperationMode.SANDBOX)
                .gatewayConfig(config1)
                .build();

        SysOperationCard card2 = SysOperationCard.builder()
                .id(cardId2)
                .mode(OperationMode.SANDBOX)
                .gatewayConfig(config2)
                .build();

        // Both should succeed
        var addRes1 = rm.addSysOperation(card1, null);
        assertTrue(addRes1.isOk());
        createdOperationIds.add(cardId1);

        var addRes2 = rm.addSysOperation(card2, null);
        assertTrue(addRes2.isOk());
        createdOperationIds.add(cardId2);
    }
}