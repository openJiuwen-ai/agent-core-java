/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.sysop.sandbox;

import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.Result;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.runner.resourcemanager.ResourceMgr;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.SysOperationCard;
import com.openjiuwen.core.sysop.config.ContainerScope;
import com.openjiuwen.core.sysop.config.PreDeployLauncherConfig;
import com.openjiuwen.core.sysop.config.SandboxGatewayConfig;
import com.openjiuwen.core.sysop.config.SandboxIsolationConfig;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Template ID conflict test for sandbox.
 * <p>
 * Mirrors Python's {@code test_templateid_conflict.py}.
 */
class TestTemplateidConflict {

    private ResourceMgr rm;
    private final List<String> createdOperationIds = new ArrayList<>();

    @BeforeEach
    void setUpRunner() {
        Runner.start();
        rm = Runner.resourceMgr();
    }

    @AfterEach
    void tearDownRunner() {
        for (int i = createdOperationIds.size() - 1; i >= 0; i--) {
            rm.removeSysOperation(createdOperationIds.get(i), null, TagMatchStrategy.ALL, true);
        }
        createdOperationIds.clear();
        Runner.stop();
    }

    @Test
    @DisplayName("same sandbox config should raise isolation template conflict")
    void testAddTwoSandboxOpsWithSameConfigShouldRaiseConflict() {
        String cardId1 = uniqueId("sandbox_op_1");
        String cardId2 = uniqueId("sandbox_op_2");

        SysOperationCard card1 = sandboxCard(cardId1, ContainerScope.SYSTEM, null, null);
        SysOperationCard card2 = sandboxCard(cardId2, ContainerScope.SYSTEM, null, null);

        Result<SysOperationCard> result1 = rm.addSysOperation(card1, null);
        assertTrue(result1.isOk(), () -> "First addSysOperation should succeed, got: " + result1);
        createdOperationIds.add(cardId1);

        Result<SysOperationCard> result2 = rm.addSysOperation(card2, null);
        assertTrue(result2.isError(), "Second addSysOperation with identical sandbox config should fail");
        String errorMsg = result2.getError().toString();
        assertTrue(errorMsg.toLowerCase().contains("conflict")
                        || errorMsg.toLowerCase().contains("already registered"),
                () -> "Error message should mention conflict, got: " + errorMsg);
    }

    @Test
    @DisplayName("different container scope should not conflict")
    void testAddTwoSandboxOpsWithDifferentContainerScopeShouldSucceed() {
        String cardId1 = uniqueId("sandbox_op_sys");
        String cardId2 = uniqueId("sandbox_op_sess");

        Result<SysOperationCard> result1 = rm.addSysOperation(
                sandboxCard(cardId1, ContainerScope.SYSTEM, null, null), null);
        assertTrue(result1.isOk(), () -> "First addSysOperation should succeed, got: " + result1);
        createdOperationIds.add(cardId1);

        Result<SysOperationCard> result2 = rm.addSysOperation(
                sandboxCard(cardId2, ContainerScope.SESSION, null, null), null);
        assertTrue(result2.isOk(),
                () -> "Second addSysOperation with different container_scope should succeed, got: " + result2);
        createdOperationIds.add(cardId2);
    }

    @Test
    @DisplayName("different custom id should not conflict")
    void testAddTwoSandboxOpsWithDifferentCustomIdShouldSucceed() {
        String cardId1 = uniqueId("sandbox_op_custom1");
        String cardId2 = uniqueId("sandbox_op_custom2");

        Result<SysOperationCard> result1 = rm.addSysOperation(
                sandboxCard(cardId1, ContainerScope.CUSTOM, "custom_a", null), null);
        assertTrue(result1.isOk(), () -> "First addSysOperation should succeed, got: " + result1);
        createdOperationIds.add(cardId1);

        Result<SysOperationCard> result2 = rm.addSysOperation(
                sandboxCard(cardId2, ContainerScope.CUSTOM, "custom_b", null), null);
        assertTrue(result2.isOk(),
                () -> "Second addSysOperation with different custom_id should succeed, got: " + result2);
        createdOperationIds.add(cardId2);
    }

    @Test
    @DisplayName("different isolation prefix should not conflict")
    void testAddTwoSandboxOpsWithDifferentPrefixShouldSucceed() {
        String cardId1 = uniqueId("sandbox_op_prefix1");
        String cardId2 = uniqueId("sandbox_op_prefix2");

        Result<SysOperationCard> result1 = rm.addSysOperation(
                sandboxCard(cardId1, ContainerScope.SYSTEM, null, "agent1"), null);
        assertTrue(result1.isOk(), () -> "First addSysOperation should succeed, got: " + result1);
        createdOperationIds.add(cardId1);

        Result<SysOperationCard> result2 = rm.addSysOperation(
                sandboxCard(cardId2, ContainerScope.SYSTEM, null, "agent2"), null);
        assertTrue(result2.isOk(),
                () -> "Second addSysOperation with different prefix should succeed, got: " + result2);
        createdOperationIds.add(cardId2);
    }

    @Test
    @DisplayName("same sandbox op can be re-added after removal")
    void testAddSameSandboxOpTwiceShouldSucceedIdempotently() {
        String cardId = uniqueId("sandbox_op_same");
        SysOperationCard card = sandboxCard(cardId, ContainerScope.SYSTEM, null, null);

        Result<SysOperationCard> result1 = rm.addSysOperation(card, null);
        assertTrue(result1.isOk(), () -> "First addSysOperation should succeed, got: " + result1);
        createdOperationIds.add(cardId);

        rm.removeSysOperation(cardId, null, TagMatchStrategy.ALL, true);
        createdOperationIds.remove(cardId);

        Result<SysOperationCard> result2 = rm.addSysOperation(card, null);
        assertTrue(result2.isOk(),
                () -> "Re-adding the same operation after removal should succeed, got: " + result2);
        createdOperationIds.add(cardId);
    }

    @Test
    @DisplayName("local mode operations do not use conflict checking")
    void testLocalModeOperationsNoConflictCheck() {
        String cardId1 = uniqueId("local_op_1");
        String cardId2 = uniqueId("local_op_2");

        Result<SysOperationCard> result1 = rm.addSysOperation(localCard(cardId1), null);
        assertTrue(result1.isOk(), () -> "First addSysOperation should succeed, got: " + result1);
        createdOperationIds.add(cardId1);

        Result<SysOperationCard> result2 = rm.addSysOperation(localCard(cardId2), null);
        assertTrue(result2.isOk(),
                () -> "Second local operation should succeed without conflict checking, got: " + result2);
        createdOperationIds.add(cardId2);
    }

    private static SysOperationCard sandboxCard(String cardId, ContainerScope scope, String customId, String prefix) {
        PreDeployLauncherConfig launcherConfig = PreDeployLauncherConfig.create("http://localhost:8080", "aio");
        launcherConfig.setIdleTtlSeconds(600);
        return SysOperationCard.builder()
                .id(cardId)
                .mode(OperationMode.SANDBOX)
                .gatewayConfig(SandboxGatewayConfig.builder()
                        .isolation(SandboxIsolationConfig.builder()
                                .containerScope(scope)
                                .customId(customId)
                                .prefix(prefix)
                                .build())
                        .launcherConfig(launcherConfig)
                        .timeoutSeconds(30)
                        .build())
                .build();
    }

    private static SysOperationCard localCard(String cardId) {
        return SysOperationCard.builder()
                .id(cardId)
                .mode(OperationMode.LOCAL)
                .build();
    }

    private static String uniqueId(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
