/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.tool_discovery;

import java.util.List;

/**
 * Package marker for progressive tool discovery.
 *
 * <p>Mirrors Python's {@code openjiuwen.harness.tools.tool_discovery} in
 * {@code openjiuwen/harness/tools/tool_discovery/__init__.py}.</p>
 */
public final class ToolDiscoveryPackage {

    public static final List<Class<?>> EXPORTED_TYPES = List.of(
            LoadToolsInput.class,
            LoadToolsTool.class,
            SearchToolsInput.class,
            SearchToolsTool.class
    );

    private ToolDiscoveryPackage() {
    }
}
