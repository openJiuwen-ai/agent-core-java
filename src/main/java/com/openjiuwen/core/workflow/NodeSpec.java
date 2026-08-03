/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.workflow.component.ComponentAbility;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Specification for a workflow node/component.
 * <p>
 * Mirrors Python's {@code NodeSpec} in
 * {@code openjiuwen/core/workflow/workflow_config.py}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NodeSpec {

    @JsonProperty("io_configs")
    private CompIOConfig ioConfigs;

    @JsonProperty("stream_io_configs")
    private CompIOConfig streamIoConfigs;

    private List<ComponentAbility> abilities = new ArrayList<>();

    @JsonProperty("max_retries")
    private int maxRetries = 0;

    private double timeout = -1.0d;

    @JsonProperty("exception_config")
    private ExceptionConfig exceptionConfig;

    public NodeSpec() {
    }

    public NodeSpec(List<ComponentAbility> abilities) {
        setAbilities(abilities);
    }

    public CompIOConfig getIoConfigs() {
        return ioConfigs;
    }

    public void setIoConfigs(CompIOConfig ioConfigs) {
        this.ioConfigs = ioConfigs;
    }

    public CompIOConfig getStreamIoConfigs() {
        return streamIoConfigs;
    }

    public void setStreamIoConfigs(CompIOConfig streamIoConfigs) {
        this.streamIoConfigs = streamIoConfigs;
    }

    public List<ComponentAbility> getAbilities() {
        return abilities;
    }

    public void setAbilities(List<ComponentAbility> abilities) {
        Objects.requireNonNull(abilities, "abilities must not be null");
        this.abilities = new ArrayList<>(abilities);
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        if (maxRetries < 0) {
            throw new IllegalArgumentException("max_retries must be greater than or equal to 0");
        }
        this.maxRetries = maxRetries;
    }

    public double getTimeout() {
        return timeout;
    }

    public void setTimeout(double timeout) {
        this.timeout = timeout;
    }

    public ExceptionConfig getExceptionConfig() {
        return exceptionConfig;
    }

    public void setExceptionConfig(ExceptionConfig exceptionConfig) {
        this.exceptionConfig = exceptionConfig;
    }
}
