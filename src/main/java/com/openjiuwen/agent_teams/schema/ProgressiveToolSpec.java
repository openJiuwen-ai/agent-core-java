/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema;

import java.util.ArrayList;
import java.util.List;

/**
 * Progressive tool exposure configuration.
 *
 * <p>Mirrors Python's {@code ProgressiveToolSpec} in
 * {@code openjiuwen/agent_teams/schema/deep_agent_spec.py}.</p>
 */
public class ProgressiveToolSpec {

    private boolean enabled = true;
    private List<String> alwaysVisibleTools = new ArrayList<>();
    private List<String> defaultVisibleTools = new ArrayList<>();
    private int maxLoadedTools = 12;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getAlwaysVisibleTools() {
        return new ArrayList<>(alwaysVisibleTools);
    }

    public void setAlwaysVisibleTools(List<String> alwaysVisibleTools) {
        this.alwaysVisibleTools = alwaysVisibleTools == null ? new ArrayList<>() : new ArrayList<>(alwaysVisibleTools);
    }

    public List<String> getDefaultVisibleTools() {
        return new ArrayList<>(defaultVisibleTools);
    }

    public void setDefaultVisibleTools(List<String> defaultVisibleTools) {
        this.defaultVisibleTools = defaultVisibleTools == null ? new ArrayList<>() : new ArrayList<>(defaultVisibleTools);
    }

    public int getMaxLoadedTools() {
        return maxLoadedTools;
    }

    public void setMaxLoadedTools(int maxLoadedTools) {
        this.maxLoadedTools = maxLoadedTools;
    }
}
