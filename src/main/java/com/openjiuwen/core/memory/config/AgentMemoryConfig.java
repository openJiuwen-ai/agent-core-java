/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */

package com.openjiuwen.core.memory.config;

import com.openjiuwen.core.common.schema.Param;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent memory configuration.
 * Corresponds to Python: config/config.py - AgentMemoryConfig
 */
public class AgentMemoryConfig {

    private final List<Param> memVariables;
    private final boolean enableLongTermMem;

    private AgentMemoryConfig(Builder builder) {
        this.memVariables = builder.memVariables != null ? new ArrayList<>(builder.memVariables) : new ArrayList<>();
        this.enableLongTermMem = builder.enableLongTermMem;
    }

    public List<Param> getMemVariables() {
        return new ArrayList<>(memVariables);
    }

    public boolean isEnableLongTermMem() {
        return enableLongTermMem;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private List<Param> memVariables = new ArrayList<>();
        private boolean enableLongTermMem = true;

        public Builder memVariables(List<Param> memVariables) {
            this.memVariables = memVariables;
            return this;
        }

        public Builder enableLongTermMem(boolean enableLongTermMem) {
            this.enableLongTermMem = enableLongTermMem;
            return this;
        }

        public AgentMemoryConfig build() {
            return new AgentMemoryConfig(this);
        }
    }

    @Override
    public String toString() {
        return "AgentMemoryConfig{" +
               "memVariables=" + memVariables.size() +
               ", enableLongTermMem=" + enableLongTermMem +
               '}';
    }
}

