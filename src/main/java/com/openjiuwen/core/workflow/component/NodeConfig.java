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
 * 
 * @since 0.1.7
 */
public class NodeConfig {
    private List<ComponentAbility> abilities;
    private IOConfig ioConfigs;
    private IOConfig streamIoConfigs;

    /**
     * NodeConfig.
     * 
     * @since 0.1.7
     */
    public NodeConfig() {
        this.abilities = new ArrayList<>();
        this.abilities.add(ComponentAbility.INVOKE);
    }

    /**
     * NodeConfig.
     * 
     * @param abilities abilities
     * @param ioConfigs ioConfigs
     * @param streamIoConfigs streamIoConfigs
     * @since 0.1.7
     */
    public NodeConfig(List<ComponentAbility> abilities, IOConfig ioConfigs, IOConfig streamIoConfigs) {
        this.abilities = abilities != null ? abilities : new ArrayList<>();
        this.ioConfigs = ioConfigs;
        this.streamIoConfigs = streamIoConfigs;
    }

    /**
     * getAbilities.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<ComponentAbility> getAbilities() {
        return abilities;
    }

    /**
     * setAbilities.
     * 
     * @param abilities abilities
     * @since 0.1.7
     */
    public void setAbilities(List<ComponentAbility> abilities) {
        this.abilities = abilities;
    }

    /**
     * getIoConfigs.
     * 
     * @return the result
     * @since 0.1.7
     */
    public IOConfig getIoConfigs() {
        return ioConfigs;
    }

    /**
     * setIoConfigs.
     * 
     * @param ioConfigs ioConfigs
     * @since 0.1.7
     */
    public void setIoConfigs(IOConfig ioConfigs) {
        this.ioConfigs = ioConfigs;
    }

    /**
     * getStreamIoConfigs.
     * 
     * @return the result
     * @since 0.1.7
     */
    public IOConfig getStreamIoConfigs() {
        return streamIoConfigs;
    }

    /**
     * setStreamIoConfigs.
     * 
     * @param streamIoConfigs streamIoConfigs
     * @since 0.1.7
     */
    public void setStreamIoConfigs(IOConfig streamIoConfigs) {
        this.streamIoConfigs = streamIoConfigs;
    }
}
