/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema;

import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;

/**
 * Serializable model configuration for a team role.
 *
 * <p>Mirrors Python's {@code TeamModelConfig} in
 * {@code openjiuwen.agent_teams.schema.deep_agent_spec}.</p>
 */
public class TeamModelConfig {

    private ModelClientConfig modelClientConfig;
    private ModelRequestConfig modelRequestConfig;

    public TeamModelConfig() {
    }

    public TeamModelConfig(ModelClientConfig modelClientConfig, ModelRequestConfig modelRequestConfig) {
        this.modelClientConfig = modelClientConfig;
        this.modelRequestConfig = modelRequestConfig;
    }

    public ModelClientConfig getModelClientConfig() {
        return modelClientConfig;
    }

    public void setModelClientConfig(ModelClientConfig modelClientConfig) {
        this.modelClientConfig = modelClientConfig;
    }

    public ModelRequestConfig getModelRequestConfig() {
        return modelRequestConfig;
    }

    public void setModelRequestConfig(ModelRequestConfig modelRequestConfig) {
        this.modelRequestConfig = modelRequestConfig;
    }
}
