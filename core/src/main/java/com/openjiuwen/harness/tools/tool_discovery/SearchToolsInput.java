/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.tool_discovery;

/**
 * Input model for search_tools.
 *
 * <p>Mirrors Python's {@code SearchToolsInput} in
 * {@code openjiuwen/harness/tools/tool_discovery/search_tools.py}.</p>
 */
public record SearchToolsInput(String query, int limit, int detailLevel) {
}
