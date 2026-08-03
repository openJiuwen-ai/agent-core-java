/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import java.util.List;

/**
 * Package marker and export list for harness tools.
 *
 * <p>Mirrors Python's {@code openjiuwen.harness.tools} in
 * {@code openjiuwen/harness/tools/__init__.py}.</p>
 */
public final class HarnessToolsPackage {

    public static final List<String> EXPORTED_TOOL_CLASSES = List.of(
            "AskUserTool",
            "CodeTool",
            "ListMcpResourcesTool",
            "ReadMcpResourceTool"
    );

    private HarnessToolsPackage() {
    }
}
