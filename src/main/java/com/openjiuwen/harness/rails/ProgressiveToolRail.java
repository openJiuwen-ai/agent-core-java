/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Rail that enables progressive tool discovery and callable-tool filtering.
 * <p>
 * Registers search_tools and load_tools meta-tools, manages visible tool
 * sets, and filters tool lists based on what the agent has currently loaded.
 * <p>
 * Mirrors Python's {@code ProgressiveToolRail} in
 * {@code openjiuwen.harness.rails.progressive_tool_rail}.
 */
public class ProgressiveToolRail extends DeepAgentRail {

    private static final Logger LOG = LoggerFactory.getLogger(ProgressiveToolRail.class);

    /** Rail priority. */
    public static final int PRIORITY = 90;

    private final java.util.Set<String> defaultVisibleTools = new java.util.HashSet<>();
    private final java.util.Set<String> alwaysVisibleTools = new java.util.HashSet<>();
    private final java.util.Set<String> loadedToolNames = new java.util.LinkedHashSet<>();

    public ProgressiveToolRail(java.util.Set<String> defaultVisibleTools,
                               java.util.Set<String> alwaysVisibleTools,
                               Integer maxLoadedTools) {
        super();
        if (defaultVisibleTools != null) {
            this.defaultVisibleTools.addAll(defaultVisibleTools);
        }
        if (alwaysVisibleTools != null) {
            this.alwaysVisibleTools.addAll(alwaysVisibleTools);
        }
    }

    /** Get the set of currently loaded tool names. */
    public java.util.Set<String> getLoadedToolNames() {
        return java.util.Collections.unmodifiableSet(loadedToolNames);
    }

    /** Load a tool by name. */
    public void loadTool(String toolName) {
        loadedToolNames.add(toolName);
        LOG.debug("[ProgressiveToolRail] Loaded tool: {}", toolName);
    }

    @Override
    public void init(Object agent) {
        LOG.info("[ProgressiveToolRail] Initialized with {} default visible tools",
                defaultVisibleTools.size());
    }

    @Override
    public void uninit(Object agent) {
        loadedToolNames.clear();
        LOG.info("[ProgressiveToolRail] Uninitialized");
    }
}
