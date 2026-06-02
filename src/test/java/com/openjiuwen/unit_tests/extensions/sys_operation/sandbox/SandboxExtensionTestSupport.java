/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.extensions.sys_operation.sandbox;

import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.core.sysop.SysOperationCard;
import com.openjiuwen.core.sysop.config.ContainerScope;
import com.openjiuwen.core.sysop.config.PreDeployLauncherConfig;
import com.openjiuwen.core.sysop.config.SandboxGatewayConfig;
import com.openjiuwen.core.sysop.config.SandboxIsolationConfig;
import com.openjiuwen.core.sysop.sandbox.LocalSandboxProviders;
import com.openjiuwen.core.sysop.sandbox.SandboxRegistry;
import org.junit.jupiter.api.AfterEach;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

abstract class SandboxExtensionTestSupport {

    @AfterEach
    void cleanupSandboxProviders() {
        unregisterProviders("aio");
        unregisterProviders("jiuwenbox");
    }

    protected SysOperation newAioSysOp() {
        return newSandboxSysOp("aio", "http://localhost:8080", ContainerScope.SYSTEM, null);
    }

    protected SysOperation newJiuwenboxSysOp() {
        return newSandboxSysOp(
                "jiuwenbox",
                "http://127.0.0.1:8321",
                ContainerScope.CUSTOM,
                "jiuwenbox_test_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8)
        );
    }

    protected static <T> List<T> collect(Iterator<T> iterator) {
        List<T> values = new ArrayList<>();
        iterator.forEachRemaining(values::add);
        return values;
    }

    private SysOperation newSandboxSysOp(
            String sandboxType,
            String baseUrl,
            ContainerScope scope,
            String customId
    ) {
        registerProviders(sandboxType);

        PreDeployLauncherConfig launcherConfig = PreDeployLauncherConfig.create(baseUrl, sandboxType);
        launcherConfig.setIdleTtlSeconds(600);

        SandboxIsolationConfig isolation = SandboxIsolationConfig.builder()
                .containerScope(scope)
                .customId(customId)
                .prefix("batch16_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8))
                .build();

        SysOperationCard card = SysOperationCard.builder()
                .id(sandboxType + "_sandbox_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8))
                .mode(OperationMode.SANDBOX)
                .gatewayConfig(SandboxGatewayConfig.builder()
                        .isolation(isolation)
                        .launcherConfig(launcherConfig)
                        .timeoutSeconds(30)
                        .build())
                .build();
        return new SysOperation(card);
    }

    private void registerProviders(String sandboxType) {
        SandboxRegistry.registerProvider(sandboxType, "fs", LocalSandboxProviders.LocalFsProvider::new);
        SandboxRegistry.registerProvider(sandboxType, "shell", LocalSandboxProviders.LocalShellProvider::new);
        SandboxRegistry.registerProvider(sandboxType, "code", LocalSandboxProviders.LocalCodeProvider::new);
    }

    private void unregisterProviders(String sandboxType) {
        SandboxRegistry.unregisterProvider(sandboxType, "fs");
        SandboxRegistry.unregisterProvider(sandboxType, "shell");
        SandboxRegistry.unregisterProvider(sandboxType, "code");
    }
}
