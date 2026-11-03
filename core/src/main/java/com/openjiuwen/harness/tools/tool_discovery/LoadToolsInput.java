/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.tool_discovery;

import java.util.List;

/**
 * Input model for load_tools.
 *
 * <p>Mirrors Python's {@code LoadToolsInput} in
 * {@code openjiuwen/harness/tools/tool_discovery/load_tools.py}.</p>
 */
public record LoadToolsInput(List<String> toolNames, boolean replace) {
}
