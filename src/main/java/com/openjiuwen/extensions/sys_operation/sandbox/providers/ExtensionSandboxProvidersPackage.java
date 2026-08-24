/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.sys_operation.sandbox.providers;

import com.openjiuwen.core.sysop.sandbox.SandboxRegistry;

/**
 * Extension sandbox provider registrations.
 *
 * <p>Mirrors Python's package import side effects in
 * {@code openjiuwen/extensions/sys_operation/sandbox/providers/__init__.py}.</p>
 */
public final class ExtensionSandboxProvidersPackage {

    static {
        registerAll();
    }

    private ExtensionSandboxProvidersPackage() {
    }

    public static synchronized void registerAll() {
        SandboxRegistry.registerProvider("aio", "fs", AioFsProvider.class);
        SandboxRegistry.registerProvider("aio", "shell", AioShellProvider.class);
        SandboxRegistry.registerProvider("aio", "code", AioCodeProvider.class);
        SandboxRegistry.registerProvider("jiuwenbox", "fs", JiuwenBoxFsProvider.class);
        SandboxRegistry.registerProvider("jiuwenbox", "shell", JiuwenBoxShellProvider.class);
        SandboxRegistry.registerProvider("jiuwenbox", "code", JiuwenBoxCodeProvider.class);
    }
}
