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

    /**
     * Auto-generated for codecheck compliance.
     */
    public NodeConfig() {
        this.abilities = new ArrayList<>();
        this.abilities.add(ComponentAbility.INVOKE);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public NodeConfig(List<ComponentAbility> abilities, IOConfig ioConfigs, IOConfig streamIoConfigs) {
        this.abilities = abilities != null ? abilities : new ArrayList<>();
        this.ioConfigs = ioConfigs;
        this.streamIoConfigs = streamIoConfigs;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<ComponentAbility> getAbilities() {
        return abilities;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setAbilities(List<ComponentAbility> abilities) {
        this.abilities = abilities;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public IOConfig getIoConfigs() {
        return ioConfigs;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setIoConfigs(IOConfig ioConfigs) {
        this.ioConfigs = ioConfigs;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public IOConfig getStreamIoConfigs() {
        return streamIoConfigs;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setStreamIoConfigs(IOConfig streamIoConfigs) {
        this.streamIoConfigs = streamIoConfigs;
    }
}
