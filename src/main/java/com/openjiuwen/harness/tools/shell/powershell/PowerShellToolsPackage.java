/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.shell.powershell;

import java.util.List;

/**
 * Package marker for PowerShell tools.
 *
 * <p>Mirrors Python's {@code openjiuwen.harness.tools.shell.powershell} in
 * {@code openjiuwen/harness/tools/shell/powershell/__init__.py}.</p>
 */
public final class PowerShellToolsPackage {

    public static final List<Class<?>> EXPORTED_TYPES = List.of(PowerShellTool.class);

    private PowerShellToolsPackage() {
    }
}
