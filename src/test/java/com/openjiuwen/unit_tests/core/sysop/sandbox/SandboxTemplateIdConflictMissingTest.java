/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.sys_operation.sandbox;

import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.resourcemanager.Result;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.SysOperationCard;
import com.openjiuwen.core.sysop.config.ContainerScope;
import com.openjiuwen.core.sysop.config.PreDeployLauncherConfig;
import com.openjiuwen.core.sysop.config.SandboxGatewayConfig;
import com.openjiuwen.core.sysop.config.SandboxIsolationConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <p>Mirrors Python's {@code TestSandboxTemplateidConflict} in
 * {@code tests/unit_tests/core/sys_operation/sandbox/test_templateid_conflict.py}.</p>
 */
class SandboxTemplateIdConflictMissingTest {

    private final List<String> createdOperationIds = new ArrayList<>();

    @BeforeEach
    void startRunner() {
        Runner.start().toCompletableFuture().join();
    }

    @AfterEach
    void stopRunner() {
        List<String> reversedIds = new ArrayList<>(createdOperationIds);
        Collections.reverse(reversedIds);
        for (String operationId : reversedIds) {
            Runner.resourceMgr.removeSysOperation(operationId);
        }
        createdOperationIds.clear();
        Runner.stop().toCompletableFuture().join();
    }

    @Test
    void addTwoSandboxOpsWithSameConfigShouldRaiseConflict() {
        String cardId1 = uniqueId("sandbox_op_1");
        String cardId2 = uniqueId("sandbox_op_2");

        Result<?, ?> result1 = addTracked(sandboxCard(cardId1, ContainerScope.SYSTEM, null, null));
        assertTrue(result1.isOk(), () -> "First add_sys_operation should succeed, got: " + result1.msg());

        Result<?, ?> result2 = Runner.resourceMgr.addSysOperation(
                sandboxCard(cardId2, ContainerScope.SYSTEM, null, null));
        assertTrue(result2.isErr(), "Second add_sys_operation with identical sandbox config should fail");
        String errorMessage = String.valueOf(result2.msg()).toLowerCase();
        assertTrue(
                errorMessage.contains("conflict") || errorMessage.contains("already registered"),
                () -> "Error message should mention conflict, got: " + result2.msg()
        );
    }

    @Test
    void addTwoSandboxOpsWithDifferentContainerScopeShouldSucceed() {
        String cardId1 = uniqueId("sandbox_op_sys");
        String cardId2 = uniqueId("sandbox_op_sess");

        Result<?, ?> result1 = addTracked(sandboxCard(cardId1, ContainerScope.SYSTEM, null, null));
        assertTrue(result1.isOk(), () -> "First add_sys_operation should succeed, got: " + result1.msg());

        Result<?, ?> result2 = addTracked(sandboxCard(cardId2, ContainerScope.SESSION, null, null));
        assertTrue(result2.isOk(), () -> "Second add_sys_operation with different container_scope should succeed, got: "
                + result2.msg());
    }

    @Test
    void addTwoSandboxOpsWithDifferentCustomIdShouldSucceed() {
        String cardId1 = uniqueId("sandbox_op_custom1");
        String cardId2 = uniqueId("sandbox_op_custom2");

        Result<?, ?> result1 = addTracked(sandboxCard(cardId1, ContainerScope.CUSTOM, "custom_a", null));
        assertTrue(result1.isOk(), () -> "First add_sys_operation should succeed, got: " + result1.msg());

        Result<?, ?> result2 = addTracked(sandboxCard(cardId2, ContainerScope.CUSTOM, "custom_b", null));
        assertTrue(result2.isOk(), () -> "Second add_sys_operation with different custom_id should succeed, got: "
                + result2.msg());
    }

    @Test
    void addTwoSandboxOpsWithDifferentPrefixShouldSucceed() {
        String cardId1 = uniqueId("sandbox_op_prefix1");
        String cardId2 = uniqueId("sandbox_op_prefix2");

        Result<?, ?> result1 = addTracked(sandboxCard(cardId1, ContainerScope.SYSTEM, null, "agent1"));
        assertTrue(result1.isOk(), () -> "First add_sys_operation should succeed, got: " + result1.msg());

        Result<?, ?> result2 = addTracked(sandboxCard(cardId2, ContainerScope.SYSTEM, null, "agent2"));
        assertTrue(result2.isOk(), () -> "Second add_sys_operation with different prefix should succeed, got: "
                + result2.msg());
    }

    @Test
    void addSameSandboxOpTwiceShouldSucceedIdempotentlyAfterRemoval() {
        String cardId = uniqueId("sandbox_op_same");
        SysOperationCard card = sandboxCard(cardId, ContainerScope.SYSTEM, null, null);

        Result<?, ?> result1 = addTracked(card);
        assertTrue(result1.isOk(), () -> "First add_sys_operation should succeed, got: " + result1.msg());

        Runner.resourceMgr.removeSysOperation(cardId);
        createdOperationIds.remove(cardId);

        Result<?, ?> result2 = addTracked(card);
        assertTrue(result2.isOk(), () -> "Re-adding the same operation after removal should succeed, got: "
                + result2.msg());
    }

    @Test
    void localModeOperationsDoNotRunConflictCheck() {
        String cardId1 = uniqueId("local_op_1");
        String cardId2 = uniqueId("local_op_2");

        Result<?, ?> result1 = addTracked(localCard(cardId1));
        assertTrue(result1.isOk(), () -> "First add_sys_operation should succeed, got: " + result1.msg());

        Result<?, ?> result2 = addTracked(localCard(cardId2));
        assertTrue(result2.isOk(), () -> "Second local operation should succeed, got: " + result2.msg());
    }

    private Result<?, ?> addTracked(SysOperationCard card) {
        Result<?, ?> result = Runner.resourceMgr.addSysOperation(card);
        if (result.isOk()) {
            createdOperationIds.add(card.getId());
        }
        return result;
    }

    private static SysOperationCard sandboxCard(String cardId, ContainerScope scope, String customId, String prefix) {
        PreDeployLauncherConfig launcherConfig = new PreDeployLauncherConfig("http://localhost:8080");
        launcherConfig.setSandboxType("aio");
        launcherConfig.setIdleTtlSeconds(600);
        SandboxIsolationConfig isolation = SandboxIsolationConfig.builder()
                .containerScope(scope)
                .customId(customId)
                .prefix(prefix)
                .build();
        SandboxGatewayConfig gatewayConfig = SandboxGatewayConfig.builder()
                .isolation(isolation)
                .launcherConfig(launcherConfig)
                .timeoutSeconds(30)
                .build();
        SysOperationCard card = new SysOperationCard();
        card.setId(cardId);
        card.setMode(OperationMode.SANDBOX);
        card.setGatewayConfig(gatewayConfig);
        return card;
    }

    private static SysOperationCard localCard(String cardId) {
        SysOperationCard card = new SysOperationCard();
        card.setId(cardId);
        card.setMode(OperationMode.LOCAL);
        return card;
    }

    private static String uniqueId(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
