/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component;

import java.util.ArrayList;
import java.util.List;

/**
 * Stub for node configuration in a workflow.
 * <p>
 * Mirrors Python's node config with abilities, io_configs, and stream_io_configs.
 */
public class NodeConfig {

    private List<ComponentAbility> abilities;
    private IOConfig ioConfigs;
    private IOConfig streamIoConfigs;

    public NodeConfig() {
        this.abilities = new ArrayList<>();
        this.abilities.add(ComponentAbility.INVOKE);
    }

    public NodeConfig(List<ComponentAbility> abilities, IOConfig ioConfigs, IOConfig streamIoConfigs) {
        this.abilities = abilities != null ? abilities : new ArrayList<>();
        this.ioConfigs = ioConfigs;
        this.streamIoConfigs = streamIoConfigs;
    }

    public List<ComponentAbility> getAbilities() {
        return abilities;
    }

    public void setAbilities(List<ComponentAbility> abilities) {
        this.abilities = abilities;
    }

    public IOConfig getIoConfigs() {
        return ioConfigs;
    }

    public void setIoConfigs(IOConfig ioConfigs) {
        this.ioConfigs = ioConfigs;
    }

    public IOConfig getStreamIoConfigs() {
        return streamIoConfigs;
    }

    public void setStreamIoConfigs(IOConfig streamIoConfigs) {
        this.streamIoConfigs = streamIoConfigs;
    }
}
