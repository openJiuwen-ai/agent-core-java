/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox;

import com.openjiuwen.core.sysop.sandbox.launchers.PreDeploymentLauncher;
import com.openjiuwen.extensions.sys_operation.sandbox.providers.AioCodeProvider;
import com.openjiuwen.extensions.sys_operation.sandbox.providers.AioFsProvider;
import com.openjiuwen.extensions.sys_operation.sandbox.providers.AioShellProvider;
import com.openjiuwen.extensions.sys_operation.sandbox.providers.JiuwenBoxCodeProvider;
import com.openjiuwen.extensions.sys_operation.sandbox.providers.JiuwenBoxFsProvider;
import com.openjiuwen.extensions.sys_operation.sandbox.providers.JiuwenBoxShellProvider;

/**
 * Registers built-in launchers and operation providers once per JVM.
 * 
 * @since 0.1.7
 */
public final class SandboxRegistryBootstrap {

    /**
     * SandboxRegistryBootstrap.
     * 
     * @since 0.1.7
     */
    private SandboxRegistryBootstrap() {
    }

    /**
     * ensureInitialized.
     * 
     * @since 0.1.7
     */
    public static void ensureInitialized() {
        synchronized (SandboxRegistryBootstrap.class) {
            SandboxRegistry.registerLauncher("pre_deploy", PreDeploymentLauncher.class);

            SandboxRegistry.registerProvider("jiuwenbox", "fs", JiuwenBoxFsProvider.class);
            SandboxRegistry.registerProvider("jiuwenbox", "shell", JiuwenBoxShellProvider.class);
            SandboxRegistry.registerProvider("jiuwenbox", "code", JiuwenBoxCodeProvider.class);

            SandboxRegistry.registerProvider("aio", "fs", AioFsProvider.class);
            SandboxRegistry.registerProvider("aio", "shell", AioShellProvider.class);
            SandboxRegistry.registerProvider("aio", "code", AioCodeProvider.class);
        }
    }
}
