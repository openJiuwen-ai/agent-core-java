/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.shell.bash;

import java.util.List;

/**
 * Package marker for bash shell tools.
 *
 * <p>Mirrors Python's {@code openjiuwen.harness.tools.shell.bash} in
 * {@code openjiuwen/harness/tools/shell/bash/__init__.py}.</p>
 */
public final class BashToolsPackage {

    public static final List<Class<?>> EXPORTED_TYPES = List.of(BashTool.class);

    private BashToolsPackage() {
    }
}
