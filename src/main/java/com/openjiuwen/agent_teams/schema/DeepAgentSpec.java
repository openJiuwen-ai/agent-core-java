/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema;

import com.openjiuwen.harness.DeepAgentConfig;

/**
 * Minimal serializable wrapper around a Java DeepAgentConfig.
 *
 * <p>Mirrors Python's {@code DeepAgentSpec} in
 * {@code openjiuwen.agent_teams.schema.deep_agent_spec} and re-export usage in
 * {@code openjiuwen.agent_teams.schema.blueprint}.
 */
public class DeepAgentSpec {

    private DeepAgentConfig config;
    private String language;

    public DeepAgentConfig getConfig() {
        return config;
    }

    public void setConfig(DeepAgentConfig config) {
        this.config = config;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }
}
