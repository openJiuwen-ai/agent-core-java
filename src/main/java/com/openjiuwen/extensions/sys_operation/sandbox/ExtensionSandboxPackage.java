/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.sys_operation.sandbox;

import com.openjiuwen.extensions.sys_operation.sandbox.providers.ExtensionSandboxProvidersPackage;

/**
 * Extension sandbox package bootstrap.
 *
 * <p>Mirrors Python's package import side effects in
 * {@code openjiuwen/extensions/sys_operation/sandbox/__init__.py}.</p>
 */
public final class ExtensionSandboxPackage {

    static {
        ExtensionSandboxProvidersPackage.registerAll();
    }

    private ExtensionSandboxPackage() {
    }

    public static void bootstrap() {
        ExtensionSandboxProvidersPackage.registerAll();
    }
}
