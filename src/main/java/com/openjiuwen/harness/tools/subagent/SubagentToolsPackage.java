/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.subagent;

import java.util.List;

/**
 * Package facade for subagent tools.
 *
 * <p>Mirrors Python's {@code openjiuwen.harness.tools.subagent} in
 * {@code openjiuwen/harness/tools/subagent/__init__.py}.</p>
 */
public final class SubagentToolsPackage {

    private SubagentToolsPackage() {
    }

    public static List<String> exportedSymbols() {
        return List.of("SessionTools", "TaskTool");
    }
}
