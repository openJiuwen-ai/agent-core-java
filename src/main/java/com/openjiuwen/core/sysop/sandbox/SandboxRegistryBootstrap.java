/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox;

import com.openjiuwen.extensions.sys_operation.sandbox.providers.aio.AioFSProvider;
import com.openjiuwen.extensions.sys_operation.sandbox.providers.aio.AioShellProvider;
import com.openjiuwen.extensions.sys_operation.sandbox.providers.aio.AioCodeProvider;
import com.openjiuwen.extensions.sys_operation.sandbox.providers.jiuwenbox.JiuwenBoxFSProvider;
import com.openjiuwen.extensions.sys_operation.sandbox.providers.jiuwenbox.JiuwenBoxShellProvider;
import com.openjiuwen.extensions.sys_operation.sandbox.providers.jiuwenbox.JiuwenBoxCodeProvider;

/**
 * Registers built-in launchers and operation providers once per JVM.
 */
public final class SandboxRegistryBootstrap {
    private static volatile boolean isInitialized;

    private SandboxRegistryBootstrap() {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static void ensureInitialized() {
        if (isInitialized) {
            return;
        }
        synchronized (SandboxRegistryBootstrap.class) {
            if (isInitialized) {
                return;
            }
            SandboxRegistry.registerLauncher("pre_deploy", PreDeploymentLauncher.class);

            SandboxRegistry.registerProvider("jiuwenbox", "fs", JiuwenBoxFSProvider.class);
            SandboxRegistry.registerProvider("jiuwenbox", "shell", JiuwenBoxShellProvider.class);
            SandboxRegistry.registerProvider("jiuwenbox", "code", JiuwenBoxCodeProvider.class);

            SandboxRegistry.registerProvider("aio", "fs", AioFSProvider.class);
            SandboxRegistry.registerProvider("aio", "shell", AioShellProvider.class);
            SandboxRegistry.registerProvider("aio", "code", AioCodeProvider.class);

            isInitialized = true;
        }
    }
}
