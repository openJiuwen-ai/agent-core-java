/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.sys_operation.sandbox;

import com.openjiuwen.core.sys_operation.sandbox.SandboxRegistry;
import com.openjiuwen.extensions.sys_operation.sandbox.providers.AioCodeProvider;
import com.openjiuwen.extensions.sys_operation.sandbox.providers.AioFsProvider;
import com.openjiuwen.extensions.sys_operation.sandbox.providers.AioShellProvider;
import com.openjiuwen.extensions.sys_operation.sandbox.providers.ExtensionSandboxProvidersPackage;
import com.openjiuwen.extensions.sys_operation.sandbox.providers.JiuwenBoxCodeProvider;
import com.openjiuwen.extensions.sys_operation.sandbox.providers.JiuwenBoxFsProvider;
import com.openjiuwen.extensions.sys_operation.sandbox.providers.JiuwenBoxShellProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;

class ExtensionSandboxPackageTest {

    @Test
    void bootstrapRegistersAllExtensionProviders() {
        unregisterAll();

        ExtensionSandboxPackage.bootstrap();
        ExtensionSandboxProvidersPackage.registerAll();

        assertSame(AioFsProvider.class, SandboxRegistry.getProviderCls("aio", "fs"));
        assertSame(AioShellProvider.class, SandboxRegistry.getProviderCls("aio", "shell"));
        assertSame(AioCodeProvider.class, SandboxRegistry.getProviderCls("aio", "code"));
        assertSame(JiuwenBoxFsProvider.class, SandboxRegistry.getProviderCls("jiuwenbox", "fs"));
        assertSame(JiuwenBoxShellProvider.class, SandboxRegistry.getProviderCls("jiuwenbox", "shell"));
        assertSame(JiuwenBoxCodeProvider.class, SandboxRegistry.getProviderCls("jiuwenbox", "code"));
    }

    private static void unregisterAll() {
        SandboxRegistry.unregisterProvider("aio", "fs");
        SandboxRegistry.unregisterProvider("aio", "shell");
        SandboxRegistry.unregisterProvider("aio", "code");
        SandboxRegistry.unregisterProvider("jiuwenbox", "fs");
        SandboxRegistry.unregisterProvider("jiuwenbox", "shell");
        SandboxRegistry.unregisterProvider("jiuwenbox", "code");
    }
}
