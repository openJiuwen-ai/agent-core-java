/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.shell;

import com.openjiuwen.harness.tools.shell.bash.BashTool;
import com.openjiuwen.harness.tools.shell.powershell.PowerShellTool;

import java.util.List;

/**
 * Package marker for shell tools.
 *
 * <p>Mirrors Python's {@code openjiuwen.harness.tools.shell} in
 * {@code openjiuwen/harness/tools/shell/__init__.py}.</p>
 */
public final class ShellToolsPackage {

    public static final List<Class<?>> EXPORTED_TYPES = List.of(BashTool.class, PowerShellTool.class);

    private ShellToolsPackage() {
    }
}
